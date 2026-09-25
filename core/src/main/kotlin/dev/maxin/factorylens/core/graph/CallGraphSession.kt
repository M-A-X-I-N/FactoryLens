package dev.maxin.factorylens.core.graph

import dev.maxin.factorylens.core.api.AnalyzerResult
import dev.maxin.factorylens.core.model.AnalysisTargetId
import dev.maxin.factorylens.core.model.AnalyzerDiagnostic
import dev.maxin.factorylens.core.model.AnalyzerError
import dev.maxin.factorylens.core.model.AnalyzerErrorCode
import dev.maxin.factorylens.core.model.CallEdge
import dev.maxin.factorylens.core.model.CallEdgeScope
import dev.maxin.factorylens.core.model.CallExpansion
import dev.maxin.factorylens.core.model.CallGraphCacheStats
import dev.maxin.factorylens.core.model.CallGraphSnapshot
import dev.maxin.factorylens.core.model.CallGraphTraversal
import dev.maxin.factorylens.core.model.CallGraphTraversalLimits
import dev.maxin.factorylens.core.model.CallTreeNode
import dev.maxin.factorylens.core.model.CallTreeNodeDisposition
import dev.maxin.factorylens.core.model.DiagnosticSeverity
import dev.maxin.factorylens.core.model.GraphNode
import dev.maxin.factorylens.core.model.ResultCompleteness
import dev.maxin.factorylens.core.model.SourceRealm
import dev.maxin.factorylens.core.model.SymbolId

public fun interface CallExpansionSource {
    public fun expandOutgoingCalls(origin: SymbolId): AnalyzerResult<CallExpansion>
}

/**
 * Session-scoped semantic graph/cache.
 *
 * The source supplies one-node semantic expansions. This layer owns reuse, graph merging,
 * deterministic bounded traversal, and path-local tree markers for cycles/shared nodes.
 *
 * Successful expansions are cached for the lifetime of this object. Failures are intentionally
 * not cached so a recoverable backend condition may be retried.
 */
public class CallGraphSession(
    public val target: AnalysisTargetId,
    private val source: CallExpansionSource,
) {
    private data class EdgeKey(
        val caller: SymbolId,
        val callee: SymbolId,
    )

    private val expansionCache = linkedMapOf<SymbolId, CallExpansion>()
    private val knownNodes = linkedMapOf<SymbolId, GraphNode>()
    private val knownEdges = linkedMapOf<EdgeKey, CallEdge>()

    private var backendQueryCount: Int = 0
    private var cacheHitCount: Int = 0

    @Synchronized
    public fun expand(origin: SymbolId): AnalyzerResult<CallExpansion> {
        expansionCache[origin]?.let { cached ->
            cacheHitCount += 1
            return AnalyzerResult.Success(cached)
        }

        backendQueryCount += 1
        val sourceResult = source.expandOutgoingCalls(origin)
        val expansion = when (sourceResult) {
            is AnalyzerResult.Failure -> return sourceResult
            is AnalyzerResult.Success -> sourceResult.value
        }

        val normalized = normalizeExpansion(origin, expansion)
        if (normalized is AnalyzerResult.Failure) {
            return normalized
        }
        val value = (normalized as AnalyzerResult.Success).value

        mergeExpansion(value)?.let { error ->
            return AnalyzerResult.Failure(error)
        }

        expansionCache[origin] = value
        return AnalyzerResult.Success(value)
    }

    /**
     * Builds a bounded tree-oriented projection from one target-local root.
     *
     * Semantic discovery is breadth-first so a shared node is admitted/expanded at its shallowest
     * reached depth before the path-local tree projection decides how to render repeated references.
     *
     * Boundary calls are retained but never recursively expanded. The target-node limit counts
     * only TARGET symbols, matching the B4/B5 traversal model; boundary nodes do not consume the
     * recursive project budget.
     */
    @Synchronized
    public fun traverse(
        root: GraphNode,
        limits: CallGraphTraversalLimits = CallGraphTraversalLimits(),
    ): AnalyzerResult<CallGraphTraversal> {
        if (root.symbol.realm != SourceRealm.TARGET) {
            return failure(
                code = AnalyzerErrorCode.INVALID_TARGET,
                message =
                    "Call-graph traversal root must belong to TARGET; " +
                    "${root.symbol.id.value} is ${root.symbol.realm}.",
                recoverable = false,
            )
        }

        mergeNode(root)?.let { error -> return AnalyzerResult.Failure(error) }

        val queriesBefore = backendQueryCount
        val hitsBefore = cacheHitCount
        val traversalNodes = linkedMapOf(root.symbol.id to root)
        val traversalEdges = linkedMapOf<EdgeKey, CallEdge>()
        val admittedTargetDepth = linkedMapOf(root.symbol.id to 0)
        val depthLimitedNodes = linkedSetOf<SymbolId>()
        val omittedTargetNodes = linkedSetOf<SymbolId>()
        val diagnostics = mutableListOf<AnalyzerDiagnostic>()
        val queue = ArrayDeque<Pair<GraphNode, Int>>()
        queue.addLast(root to 0)

        var aggregateCompleteness = ResultCompleteness.COMPLETE

        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            val symbolId = node.symbol.id

            if (depth >= limits.maxDepth) {
                depthLimitedNodes += symbolId
                continue
            }

            val expansionResult = expand(symbolId)
            val expansion = when (expansionResult) {
                is AnalyzerResult.Failure -> return expansionResult
                is AnalyzerResult.Success -> expansionResult.value
            }

            aggregateCompleteness = combineCompleteness(
                aggregateCompleteness,
                expansion.completeness,
            )
            diagnostics += expansion.diagnostics

            val expansionNodes = expansion.nodes.associateBy { it.symbol.id }
            for (edge in stableEdges(expansion)) {
                val child = expansionNodes[edge.callee]
                    ?: return failure(
                        code = AnalyzerErrorCode.BACKEND_PROTOCOL_ERROR,
                        message =
                            "Expansion for ${symbolId.value} references missing callee " +
                            "${edge.callee.value}.",
                        recoverable = false,
                    )

                if (edge.scope == CallEdgeScope.TARGET_LOCAL) {
                    val knownDepth = admittedTargetDepth[child.symbol.id]
                    if (knownDepth == null) {
                        if (admittedTargetDepth.size >= limits.maxTargetNodes) {
                            omittedTargetNodes += child.symbol.id
                            continue
                        }

                        val childDepth = depth + 1
                        admittedTargetDepth[child.symbol.id] = childDepth
                        traversalNodes[child.symbol.id] = child
                        queue.addLast(child to childDepth)
                    }
                } else {
                    traversalNodes.putIfAbsent(child.symbol.id, child)
                }

                traversalEdges.putIfAbsent(
                    EdgeKey(edge.caller, edge.callee),
                    edge,
                )
            }
        }

        val edgesByCaller = traversalEdges.values.groupBy(CallEdge::caller)
        val rendered = linkedSetOf<SymbolId>()

        fun project(
            node: GraphNode,
            incomingEdge: CallEdge?,
            depth: Int,
            path: Set<SymbolId>,
        ): CallTreeNode {
            val symbolId = node.symbol.id

            if (node.symbol.realm != SourceRealm.TARGET) {
                return CallTreeNode(
                    node = node,
                    incomingEdge = incomingEdge,
                    depth = depth,
                    disposition = CallTreeNodeDisposition.BOUNDARY,
                )
            }

            if (symbolId in path) {
                return CallTreeNode(
                    node = node,
                    incomingEdge = incomingEdge,
                    depth = depth,
                    disposition = CallTreeNodeDisposition.CYCLE,
                )
            }

            if (symbolId in rendered) {
                return CallTreeNode(
                    node = node,
                    incomingEdge = incomingEdge,
                    depth = depth,
                    disposition = CallTreeNodeDisposition.SHARED,
                )
            }

            rendered += symbolId

            if (symbolId in depthLimitedNodes) {
                return CallTreeNode(
                    node = node,
                    incomingEdge = incomingEdge,
                    depth = depth,
                    disposition = CallTreeNodeDisposition.DEPTH_LIMIT,
                )
            }

            val children = (edgesByCaller[symbolId] ?: emptyList())
                .mapNotNull { edge ->
                    traversalNodes[edge.callee]?.let { child ->
                        project(
                            node = child,
                            incomingEdge = edge,
                            depth = depth + 1,
                            path = path + symbolId,
                        )
                    }
                }

            return CallTreeNode(
                node = node,
                incomingEdge = incomingEdge,
                depth = depth,
                disposition =
                    if (children.isEmpty()) {
                        CallTreeNodeDisposition.LEAF
                    } else {
                        CallTreeNodeDisposition.EXPANDED
                    },
                children = children,
            )
        }

        val tree = project(
            node = root,
            incomingEdge = null,
            depth = 0,
            path = emptySet(),
        )

        val depthLimitReached = depthLimitedNodes.isNotEmpty()
        val targetNodeLimitReached = omittedTargetNodes.isNotEmpty()

        if (targetNodeLimitReached) {
            diagnostics += AnalyzerDiagnostic(
                code = "graph-target-node-limit-reached",
                severity = DiagnosticSeverity.INFO,
                message =
                    "Traversal reached the configured target-node limit " +
                    "(${limits.maxTargetNodes}); ${omittedTargetNodes.size} target nodes were omitted.",
            )
        }
        if (depthLimitReached) {
            diagnostics += AnalyzerDiagnostic(
                code = "graph-depth-limit-reached",
                severity = DiagnosticSeverity.INFO,
                message =
                    "Traversal stopped recursive expansion at the configured depth " +
                    "${limits.maxDepth}.",
            )
        }

        if (
            aggregateCompleteness != ResultCompleteness.UNKNOWN &&
            (depthLimitReached || targetNodeLimitReached)
        ) {
            aggregateCompleteness = ResultCompleteness.PARTIAL
        }

        return AnalyzerResult.Success(
            CallGraphTraversal(
                target = target,
                root = root.symbol.id,
                tree = tree,
                nodes = traversalNodes.values.toList(),
                edges = traversalEdges.values.toList(),
                completeness = aggregateCompleteness,
                diagnostics = diagnostics.distinct(),
                limits = limits,
                depthLimitReached = depthLimitReached,
                targetNodeLimitReached = targetNodeLimitReached,
                omittedTargetNodeCount = omittedTargetNodes.size,
                backendQueries = backendQueryCount - queriesBefore,
                cacheHits = cacheHitCount - hitsBefore,
            ),
        )
    }

    @Synchronized
    public fun snapshot(): CallGraphSnapshot =
        CallGraphSnapshot(
            target = target,
            nodes = knownNodes.values.toList(),
            edges = knownEdges.values.toList(),
            cachedOrigins = expansionCache.keys.toSet(),
        )

    @Synchronized
    public fun stats(): CallGraphCacheStats =
        CallGraphCacheStats(
            cachedExpansionCount = expansionCache.size,
            backendQueryCount = backendQueryCount,
            cacheHitCount = cacheHitCount,
            uniqueNodeCount = knownNodes.size,
            uniqueEdgeCount = knownEdges.size,
        )

    /**
     * Clears graph/query state while keeping the configured source and target.
     *
     * A higher analyzer-session restart can use this to ensure backend restarts never serve
     * previous semantic results as current.
     */
    @Synchronized
    public fun clear() {
        expansionCache.clear()
        knownNodes.clear()
        knownEdges.clear()
        backendQueryCount = 0
        cacheHitCount = 0
    }

    private fun normalizeExpansion(
        origin: SymbolId,
        expansion: CallExpansion,
    ): AnalyzerResult<CallExpansion> {
        if (expansion.target != target) {
            return failure(
                code = AnalyzerErrorCode.BACKEND_PROTOCOL_ERROR,
                message =
                    "Expansion target ${expansion.target.value} does not match graph target " +
                    "${target.value}.",
                recoverable = false,
            )
        }
        if (expansion.origin != origin) {
            return failure(
                code = AnalyzerErrorCode.BACKEND_PROTOCOL_ERROR,
                message =
                    "Expansion origin ${expansion.origin.value} does not match requested origin " +
                    "${origin.value}.",
                recoverable = false,
            )
        }

        val nodes = linkedMapOf<SymbolId, GraphNode>()
        for (node in expansion.nodes) {
            val previous = nodes.putIfAbsent(node.symbol.id, node)
            if (previous != null && previous != node) {
                return failure(
                    code = AnalyzerErrorCode.BACKEND_PROTOCOL_ERROR,
                    message =
                        "Expansion contains conflicting descriptors for symbol " +
                        "${node.symbol.id.value}.",
                    recoverable = false,
                )
            }
        }

        val edges = linkedMapOf<EdgeKey, CallEdge>()
        for (edge in expansion.edges) {
            if (edge.caller != origin) {
                return failure(
                    code = AnalyzerErrorCode.BACKEND_PROTOCOL_ERROR,
                    message =
                        "Expansion for ${origin.value} contains an edge owned by " +
                        "${edge.caller.value}.",
                    recoverable = false,
                )
            }
            val callee = nodes[edge.callee]
                ?: return failure(
                    code = AnalyzerErrorCode.BACKEND_PROTOCOL_ERROR,
                    message =
                        "Expansion edge ${edge.caller.value} -> ${edge.callee.value} " +
                        "has no matching callee node.",
                    recoverable = false,
                )

            val expectedScope =
                if (callee.symbol.realm == SourceRealm.TARGET) {
                    CallEdgeScope.TARGET_LOCAL
                } else {
                    CallEdgeScope.BOUNDARY
                }
            if (edge.scope != expectedScope) {
                return failure(
                    code = AnalyzerErrorCode.BACKEND_PROTOCOL_ERROR,
                    message =
                        "Expansion edge ${edge.caller.value} -> ${edge.callee.value} has scope " +
                        "${edge.scope}, but callee realm ${callee.symbol.realm} requires " +
                        "$expectedScope.",
                    recoverable = false,
                )
            }

            val key = EdgeKey(edge.caller, edge.callee)
            edges[key] = edges[key]?.let { existing ->
                mergeEdges(existing, edge)
            } ?: edge
        }

        return AnalyzerResult.Success(
            expansion.copy(
                nodes = nodes.values.toList(),
                edges = edges.values.toList(),
                diagnostics = expansion.diagnostics.distinct(),
            ),
        )
    }

    private fun mergeExpansion(expansion: CallExpansion): AnalyzerError? {
        for (node in expansion.nodes) {
            mergeNode(node)?.let { return it }
        }

        for (edge in expansion.edges) {
            val key = EdgeKey(edge.caller, edge.callee)
            val existing = knownEdges[key]
            if (existing == null) {
                knownEdges[key] = edge
            } else {
                if (existing.scope != edge.scope) {
                    return AnalyzerError(
                        code = AnalyzerErrorCode.BACKEND_PROTOCOL_ERROR,
                        message =
                            "Cached edge ${edge.caller.value} -> ${edge.callee.value} " +
                            "changed scope from ${existing.scope} to ${edge.scope}.",
                        recoverable = false,
                    )
                }
                knownEdges[key] = mergeEdges(existing, edge)
            }
        }

        return null
    }

    private fun mergeNode(node: GraphNode): AnalyzerError? {
        val existing = knownNodes[node.symbol.id]
        if (existing == null) {
            knownNodes[node.symbol.id] = node
            return null
        }
        if (existing != node) {
            return AnalyzerError(
                code = AnalyzerErrorCode.BACKEND_PROTOCOL_ERROR,
                message =
                    "Symbol ${node.symbol.id.value} changed descriptor inside one graph session.",
                recoverable = false,
            )
        }
        return null
    }

    private fun mergeEdges(
        first: CallEdge,
        second: CallEdge,
    ): CallEdge =
        first.copy(
            callSites = (first.callSites + second.callSites).distinct(),
            evidence = (first.evidence + second.evidence).distinct(),
        )

    private fun stableEdges(expansion: CallExpansion): List<CallEdge> {
        val nodes = expansion.nodes.associateBy { it.symbol.id }
        return expansion.edges.sortedWith(
            compareBy<CallEdge>(
                { edge -> nodes[edge.callee]?.symbol?.navigation?.preferred()?.uri?.value ?: "" },
                {
                    edge ->
                    nodes[edge.callee]
                        ?.symbol
                        ?.navigation
                        ?.preferred()
                        ?.range
                        ?.start
                        ?.line
                        ?: Int.MAX_VALUE
                },
                {
                    edge ->
                    nodes[edge.callee]
                        ?.symbol
                        ?.navigation
                        ?.preferred()
                        ?.range
                        ?.start
                        ?.column
                        ?: Int.MAX_VALUE
                },
                { edge -> nodes[edge.callee]?.symbol?.displayName ?: "" },
                { edge -> edge.callee.value },
            ),
        )
    }

    private fun combineCompleteness(
        current: ResultCompleteness,
        incoming: ResultCompleteness,
    ): ResultCompleteness =
        when {
            current == ResultCompleteness.UNKNOWN || incoming == ResultCompleteness.UNKNOWN ->
                ResultCompleteness.UNKNOWN
            current == ResultCompleteness.PARTIAL || incoming == ResultCompleteness.PARTIAL ->
                ResultCompleteness.PARTIAL
            else -> ResultCompleteness.COMPLETE
        }

    private fun <T> failure(
        code: AnalyzerErrorCode,
        message: String,
        recoverable: Boolean,
    ): AnalyzerResult<T> =
        AnalyzerResult.Failure(
            AnalyzerError(
                code = code,
                message = message,
                recoverable = recoverable,
            ),
        )
}
