package dev.maxin.factorylens.core.model

public enum class RootKind {
    EXTERNAL_OVERRIDE,
    DYNAMIC_DELEGATE,
    OTHER_FRAMEWORK,
    CANDIDATE,
}

public enum class RootPriority {
    PRIMARY,
    SECONDARY,
}

public data class RootDescriptor(
    public val id: RootId,
    public val symbol: SymbolDescriptor,
    public val kind: RootKind,
    public val priority: RootPriority,
    public val evidence: List<EvidenceRecord>,
    public val label: String? = null,
) {
    init {
        require(evidence.isNotEmpty()) { "RootDescriptor.evidence must not be empty." }
    }
}

public data class GraphNode(
    public val symbol: SymbolDescriptor,
)

public enum class CallEdgeScope {
    TARGET_LOCAL,
    BOUNDARY,
}

public data class CallEdge(
    public val caller: SymbolId,
    public val callee: SymbolId,
    public val scope: CallEdgeScope,
    public val callSites: List<SourceLocation> = emptyList(),
    public val evidence: List<EvidenceRecord>,
) {
    init {
        require(evidence.isNotEmpty()) { "CallEdge.evidence must not be empty." }
    }
}

public enum class ResultCompleteness {
    COMPLETE,
    PARTIAL,
    UNKNOWN,
}

public data class RootDiscovery(
    public val target: AnalysisTargetId,
    public val roots: List<RootDescriptor>,
    public val completeness: ResultCompleteness,
    public val diagnostics: List<AnalyzerDiagnostic> = emptyList(),
)

public data class CallExpansion(
    public val target: AnalysisTargetId,
    public val origin: SymbolId,
    public val nodes: List<GraphNode>,
    public val edges: List<CallEdge>,
    public val completeness: ResultCompleteness,
    public val diagnostics: List<AnalyzerDiagnostic> = emptyList(),
)
