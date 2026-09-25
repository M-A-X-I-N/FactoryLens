package dev.maxin.factorylens.workspace.satisfactory

import dev.maxin.factorylens.core.api.AnalysisTarget
import dev.maxin.factorylens.core.model.SourceRealm
import dev.maxin.factorylens.core.model.SourceRealmClassifier
import dev.maxin.factorylens.core.model.SourceUri
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolute

/**
 * Classifies source locations relative to one configured Satisfactory workspace and analysis target.
 *
 * Semantic backends may canonicalize a source path through a symlink or Windows junction before
 * reporting it. Classification therefore compares both the configured lexical paths and their
 * real-path equivalents when those paths exist. This preserves target ownership without rewriting
 * the source URI clangd actually returned.
 */
public class SatisfactorySourceBoundaryClassifier(
    workspace: SatisfactoryWorkspace,
    engine: UnrealEngineInstallation,
    target: AnalysisTarget,
) : SourceRealmClassifier {
    private val canonicalPathCache = ConcurrentHashMap<Path, Path>()
    private val workspaceRoot = workspace.normalizedRoot()
    private val modsRoots = pathVariants(workspace.modsRoot.absolute().normalize())
    private val projectSourceRoots = pathVariants(workspaceRoot.resolve("Source").normalize())
    private val smlRoots = pathVariants(workspace.modsRoot.absolute().normalize().resolve("SML"))
    private val engineRoots = pathVariants(engine.root.absolute().normalize())
    private val targetRoots = target.sourceRoots
        .flatMap { sourceRoot ->
            val path = requireNotNull(pathFromFileUri(sourceRoot)) {
                "Satisfactory analysis target source roots must use file URIs: ${sourceRoot.value}"
            }
            pathVariants(path)
        }
        .toSet()

    override fun classify(uri: SourceUri): SourceRealm =
        pathFromFileUri(uri)
            ?.let(::classify)
            ?: SourceRealm.UNKNOWN

    public fun classify(path: Path): SourceRealm {
        val normalized = normalizeAgainstWorkspace(path)
        val candidates = pathVariants(normalized)

        if (candidates.any(::isGenerated)) {
            return SourceRealm.GENERATED
        }
        if (matchesAnyRoot(candidates, targetRoots)) {
            return SourceRealm.TARGET
        }
        if (matchesAnyRoot(candidates, engineRoots)) {
            return SourceRealm.UNREAL_ENGINE
        }
        if (matchesAnyRoot(candidates, smlRoots)) {
            return SourceRealm.SML
        }
        if (matchesAnyRoot(candidates, projectSourceRoots)) {
            return SourceRealm.FACTORY_GAME
        }
        if (matchesAnyRoot(candidates, modsRoots)) {
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

    private fun pathVariants(path: Path): Set<Path> {
        val normalized = path.absolute().normalize()
        val canonical = canonicalPathCache.computeIfAbsent(normalized) { candidate ->
            try {
                candidate.toRealPath()
            } catch (_: Exception) {
                candidate
            }
        }
        return if (canonical == normalized) {
            setOf(normalized)
        } else {
            setOf(normalized, canonical)
        }
    }

    private fun matchesAnyRoot(
        candidates: Set<Path>,
        roots: Set<Path>,
    ): Boolean =
        candidates.any { candidate ->
            roots.any(candidate::startsWith)
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
