package dev.maxin.factorylens.core.api

import dev.maxin.factorylens.core.model.AnalysisTargetId
import dev.maxin.factorylens.core.model.RootDiscovery

/**
 * One supported source of externally/framework-reachable analysis roots.
 *
 * Providers own discovery/evidence for one root mechanism. Higher analyzer composition may merge
 * several providers without making any individual provider understand unrelated framework rules.
 */
public interface RootProvider : AutoCloseable {
    public val target: AnalysisTargetId

    public fun discoverRoots(
        progress: ProgressReporter = ProgressReporter.NONE,
    ): AnalyzerResult<RootDiscovery>

    override fun close(): Unit = Unit
}
