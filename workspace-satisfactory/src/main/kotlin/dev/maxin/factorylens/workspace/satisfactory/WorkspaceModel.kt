package dev.maxin.factorylens.workspace.satisfactory

import dev.maxin.factorylens.core.api.WorkspaceDescriptor
import dev.maxin.factorylens.core.model.SourceUri
import dev.maxin.factorylens.core.model.WorkspaceId
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.normalize

public data class SatisfactoryWorkspace(
    public val root: Path,
    public val projectFile: Path,
    public val modsRoot: Path,
    public val engineAssociation: String,
) {
    public fun toDescriptor(): WorkspaceDescriptor =
        WorkspaceDescriptor(
            id = WorkspaceId(normalizedRoot().toString()),
            rootUri = SourceUri(normalizedRoot().toUri().toString()),
            projectFileUri = SourceUri(projectFile.absolute().normalize().toUri().toString()),
        )

    public fun normalizedRoot(): Path = root.absolute().normalize()
}

public enum class EngineResolutionSource {
    EXPLICIT,
    WINDOWS_REGISTRY,
}

public data class UnrealEngineInstallation(
    public val root: Path,
    public val association: String,
    public val source: EngineResolutionSource,
)
