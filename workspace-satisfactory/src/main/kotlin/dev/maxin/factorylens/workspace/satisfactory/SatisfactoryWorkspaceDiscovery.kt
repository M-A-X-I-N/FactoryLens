package dev.maxin.factorylens.workspace.satisfactory

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.readText

public sealed interface WorkspaceDiscoveryResult {
    public data class Success(
        public val workspace: SatisfactoryWorkspace,
    ) : WorkspaceDiscoveryResult

    public data class Failure(
        public val message: String,
    ) : WorkspaceDiscoveryResult
}

public object SatisfactoryWorkspaceDiscovery {
    private val engineAssociationPattern =
        Regex("""["]EngineAssociation["]\s*:\s*["]([^"]+)["]""")

    public fun discover(root: Path): WorkspaceDiscoveryResult {
        val normalizedRoot = root.absolute().normalize()
        val projectFile = normalizedRoot.resolve("FactoryGame.uproject")
        val modsRoot = normalizedRoot.resolve("Mods")

        if (!Files.isDirectory(normalizedRoot)) {
            return WorkspaceDiscoveryResult.Failure(
                "Satisfactory workspace root is not a directory: " + normalizedRoot,
            )
        }
        if (!Files.isRegularFile(projectFile)) {
            return WorkspaceDiscoveryResult.Failure(
                "FactoryGame.uproject was not found at: " + projectFile,
            )
        }
        if (!Files.isDirectory(modsRoot)) {
            return WorkspaceDiscoveryResult.Failure(
                "SML Mods directory was not found at: " + modsRoot,
            )
        }

        val projectText = try {
            projectFile.readText(Charsets.UTF_8)
        } catch (error: Exception) {
            return WorkspaceDiscoveryResult.Failure(
                "FactoryGame.uproject could not be read: " + error.message,
            )
        }

        val association = engineAssociationPattern.find(projectText)?.groupValues?.get(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return WorkspaceDiscoveryResult.Failure(
                "FactoryGame.uproject does not declare a non-empty EngineAssociation.",
            )

        return WorkspaceDiscoveryResult.Success(
            SatisfactoryWorkspace(
                root = normalizedRoot,
                projectFile = projectFile,
                modsRoot = modsRoot,
                engineAssociation = association,
            ),
        )
    }
}
