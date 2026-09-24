package dev.maxin.factorylens.workspace.satisfactory

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertIs

public class WindowsBatchInvocationTest {
    @Test
    public fun realCmdInvocationHandlesPathsWithSpaces(): Unit {
        if (!System.getProperty("os.name").lowercase().contains("windows")) {
            return
        }

        val workspaceRoot = Files.createTempDirectory("FactoryLens Workspace With Spaces ")
        val projectFile = workspaceRoot.resolve("FactoryGame.uproject")
        projectFile.writeText("""{"EngineAssociation":"test"}""")
        val modsRoot = workspaceRoot.resolve("Mods").createDirectories()

        val engineRoot = Files.createTempDirectory("FactoryLens Engine With Spaces ")
        val batchDir = engineRoot.resolve("Engine/Build/BatchFiles").createDirectories()
        val output = Files.createTempDirectory("FactoryLens Output With Spaces ")

        val compileDatabase = output.resolve("compile_commands.json")
        batchDir.resolve("Build.bat").writeText(
            "@echo off\r\n" +
                ">\"" + compileDatabase + "\" echo " +
                "[{\"directory\":\"C:/workspace\",\"command\":\"cl.exe\",\"file\":\"C:/workspace/A.cpp\"}]\r\n" +
                "exit /b 0\r\n",
        )

        val result = UbtCompileMetadataGenerator().generate(
            UbtCompileMetadataRequest(
                workspace = SatisfactoryWorkspace(
                    root = workspaceRoot,
                    projectFile = projectFile,
                    modsRoot = modsRoot,
                    engineAssociation = "test",
                ),
                engine = UnrealEngineInstallation(
                    root = engineRoot,
                    association = "test",
                    source = EngineResolutionSource.EXPLICIT,
                ),
                outputDirectory = output,
            ),
        )

        assertIs<CompileMetadataResult.Success>(result)
    }
}
