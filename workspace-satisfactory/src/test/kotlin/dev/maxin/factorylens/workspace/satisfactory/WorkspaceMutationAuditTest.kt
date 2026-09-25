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
    public fun acceptsOnlyExistingUbtTimestampBookkeepingRewrites(): Unit {
        val before = FileFingerprint(
            size = 100,
            modifiedMillis = 1,
            sha256 = "before",
        )
        val after = FileFingerprint(
            size = 100,
            modifiedMillis = 2,
            sha256 = "after",
        )

        assertTrue(
            WorkspaceMutationAudit.isAcceptedUbtTimestampBookkeeping(
                WorkspaceMutation(
                    relativePath =
                        "Mods\\FicsitWiremod\\Intermediate\\Build\\Win64\\UnrealEditor\\Inc\\FicsitWiremod\\UHT\\Timestamp",
                    before = before,
                    after = after,
                    kind = WorkspaceMutationKind.CONTENT,
                ),
            ),
        )
        assertTrue(
            WorkspaceMutationAudit.isAcceptedUbtTimestampBookkeeping(
                WorkspaceMutation(
                    relativePath =
                        "Intermediate/Build/Win64/UnrealEditor/Inc/FactoryEditor/UHT/Timestamp",
                    before = before,
                    after = after.copy(sha256 = "before"),
                    kind = WorkspaceMutationKind.METADATA_ONLY,
                ),
            ),
        )
        assertTrue(
            !WorkspaceMutationAudit.isAcceptedUbtTimestampBookkeeping(
                WorkspaceMutation(
                    relativePath =
                        "Mods\\FicsitWiremod\\Source\\FicsitWiremod\\UHT\\Timestamp",
                    before = before,
                    after = after,
                    kind = WorkspaceMutationKind.CONTENT,
                ),
            ),
        )
        assertTrue(
            !WorkspaceMutationAudit.isAcceptedUbtTimestampBookkeeping(
                WorkspaceMutation(
                    relativePath =
                        "Mods\\FicsitWiremod\\Intermediate\\Build\\Win64\\UnrealEditor\\Inc\\FicsitWiremod\\UHT\\Timestamp",
                    before = null,
                    after = after,
                    kind = WorkspaceMutationKind.ADDED,
                ),
            ),
        )
        assertTrue(
            !WorkspaceMutationAudit.isAcceptedUbtTimestampBookkeeping(
                WorkspaceMutation(
                    relativePath =
                        "Mods\\FicsitWiremod\\Intermediate\\Build\\Win64\\UnrealEditor\\Inc\\FicsitWiremod\\FicsitWiremod.generated.h",
                    before = before,
                    after = after,
                    kind = WorkspaceMutationKind.CONTENT,
                ),
            ),
        )
    }

    @Test
    public fun acceptsOnlyExistingUbtCompileMetadataBuildStateRewrites(): Unit {
        val before = FileFingerprint(
            size = 100,
            modifiedMillis = 1,
            sha256 = "before",
        )
        val after = FileFingerprint(
            size = 100,
            modifiedMillis = 2,
            sha256 = "after",
        )

        val acceptedPaths = listOf(
            "Intermediate\\Build\\Win64\\x64\\UnrealEditor\\Development\\FactoryEditor\\FactoryEditor.Shared.rsp",
            "Mods\\FicsitWiremod\\Intermediate\\Build\\Win64\\x64\\UnrealEditor\\Development\\FicsitWiremod\\Wiremod.cpp.obj.rsp.old",
            "Mods\\GameFeatures\\RSS\\Intermediate\\Build\\Win64\\x64\\UnrealEditor\\Development\\RSS\\Definitions.h",
            "Mods\\SML\\Intermediate\\Build\\Win64\\x64\\UnrealEditor\\Development\\SML\\Definitions.h.old",
            "Intermediate\\Build\\Win64\\x64\\FactoryEditor\\Development\\TargetMetadata.dat",
        )
        for (path in acceptedPaths) {
            assertTrue(
                WorkspaceMutationAudit.isAcceptedUbtCompileMetadataBuildState(
                    WorkspaceMutation(
                        relativePath = path,
                        before = before,
                        after = after,
                        kind = WorkspaceMutationKind.CONTENT,
                    ),
                ),
            )
        }

        assertTrue(
            WorkspaceMutationAudit.isAcceptedUbtCompileMetadataBuildState(
                WorkspaceMutation(
                    relativePath =
                        "Intermediate\\Build\\Win64\\x64\\FactoryEditor\\Development\\TargetMetadata.dat",
                    before = before.copy(sha256 = null),
                    after = after.copy(sha256 = null),
                    kind = WorkspaceMutationKind.UNKNOWN,
                ),
            ),
        )
        assertTrue(
            !WorkspaceMutationAudit.isAcceptedUbtCompileMetadataBuildState(
                WorkspaceMutation(
                    relativePath =
                        "Mods\\FicsitWiremod\\Source\\FicsitWiremod\\Private\\Wiremod.cpp.obj.rsp",
                    before = before,
                    after = after,
                    kind = WorkspaceMutationKind.CONTENT,
                ),
            ),
        )
        assertTrue(
            !WorkspaceMutationAudit.isAcceptedUbtCompileMetadataBuildState(
                WorkspaceMutation(
                    relativePath =
                        "Mods\\FicsitWiremod\\Intermediate\\Build\\Win64\\x64\\UnrealEditor\\Development\\FicsitWiremod\\Wiremod.cpp.obj.rsp",
                    before = null,
                    after = after,
                    kind = WorkspaceMutationKind.ADDED,
                ),
            ),
        )
        assertTrue(
            !WorkspaceMutationAudit.isAcceptedUbtCompileMetadataBuildState(
                WorkspaceMutation(
                    relativePath =
                        "Mods\\FicsitWiremod\\Intermediate\\Build\\Win64\\x64\\UnrealEditor\\Development\\FicsitWiremod\\Module.FicsitWiremod.gen.cpp",
                    before = before,
                    after = after,
                    kind = WorkspaceMutationKind.CONTENT,
                ),
            ),
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
