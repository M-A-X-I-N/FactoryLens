package dev.maxin.factorylens.workspace.satisfactory

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.absolute
import kotlin.io.path.normalize

public data class FileFingerprint(
    public val size: Long,
    public val modifiedMillis: Long,
)

public data class WorkspaceSnapshot(
    public val root: Path,
    public val files: Map<String, FileFingerprint>,
)

public data class WorkspaceMutation(
    public val relativePath: String,
    public val before: FileFingerprint?,
    public val after: FileFingerprint?,
)

public object WorkspaceMutationAudit {
    private val excludedDirectoryNames =
        setOf(
            ".git",
            ".vs",
            "Binaries",
            "Saved",
            "DerivedDataCache",
        )

    public fun capture(root: Path): WorkspaceSnapshot {
        val normalizedRoot = root.absolute().normalize()
        val files = linkedMapOf<String, FileFingerprint>()

        Files.walk(normalizedRoot).use { stream ->
            stream
                .filter { path -> Files.isRegularFile(path) }
                .filter { path -> !hasExcludedDirectory(normalizedRoot, path) }
                .forEach { path ->
                    val attributes = Files.readAttributes(
                        path,
                        BasicFileAttributes::class.java,
                    )
                    files[normalizedRoot.relativize(path).toString()] =
                        FileFingerprint(
                            size = attributes.size(),
                            modifiedMillis = attributes.lastModifiedTime().toMillis(),
                        )
                }
        }

        return WorkspaceSnapshot(
            root = normalizedRoot,
            files = files,
        )
    }

    public fun diff(
        before: WorkspaceSnapshot,
        after: WorkspaceSnapshot,
    ): List<WorkspaceMutation> {
        require(before.root == after.root) {
            "Workspace snapshots must have the same root."
        }

        return (before.files.keys + after.files.keys)
            .toSortedSet()
            .mapNotNull { path ->
                val previous = before.files[path]
                val current = after.files[path]
                if (previous == current) {
                    null
                } else {
                    WorkspaceMutation(
                        relativePath = path,
                        before = previous,
                        after = current,
                    )
                }
            }
    }

    private fun hasExcludedDirectory(
        root: Path,
        path: Path,
    ): Boolean =
        root.relativize(path)
            .any { element -> element.toString() in excludedDirectoryNames }
}
