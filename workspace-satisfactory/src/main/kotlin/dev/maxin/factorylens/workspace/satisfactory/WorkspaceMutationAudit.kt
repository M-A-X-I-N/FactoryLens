package dev.maxin.factorylens.workspace.satisfactory

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import kotlin.io.path.absolute

public data class FileFingerprint(
    public val size: Long,
    public val modifiedMillis: Long,
    public val sha256: String?,
)

public enum class WorkspaceMutationKind {
    ADDED,
    REMOVED,
    CONTENT,
    METADATA_ONLY,
    UNKNOWN,
}

public data class WorkspaceSnapshot(
    public val root: Path,
    public val files: Map<String, FileFingerprint>,
)

public data class WorkspaceMutation(
    public val relativePath: String,
    public val before: FileFingerprint?,
    public val after: FileFingerprint?,
    public val kind: WorkspaceMutationKind,
)

public object WorkspaceMutationAudit {
    private const val MAX_HASHED_FILE_BYTES: Long = 1_048_576L

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
                            sha256 = if (attributes.size() <= MAX_HASHED_FILE_BYTES) {
                                sha256(path)
                            } else {
                                null
                            },
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
                        kind = classify(previous, current),
                    )
                }
            }
    }

    private fun classify(
        before: FileFingerprint?,
        after: FileFingerprint?,
    ): WorkspaceMutationKind =
        when {
            before == null -> WorkspaceMutationKind.ADDED
            after == null -> WorkspaceMutationKind.REMOVED
            before.size != after.size -> WorkspaceMutationKind.CONTENT
            before.sha256 != null &&
                after.sha256 != null &&
                before.sha256 != after.sha256 -> WorkspaceMutationKind.CONTENT
            before.sha256 != null &&
                after.sha256 != null &&
                before.sha256 == after.sha256 -> WorkspaceMutationKind.METADATA_ONLY
            else -> WorkspaceMutationKind.UNKNOWN
        }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) {
                    break
                }
                digest.update(buffer, 0, count)
            }
        }

        return digest.digest().joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun hasExcludedDirectory(
        root: Path,
        path: Path,
    ): Boolean =
        root.relativize(path)
            .any { element -> element.toString() in excludedDirectoryNames }
}
