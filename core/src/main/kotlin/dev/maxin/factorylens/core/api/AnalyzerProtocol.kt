package dev.maxin.factorylens.core.api

import dev.maxin.factorylens.core.model.AnalysisTargetId
import dev.maxin.factorylens.core.model.AnalyzerError
import dev.maxin.factorylens.core.model.AnalyzerProgress
import dev.maxin.factorylens.core.model.AnalyzerStateSnapshot
import dev.maxin.factorylens.core.model.CallExpansion
import dev.maxin.factorylens.core.model.RootDiscovery
import dev.maxin.factorylens.core.model.SourceUri
import dev.maxin.factorylens.core.model.SymbolId
import dev.maxin.factorylens.core.model.WorkspaceId

public data class WorkspaceDescriptor(
    public val id: WorkspaceId,
    public val rootUri: SourceUri,
    public val projectFileUri: SourceUri,
)

public data class AnalysisTarget(
    public val id: AnalysisTargetId,
    public val displayName: String,
    public val sourceRoots: List<SourceUri>,
) {
    init {
        require(displayName.isNotBlank()) { "AnalysisTarget.displayName must not be blank." }
        require(sourceRoots.isNotEmpty()) { "AnalysisTarget.sourceRoots must not be empty." }
    }
}

public data class AnalyzerSessionRequest(
    public val workspace: WorkspaceDescriptor,
    public val target: AnalysisTarget,
)

public sealed interface AnalyzerResult<out T> {
    public data class Success<T>(
        public val value: T,
    ) : AnalyzerResult<T>

    public data class Failure(
        public val error: AnalyzerError,
    ) : AnalyzerResult<Nothing>
}

public fun interface ProgressReporter {
    public fun report(progress: AnalyzerProgress)

    public companion object {
        public val NONE: ProgressReporter = ProgressReporter { }
    }
}

/**
 * IDE-independent supported FactoryLens analyzer session.
 *
 * Implementations may use clangd, Clang, UBT, caches, or other adapters internally,
 * but callers receive only FactoryLens domain types.
 */
public interface FactoryLensAnalyzerSession : AutoCloseable {
    public val workspace: WorkspaceDescriptor
    public val target: AnalysisTarget

    public fun state(): AnalyzerStateSnapshot

    public suspend fun discoverRoots(
        progress: ProgressReporter = ProgressReporter.NONE,
    ): AnalyzerResult<RootDiscovery>

    public suspend fun expandOutgoingCalls(
        origin: SymbolId,
        progress: ProgressReporter = ProgressReporter.NONE,
    ): AnalyzerResult<CallExpansion>

    public suspend fun restart(
        progress: ProgressReporter = ProgressReporter.NONE,
    ): AnalyzerResult<AnalyzerStateSnapshot>

    override fun close()
}

public interface FactoryLensAnalyzer {
    public suspend fun openSession(
        request: AnalyzerSessionRequest,
        progress: ProgressReporter = ProgressReporter.NONE,
    ): AnalyzerResult<FactoryLensAnalyzerSession>
}
