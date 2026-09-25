package dev.maxin.factorylens.workspace.satisfactory

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class WorkspaceMutationAuditTest {
    @Test
    public fun detectsAddedRemovedAndChangedFiles(): Unit {
        val root = Files.createTempDirectory("factorylens-audit")
        val changed = root.resolve("Changed.txt")
        val removed = root.resolve("Removed.txt")
        changed.writeText("before")
        removed.writeText("remove me")

        val before = WorkspaceMutationAudit.capture(root)

        changed.writeText("after with a different size")
        Files.delete(removed)
        root.resolve("Added.txt").writeText("new")

        val mutations = WorkspaceMutationAudit.diff(
            before,
            WorkspaceMutationAudit.capture(root),
        )

        assertEquals(
            setOf("Added.txt", "Changed.txt", "Removed.txt"),
            mutations.map { it.relativePath }.toSet(),
        )
        assertEquals(
            mapOf(
                "Added.txt" to WorkspaceMutationKind.ADDED,
                "Changed.txt" to WorkspaceMutationKind.CONTENT,
                "Removed.txt" to WorkspaceMutationKind.REMOVED,
            ),
            mutations.associate { it.relativePath to it.kind },
        )
    }

    @Test
    public fun distinguishesMetadataOnlyChangesFromSameSizeContentChanges(): Unit {
        val root = Files.createTempDirectory("factorylens-audit")
        val metadataOnly = root.resolve("MetadataOnly.txt")
        val sameSizeContent = root.resolve("SameSizeContent.txt")
        metadataOnly.writeText("unchanged")
        sameSizeContent.writeText("before")

        val before = WorkspaceMutationAudit.capture(root)
        Files.setLastModifiedTime(
            metadataOnly,
            FileTime.fromMillis(Files.getLastModifiedTime(metadataOnly).toMillis() + 10_000),
        )
        sameSizeContent.writeText("after!")

        val mutations = WorkspaceMutationAudit.diff(
            before,
            WorkspaceMutationAudit.capture(root),
        ).associateBy { it.relativePath }

        assertEquals(
            WorkspaceMutationKind.METADATA_ONLY,
            mutations.getValue("MetadataOnly.txt").kind,
        )
        assertEquals(
            WorkspaceMutationKind.CONTENT,
            mutations.getValue("SameSizeContent.txt").kind,
        )
        assertEquals(
            mutations.getValue("MetadataOnly.txt").before?.sha256,
            mutations.getValue("MetadataOnly.txt").after?.sha256,
        )
    }

    @Test
    public fun keepsIntermediateInAuditButExcludesKnownNoiseDirectories(): Unit {
        val root = Files.createTempDirectory("factorylens-audit")
        root.resolve("Intermediate").createDirectories()
        root.resolve("Saved").createDirectories()
        root.resolve("Intermediate/generated.txt").writeText("tracked")
        root.resolve("Saved/noise.txt").writeText("ignored")

        val snapshot = WorkspaceMutationAudit.capture(root)

        assertTrue(snapshot.files.keys.any { it.contains("Intermediate") })
        assertTrue(snapshot.files.keys.none { it.contains("Saved") })
    }
}
