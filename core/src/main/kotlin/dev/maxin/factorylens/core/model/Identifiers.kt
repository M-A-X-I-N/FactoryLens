package dev.maxin.factorylens.core.model

@JvmInline
public value class WorkspaceId(public val value: String) {
    init {
        require(value.isNotBlank()) { "WorkspaceId must not be blank." }
    }
}

@JvmInline
public value class AnalysisTargetId(public val value: String) {
    init {
        require(value.isNotBlank()) { "AnalysisTargetId must not be blank." }
    }
}

@JvmInline
public value class SymbolId(public val value: String) {
    init {
        require(value.isNotBlank()) { "SymbolId must not be blank." }
    }
}

@JvmInline
public value class RootId(public val value: String) {
    init {
        require(value.isNotBlank()) { "RootId must not be blank." }
    }
}

@JvmInline
public value class SourceUri(public val value: String) {
    init {
        require(value.isNotBlank()) { "SourceUri must not be blank." }
    }
}
