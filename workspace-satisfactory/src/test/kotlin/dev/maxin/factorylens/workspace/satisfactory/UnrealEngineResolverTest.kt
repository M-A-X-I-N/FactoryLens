package dev.maxin.factorylens.workspace.satisfactory

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

public class UnrealEngineResolverTest {
    @Test
    public fun resolvesRegisteredEngineAssociation(): Unit {
        val root = Files.createTempDirectory("factorylens-engine")
        root.resolve("Engine/Build/BatchFiles").createDirectories()
        root.resolve("Engine/Build/BatchFiles/Build.bat").writeText("@echo off")

        val workspace = SatisfactoryWorkspace(
            root = Files.createTempDirectory("factorylens-workspace"),
            projectFile = Files.createTempFile("FactoryGame", ".uproject"),
            modsRoot = Files.createTempDirectory("Mods"),
            engineAssociation = "5.6.1-CSS",
        )

        val resolver = UnrealEngineResolver(
            processRunner = ProcessRunner { command, _ ->
                assertEquals("reg.exe", command.first())
                ProcessResult(
                    exitCode = 0,
                    output =
                        "HKEY_CURRENT_USER\\SOFTWARE\\Epic Games\\Unreal Engine\\Builds\n" +
                            "    5.6.1-CSS    REG_SZ    " +
                            root.toAbsolutePath(),
                )
            },
            operatingSystemName = "Windows 11",
        )

        val result = resolver.resolve(workspace)
        val success = assertIs<EngineResolutionResult.Success>(result)

        assertEquals(
            root.toAbsolutePath().normalize(),
            success.engine.root,
        )
        assertEquals(
            EngineResolutionSource.WINDOWS_REGISTRY,
            success.engine.source,
        )
    }

    @Test
    public fun explicitEngineRootWorksWithoutWindowsRegistry(): Unit {
        val root = Files.createTempDirectory("factorylens-engine")
        root.resolve("Engine/Build/BatchFiles").createDirectories()
        root.resolve("Engine/Build/BatchFiles/Build.bat").writeText("@echo off")

        val workspace = SatisfactoryWorkspace(
            root = Files.createTempDirectory("factorylens-workspace"),
            projectFile = Files.createTempFile("FactoryGame", ".uproject"),
            modsRoot = Files.createTempDirectory("Mods"),
            engineAssociation = "5.6.1-CSS",
        )

        val result = UnrealEngineResolver(
            processRunner = ProcessRunner { _, _ ->
                error("registry process should not run")
            },
            operatingSystemName = "Linux",
        ).resolve(
            workspace = workspace,
            explicitRoot = root,
        )

        val success = assertIs<EngineResolutionResult.Success>(result)
        assertEquals(EngineResolutionSource.EXPLICIT, success.engine.source)
    }
}
