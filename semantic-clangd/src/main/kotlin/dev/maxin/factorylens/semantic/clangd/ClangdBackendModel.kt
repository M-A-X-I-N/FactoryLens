package dev.maxin.factorylens.semantic.clangd

import java.nio.file.Path
import java.time.Duration

public data class ClangdVersion(
    public val raw: String,
    public val major: Int,
    public val minor: Int?,
    public val patch: Int?,
)

public enum class ClangdBackendStatus {
    STARTING,
    READY,
    STOPPED,
    ERROR,
}

public enum class ClangdBackgroundIndexStatus {
    UNKNOWN,
    RUNNING,
    COMPLETE,
}

public data class ClangdServerCapabilities(
    public val callHierarchyProvider: Boolean,
)

public data class ClangdBackendState(
    public val status: ClangdBackendStatus,
    public val backgroundIndex: ClangdBackgroundIndexStatus,
    public val processId: Long?,
    public val version: ClangdVersion?,
    public val capabilities: ClangdServerCapabilities?,
    public val message: String? = null,
)

public data class ClangdBackendConfig(
    public val executable: Path,
    public val compileCommandsDirectory: Path,
    public val workspaceRoot: Path,
    public val stderrLog: Path,
    public val launcherArguments: List<String> = emptyList(),
    public val requiredMajorVersion: Int = 20,
    public val requestTimeout: Duration = Duration.ofSeconds(30),
    public val shutdownTimeout: Duration = Duration.ofSeconds(10),
    public val backgroundIndex: Boolean = true,
) {
    init {
        require(requiredMajorVersion > 0) {
            "ClangdBackendConfig.requiredMajorVersion must be positive."
        }
        require(!requestTimeout.isNegative && !requestTimeout.isZero) {
            "ClangdBackendConfig.requestTimeout must be positive."
        }
        require(!shutdownTimeout.isNegative && !shutdownTimeout.isZero) {
            "ClangdBackendConfig.shutdownTimeout must be positive."
        }
    }
}

public enum class ClangdBackendFailureCode {
    EXECUTABLE_NOT_FOUND,
    COMPILE_DATABASE_NOT_FOUND,
    INVALID_WORKSPACE,
    VERSION_PROBE_FAILED,
    INCOMPATIBLE_VERSION,
    INCOMPATIBLE_CAPABILITIES,
    START_FAILED,
    INITIALIZE_FAILED,
    PROTOCOL_ERROR,
}

public data class ClangdBackendFailure(
    public val code: ClangdBackendFailureCode,
    public val message: String,
    public val details: String? = null,
) {
    init {
        require(message.isNotBlank()) {
            "ClangdBackendFailure.message must not be blank."
        }
    }
}

public sealed interface ClangdBackendStartResult {
    public data class Success(
        public val session: ClangdBackendSession,
    ) : ClangdBackendStartResult

    public data class Failure(
        public val error: ClangdBackendFailure,
    ) : ClangdBackendStartResult
}
