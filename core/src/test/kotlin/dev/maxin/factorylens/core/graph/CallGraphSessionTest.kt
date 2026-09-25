package dev.maxin.factorylens.core.graph

import dev.maxin.factorylens.core.api.AnalyzerResult
import dev.maxin.factorylens.core.model.AnalysisTargetId
import dev.maxin.factorylens.core.model.AnalyzerError
import dev.maxin.factorylens.core.model.AnalyzerErrorCode
import dev.maxin.factorylens.core.model.CallEdge
import dev.maxin.factorylens.core.model.CallEdgeScope
import dev.maxin.factorylens.core.model.CallExpansion
import dev.maxin.factorylens.core.model.CallGraphTraversalLimits
import dev.maxin.factorylens.core.model.CallTreeNode
import dev.maxin.factorylens.core.model.CallTreeNodeDisposition
import dev.maxin.factorylens.core.model.EvidenceConfidence
import dev.maxin.factorylens.core.model.EvidenceKind
import dev.maxin.factorylens.core.model.EvidenceRecord
import dev.maxin.factorylens.core.model.GraphNode
import dev.maxin.factorylens.core.model.ResultCompleteness
import dev.maxin.factorylens.core.model.SourceRealm
import dev.maxin.factorylens.core.model.SymbolDescriptor
import dev.maxin.factorylens.core.model.SymbolId
import dev.maxin.factorylens.core.model.SymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

public class CallGraphSessionTest {
    private val target = AnalysisTargetId("rss")

    @Test
    public fun mergesSharedPathsMarksCyclesAndReusesCachedExpansions(): Unit {
        val a = node("A")
        val b = node("B")
        val c = node("C")
        val d = node("D")
        val engine = node("Engine", SourceRealm.UNREAL_ENGINE)

        val source = RecordingSource(
            mapOf(
                a.symbol.id to expansion(
                    a,
                    edge(a, b),
                    edge(a, c),
                    edge(a, engine),
                ),
                b.symbol.id to expansion(
                    b,
                    edge(b, d),
                ),
                c.symbol.id to expansion(
                    c,
                    edge(c, d),
                ),
                d.symbol.id to expansion(
                    d,
                    edge(d, b),
                ),
            ),
        )
        val graph = CallGraphSession(target, source)

        val first = assertIs<AnalyzerResult.Success<*>>(
            graph.traverse(
                root = a,
                limits = CallGraphTraversalLimits(
                    maxDepth = 6,
                    maxTargetNodes = 10,
                ),
            ),
        ).value as dev.maxin.factorylens.core.model.CallGraphTraversal

        assertEquals(5, first.nodes.size)
        assertEquals(6, first.edges.size)
        assertFalse(first.depthLimitReached)
        assertFalse(first.targetNodeLimitReached)
        assertEquals(ResultCompleteness.COMPLETE, first.completeness)
        assertEquals(4, first.backendQueries)
        assertEquals(0, first.cacheHits)

        val occurrences = flatten(first.tree)
        assertTrue(
            occurrences.any {
                it.node.symbol.id == b.symbol.id &&
                    it.depth == 3 &&
                    it.disposition == CallTreeNodeDisposition.CYCLE
            },
        )
        assertTrue(
            occurrences.any {
                it.node.symbol.id == d.symbol.id &&
                    it.depth == 2 &&
                    it.disposition == CallTreeNodeDisposition.SHARED
            },
        )
        assertTrue(
            occurrences.any {
                it.node.symbol.id == engine.symbol.id &&
                    it.disposition == CallTreeNodeDisposition.BOUNDARY
            },
        )

        assertEquals(
            listOf(a.symbol.id, b.symbol.id, d.symbol.id, c.symbol.id),
            source.queries,
        )

        val second = assertIs<AnalyzerResult.Success<*>>(
            graph.traverse(
                root = a,
                limits = CallGraphTraversalLimits(
                    maxDepth = 6,
                    maxTargetNodes = 10,
                ),
            ),
        ).value as dev.maxin.factorylens.core.model.CallGraphTraversal

        assertEquals(0, second.backendQueries)
        assertEquals(4, second.cacheHits)
        assertEquals(4, source.queries.size)

        val stats = graph.stats()
        assertEquals(4, stats.cachedExpansionCount)
        assertEquals(4, stats.backendQueryCount)
        assertEquals(4, stats.cacheHitCount)
        assertEquals(5, stats.uniqueNodeCount)
        assertEquals(6, stats.uniqueEdgeCount)

        val snapshot = graph.snapshot()
        assertEquals(
            setOf(a.symbol.id, b.symbol.id, c.symbol.id, d.symbol.id),
            snapshot.cachedOrigins,
        )
        assertEquals(5, snapshot.nodes.size)
        assertEquals(6, snapshot.edges.size)
    }

    @Test
    public fun enforcesDepthAndTargetNodeBoundsWithoutDiscardingBoundaryEdges(): Unit {
        val a = node("A")
        val b = node("B")
        val c = node("C")
        val d = node("D")
        val engine = node("Engine", SourceRealm.UNREAL_ENGINE)
        val expansions = mapOf(
            a.symbol.id to expansion(
                a,
                edge(a, b),
                edge(a, c),
                edge(a, engine),
            ),
            b.symbol.id to expansion(b, edge(b, d)),
            c.symbol.id to expansion(c),
            d.symbol.id to expansion(d),
        )

        val depthSource = RecordingSource(expansions)
        val depthGraph = CallGraphSession(target, depthSource)
        val depthLimited = assertIs<AnalyzerResult.Success<*>>(
            depthGraph.traverse(
                root = a,
                limits = CallGraphTraversalLimits(
                    maxDepth = 1,
                    maxTargetNodes = 10,
                ),
            ),
        ).value as dev.maxin.factorylens.core.model.CallGraphTraversal

        assertTrue(depthLimited.depthLimitReached)
        assertFalse(depthLimited.targetNodeLimitReached)
        assertEquals(ResultCompleteness.PARTIAL, depthLimited.completeness)
        assertEquals(listOf(a.symbol.id), depthSource.queries)
        assertEquals(4, depthLimited.nodes.size)
        assertEquals(3, depthLimited.edges.size)
        assertTrue(
            flatten(depthLimited.tree).any {
                it.node.symbol.id == b.symbol.id &&
                    it.disposition == CallTreeNodeDisposition.DEPTH_LIMIT
            },
        )
        assertTrue(
            flatten(depthLimited.tree).any {
                it.node.symbol.id == engine.symbol.id &&
                    it.disposition == CallTreeNodeDisposition.BOUNDARY
            },
        )

        val nodeSource = RecordingSource(expansions)
        val nodeGraph = CallGraphSession(target, nodeSource)
        val nodeLimited = assertIs<AnalyzerResult.Success<*>>(
            nodeGraph.traverse(
                root = a,
                limits = CallGraphTraversalLimits(
                    maxDepth = 6,
                    maxTargetNodes = 2,
                ),
            ),
        ).value as dev.maxin.factorylens.core.model.CallGraphTraversal

        assertTrue(nodeLimited.targetNodeLimitReached)
        assertFalse(nodeLimited.depthLimitReached)
        assertEquals(2, nodeLimited.omittedTargetNodeCount)
        assertEquals(ResultCompleteness.PARTIAL, nodeLimited.completeness)
        assertEquals(listOf(a.symbol.id, b.symbol.id), nodeSource.queries)
        assertEquals(
            setOf(a.symbol.id, b.symbol.id, engine.symbol.id),
            nodeLimited.nodes.map { it.symbol.id }.toSet(),
        )
        assertEquals(2, nodeLimited.edges.size)
        assertTrue(
            nodeLimited.edges.any {
                it.callee == engine.symbol.id && it.scope == CallEdgeScope.BOUNDARY
            },
        )
        assertTrue(
            nodeLimited.diagnostics.any {
                it.code == "graph-target-node-limit-reached"
            },
        )
    }

    @Test
    public fun deduplicatesRepeatedEdgesAndDoesNotCacheFailures(): Unit {
        val a = node("A")
        val b = node("B")
        var attempts = 0
        val graph = CallGraphSession(
            target = target,
            source = CallExpansionSource { origin ->
                attempts += 1
                if (attempts == 1) {
                    AnalyzerResult.Failure(
                        AnalyzerError(
                            code = AnalyzerErrorCode.QUERY_FAILED,
                            message = "temporary",
                            recoverable = true,
                        ),
                    )
                } else {
                    AnalyzerResult.Success(
                        CallExpansion(
                            target = target,
                            origin = origin,
                            nodes = listOf(b),
                            edges = listOf(
                                edge(a, b, "first"),
                                edge(a, b, "second"),
                            ),
                            completeness = ResultCompleteness.COMPLETE,
                        ),
                    )
                }
            },
        )

        assertIs<AnalyzerResult.Failure>(graph.expand(a.symbol.id))

        val recovered = assertIs<AnalyzerResult.Success<*>>(
            graph.expand(a.symbol.id),
        ).value as CallExpansion
        assertEquals(1, recovered.edges.size)
        assertEquals(
            setOf("first", "second"),
            recovered.edges.single().evidence.map { it.summary }.toSet(),
        )

        assertIs<AnalyzerResult.Success<*>>(graph.expand(a.symbol.id))
        assertEquals(2, attempts)
        assertEquals(2, graph.stats().backendQueryCount)
        assertEquals(1, graph.stats().cacheHitCount)
    }

    private fun expansion(
        origin: GraphNode,
        vararg edges: CallEdge,
    ): CallExpansion {
        val nodes = edges
            .map { edge ->
                requireNotNull(nodeRegistry[edge.callee]) {
                    "Missing test node ${edge.callee.value}"
                }
            }
            .distinctBy { it.symbol.id }

        return CallExpansion(
            target = target,
            origin = origin.symbol.id,
            nodes = nodes,
            edges = edges.toList(),
            completeness = ResultCompleteness.COMPLETE,
        )
    }

    private fun edge(
        caller: GraphNode,
        callee: GraphNode,
        summary: String = "${caller.symbol.displayName}->${callee.symbol.displayName}",
    ): CallEdge {
        nodeRegistry[caller.symbol.id] = caller
        nodeRegistry[callee.symbol.id] = callee
        return CallEdge(
            caller = caller.symbol.id,
            callee = callee.symbol.id,
            scope =
                if (callee.symbol.realm == SourceRealm.TARGET) {
                    CallEdgeScope.TARGET_LOCAL
                } else {
                    CallEdgeScope.BOUNDARY
                },
            evidence = listOf(
                EvidenceRecord(
                    kind = EvidenceKind.SEMANTIC_CALL,
                    confidence = EvidenceConfidence.CONFIRMED,
                    summary = summary,
                    relatedSymbols = listOf(caller.symbol.id, callee.symbol.id),
                ),
            ),
        )
    }

    private fun node(
        id: String,
        realm: SourceRealm = SourceRealm.TARGET,
    ): GraphNode {
        val node = GraphNode(
            SymbolDescriptor(
                id = SymbolId(id),
                displayName = id,
                kind = SymbolKind.METHOD,
                realm = realm,
            ),
        )
        nodeRegistry[node.symbol.id] = node
        return node
    }

    private fun flatten(root: CallTreeNode): List<CallTreeNode> =
        buildList {
            fun visit(node: CallTreeNode) {
                add(node)
                node.children.forEach(::visit)
            }
            visit(root)
        }

    private class RecordingSource(
        private val expansions: Map<SymbolId, CallExpansion>,
    ) : CallExpansionSource {
        val queries: MutableList<SymbolId> = mutableListOf()

        override fun expandOutgoingCalls(origin: SymbolId): AnalyzerResult<CallExpansion> {
            queries += origin
            val expansion = expansions[origin]
                ?: return AnalyzerResult.Failure(
                    AnalyzerError(
                        code = AnalyzerErrorCode.QUERY_FAILED,
                        message = "No fixture expansion for ${origin.value}.",
                        recoverable = false,
                    ),
                )
            return AnalyzerResult.Success(expansion)
        }
    }

    private companion object {
        val nodeRegistry: MutableMap<SymbolId, GraphNode> = linkedMapOf()
    }
}
