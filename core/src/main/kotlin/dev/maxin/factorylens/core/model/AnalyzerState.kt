package dev.maxin.factorylens.core.model

public enum class AnalyzerStatus {
    NOT_CONFIGURED,
    STARTING,
    INDEXING,
    READY,
    QUERYING,
    DEGRADED,
    ERROR,
    STOPPED,
}

public enum class AnalyzerStage {
    CONFIGURATION,
    STARTING_BACKEND,
    INDEXING,
    DISCOVERING_ROOTS,
    EXPANDING_CALLS,
    STOPPING,
}

public data class AnalyzerProgress(
    public val stage: AnalyzerStage,
    public val message: String,
    public val completedUnits: Long? = null,
    public val totalUnits: Long? = null,
) {
    init {
        require(message.isNotBlank()) { "AnalyzerProgress.message must not be blank." }
        require(completedUnits == null || completedUnits >= 0) {
            "AnalyzerProgress.completedUnits must be zero or greater."
        }
        require(totalUnits == null || totalUnits >= 0) {
            "AnalyzerProgress.totalUnits must be zero or greater."
        }
        require(completedUnits == null || totalUnits == null || completedUnits <= totalUnits) {
            "AnalyzerProgress.completedUnits must not exceed totalUnits."
        }
    }
}

public enum class DiagnosticSeverity {
    INFO,
    WARNING,
    ERROR,
}

public data class AnalyzerDiagnostic(
    public val code: String,
    public val severity: DiagnosticSeverity,
    public val message: String,
    public val location: SourceLocation? = null,
) {
    init {
        require(code.isNotBlank()) { "AnalyzerDiagnostic.code must not be blank." }
        require(message.isNotBlank()) { "AnalyzerDiagnostic.message must not be blank." }
    }
}

public enum class AnalyzerErrorCode {
    INVALID_WORKSPACE,
    INVALID_TARGET,
    BACKEND_NOT_FOUND,
    BACKEND_INCOMPATIBLE,
    BACKEND_START_FAILED,
    COMPILE_METADATA_UNAVAILABLE,
    BACKEND_PROTOCOL_ERROR,
    QUERY_FAILED,
    CANCELLED,
    INTERNAL,
}

public data class AnalyzerError(
    public val code: AnalyzerErrorCode,
    public val message: String,
    public val recoverable: Boolean,
    public val details: String? = null,
) {
    init {
        require(message.isNotBlank()) { "AnalyzerError.message must not be blank." }
    }
}

public data class AnalyzerStateSnapshot(
    public val status: AnalyzerStatus,
    public val message: String? = null,
    public val lastError: AnalyzerError? = null,
)
