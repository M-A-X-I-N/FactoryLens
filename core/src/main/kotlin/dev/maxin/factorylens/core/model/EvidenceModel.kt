package dev.maxin.factorylens.core.model

public enum class EvidenceKind {
    SEMANTIC_CALL,
    SEMANTIC_EXTERNAL_OVERRIDE,
    FOREGROUND_OVERRIDE_VERIFICATION,
    UNREAL_DYNAMIC_DELEGATE_REGISTRATION,
    FRAMEWORK_RULE,
    MANUAL_CONFIRMATION,
    UNRESOLVED,
}

public enum class EvidenceConfidence {
    CONFIRMED,
    INFERRED,
    CANDIDATE,
    UNRESOLVED,
}

public data class EvidenceRecord(
    public val kind: EvidenceKind,
    public val confidence: EvidenceConfidence,
    public val summary: String,
    public val location: SourceLocation? = null,
    public val relatedSymbols: List<SymbolId> = emptyList(),
) {
    init {
        require(summary.isNotBlank()) { "EvidenceRecord.summary must not be blank." }
    }
}
