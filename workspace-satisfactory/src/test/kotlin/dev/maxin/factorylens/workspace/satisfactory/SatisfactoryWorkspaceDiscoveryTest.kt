package dev.maxin.factorylens.workspace.satisfactory

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

public class SatisfactoryWorkspaceDiscoveryTest {
    @Test
    public fun discoversConfiguredSmlWorkspace(): Unit {
        val root = Files.createTempDirectory("factorylens-workspace")
        root.resolve("Mods").createDirectories()
        root.resolve("FactoryGame.uproject").writeText(
            """
            {
              "EngineAssociation": "5.6.1-CSS"
            }
            """.trimIndent(),
        )

        val result = SatisfactoryWorkspaceDiscovery.discover(root)
        val success = assertIs<WorkspaceDiscoveryResult.Success>(result)

        assertEquals("5.6.1-CSS", success.workspace.engineAssociation)
        assertEquals(root.toAbsolutePath().normalize(), success.workspace.normalizedRoot())
    }

    @Test
    public fun rejectsWorkspaceWithoutModsDirectory(): Unit {
        val root = Files.createTempDirectory("factorylens-workspace")
        root.resolve("FactoryGame.uproject").writeText(
            """{"EngineAssociation":"5.6.1-CSS"}""",
        )

        val result = SatisfactoryWorkspaceDiscovery.discover(root)
        val failure = assertIs<WorkspaceDiscoveryResult.Failure>(result)

        assert(failure.message.contains("Mods directory"))
    }
}
