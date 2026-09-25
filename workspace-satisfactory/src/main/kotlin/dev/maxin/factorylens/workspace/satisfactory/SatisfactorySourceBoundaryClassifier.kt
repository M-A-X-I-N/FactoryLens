package dev.maxin.factorylens.workspace.satisfactory

import dev.maxin.factorylens.core.api.AnalysisTarget
import dev.maxin.factorylens.core.model.SourceRealm
import dev.maxin.factorylens.core.model.SourceUri
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.absolute

/**
 * Classifies source locations relative to one configured Satisfactory workspace and analysis target.
 *
 * Classification is lexical by normalized path. It deliberately does not resolve symlinks/junctions,
 * because semantic backends should be classified against the paths they actually report.
 */
public class SatisfactorySourceBoundaryClassifier(
    workspace: SatisfactoryWorkspace,
    engine: UnrealEngineInstallation,
    target: AnalysisTarget,
) {
    private val workspaceRoot = workspace.normalizedRoot()
    private val modsRoot = workspace.modsRoot.absolute().normalize()
    private val projectSourceRoot = workspaceRoot.resolve("Source").normalize()
    private val smlRoot = modsRoot.resolve("SML").normalize()
    private val engineRoot = engine.root.absolute().normalize()
    private val targetRoots = target.sourceRoots.map { sourceRoot ->
        requireNotNull(pathFromFileUri(sourceRoot)) {
            "Satisfactory analysis target source roots must use file URIs: ${sourceRoot.value}"
        }
    }

    public fun classify(uri: SourceUri): SourceRealm =
        pathFromFileUri(uri)
            ?.let(::classify)
            ?: SourceRealm.UNKNOWN

    public fun classify(path: Path): SourceRealm {
        val normalized = normalizeAgainstWorkspace(path)

        if (isGenerated(normalized)) {
            return SourceRealm.GENERATED
        }
        if (targetRoots.any(normalized::startsWith)) {
            return SourceRealm.TARGET
        }
        if (normalized.startsWith(engineRoot)) {
            return SourceRealm.UNREAL_ENGINE
        }
        if (normalized.startsWith(smlRoot)) {
            return SourceRealm.SML
        }
        if (normalized.startsWith(projectSourceRoot)) {
            return SourceRealm.FACTORY_GAME
        }
        if (normalized.startsWith(modsRoot)) {
            return SourceRealm.DEPENDENCY_MOD
        }

        return SourceRealm.OTHER_EXTERNAL
    }

    private fun normalizeAgainstWorkspace(path: Path): Path =
        if (path.isAbsolute) {
            path.normalize()
        } else {
            workspaceRoot.resolve(path).normalize()
        }

    private fun isGenerated(path: Path): Boolean {
        if (path.any { segment -> segment.toString().equals("Intermediate", ignoreCase = true) }) {
            return true
        }

        val fileName = path.fileName?.toString()?.lowercase() ?: return false
        return fileName.endsWith(".generated.h") ||
            fileName.endsWith(".gen.cpp") ||
            fileName.endsWith(".ispc.generated.h")
    }

    private fun pathFromFileUri(sourceUri: SourceUri): Path? =
        try {
            val uri = URI(sourceUri.value)
            if (!uri.scheme.equals("file", ignoreCase = true)) {
                null
            } else {
                Path.of(uri).absolute().normalize()
            }
        } catch (_: Exception) {
            null
        }
}
