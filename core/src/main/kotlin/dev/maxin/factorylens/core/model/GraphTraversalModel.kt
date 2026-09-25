package dev.maxin.factorylens.core.model

public data class CallGraphTraversalLimits(
    public val maxDepth: Int = 3,
    public val maxTargetNodes: Int = 80,
) {
    init {
        require(maxDepth >= 0) { "CallGraphTraversalLimits.maxDepth must be zero or greater." }
        require(maxTargetNodes >= 1) {
            "CallGraphTraversalLimits.maxTargetNodes must be at least one."
        }
    }
}

public enum class CallTreeNodeDisposition {
    EXPANDED,
    LEAF,
    BOUNDARY,
    SHARED,
    CYCLE,
    DEPTH_LIMIT,
}

public data class CallTreeNode(
    public val node: GraphNode,
    public val incomingEdge: CallEdge? = null,
    public val depth: Int,
    public val disposition: CallTreeNodeDisposition,
    public val children: List<CallTreeNode> = emptyList(),
) {
    init {
        require(depth >= 0) { "CallTreeNode.depth must be zero or greater." }
        require(
            (depth == 0 && incomingEdge == null) ||
                (depth > 0 && incomingEdge != null),
        ) {
            "Only the traversal root may omit an incoming edge."
        }
        if (
            disposition == CallTreeNodeDisposition.BOUNDARY ||
            disposition == CallTreeNodeDisposition.SHARED ||
            disposition == CallTreeNodeDisposition.CYCLE ||
            disposition == CallTreeNodeDisposition.DEPTH_LIMIT
        ) {
            require(children.isEmpty()) {
                "$disposition traversal nodes must not contain expanded children."
            }
        }
    }
}

public data class CallGraphTraversal(
    public val target: AnalysisTargetId,
    public val root: SymbolId,
    public val tree: CallTreeNode,
    public val nodes: List<GraphNode>,
    public val edges: List<CallEdge>,
    public val completeness: ResultCompleteness,
    public val diagnostics: List<AnalyzerDiagnostic> = emptyList(),
    public val limits: CallGraphTraversalLimits,
    public val depthLimitReached: Boolean,
    public val targetNodeLimitReached: Boolean,
    public val omittedTargetNodeCount: Int,
    public val backendQueries: Int,
    public val cacheHits: Int,
) {
    init {
        require(tree.node.symbol.id == root) {
            "CallGraphTraversal.tree must begin at CallGraphTraversal.root."
        }
        require(omittedTargetNodeCount >= 0) {
            "CallGraphTraversal.omittedTargetNodeCount must be zero or greater."
        }
        require(backendQueries >= 0) {
            "CallGraphTraversal.backendQueries must be zero or greater."
        }
        require(cacheHits >= 0) {
            "CallGraphTraversal.cacheHits must be zero or greater."
        }
    }
}

public data class CallGraphSnapshot(
    public val target: AnalysisTargetId,
    public val nodes: List<GraphNode>,
    public val edges: List<CallEdge>,
    public val cachedOrigins: Set<SymbolId>,
)

public data class CallGraphCacheStats(
    public val cachedExpansionCount: Int,
    public val backendQueryCount: Int,
    public val cacheHitCount: Int,
    public val uniqueNodeCount: Int,
    public val uniqueEdgeCount: Int,
)
