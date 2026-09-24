package dev.maxin.factorylens.workspace.satisfactory

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

public class UbtCompileMetadataGeneratorTest {
    @Test
    public fun generatesValidatedCompileDatabaseOutsideWorkspace(): Unit {
        val workspaceRoot = Files.createTempDirectory("factorylens-workspace")
        val projectFile = workspaceRoot.resolve("FactoryGame.uproject")
        projectFile.writeText("""{"EngineAssociation":"5.6.1-CSS"}""")
        val modsRoot = workspaceRoot.resolve("Mods").createDirectories()

        val engineRoot = Files.createTempDirectory("factorylens-engine")
        engineRoot.resolve("Engine/Build/BatchFiles").createDirectories()
        engineRoot.resolve("Engine/Build/BatchFiles/Build.bat").writeText("@echo off")

        val output = Files.createTempDirectory("factorylens-output").resolve("compile-view")
        val workspace = SatisfactoryWorkspace(
            root = workspaceRoot,
            projectFile = projectFile,
            modsRoot = modsRoot,
            engineAssociation = "5.6.1-CSS",
        )
        val engine = UnrealEngineInstallation(
            root = engineRoot,
            association = "5.6.1-CSS",
            source = EngineResolutionSource.EXPLICIT,
        )

        var capturedCommand: List<String>? = null
        val generator = UbtCompileMetadataGenerator(
            processRunner = ProcessRunner { command, _ ->
                capturedCommand = command
                output.createDirectories()
                output.resolve("compile_commands.json").writeText(
                    """
                    [
                      {"directory":"C:/workspace","command":"cl.exe @one.rsp","file":"C:/workspace/A.cpp"},
                      {"directory":"C:/workspace","command":"cl.exe @two.rsp","file":"C:/workspace/B.cpp"}
                    ]
                    """.trimIndent(),
                )
                ProcessResult(exitCode = 0, output = "Generated compile database")
            },
            environment = mapOf("COMSPEC" to "cmd.exe"),
        )

        val result = generator.generate(
            UbtCompileMetadataRequest(
                workspace = workspace,
                engine = engine,
                outputDirectory = output,
            ),
        )

        val success = assertIs<CompileMetadataResult.Success>(result)
        assertEquals(2, success.view.entryCount)
        assertTrue(success.view.databasePath.toFile().isFile())

        val commandText = capturedCommand.orEmpty().joinToString(" ")
        assertTrue(commandText.contains("FactoryEditor"))
        assertTrue(commandText.contains("-Compiler=VisualStudio2022"))
        assertTrue(commandText.contains("-Mode=GenerateClangDatabase"))
        assertTrue(commandText.contains("-OutputDir="))
    }

    @Test
    public fun rejectsOutputInsideAnalyzedWorkspace(): Unit {
        val workspaceRoot = Files.createTempDirectory("factorylens-workspace")
        val projectFile = workspaceRoot.resolve("FactoryGame.uproject")
        projectFile.writeText("""{"EngineAssociation":"5.6.1-CSS"}""")
        val modsRoot = workspaceRoot.resolve("Mods").createDirectories()

        val engineRoot = Files.createTempDirectory("factorylens-engine")
        engineRoot.resolve("Engine/Build/BatchFiles").createDirectories()
        engineRoot.resolve("Engine/Build/BatchFiles/Build.bat").writeText("@echo off")

        val workspace = SatisfactoryWorkspace(
            root = workspaceRoot,
            projectFile = projectFile,
            modsRoot = modsRoot,
            engineAssociation = "5.6.1-CSS",
        )
        val engine = UnrealEngineInstallation(
            root = engineRoot,
            association = "5.6.1-CSS",
            source = EngineResolutionSource.EXPLICIT,
        )

        val result = UbtCompileMetadataGenerator(
            processRunner = ProcessRunner { _, _ ->
                error("UBT must not run for an unsafe output path")
            },
        ).generate(
            UbtCompileMetadataRequest(
                workspace = workspace,
                engine = engine,
                outputDirectory = workspaceRoot.resolve("Intermediate/factorylens"),
            ),
        )

        val failure = assertIs<CompileMetadataResult.Failure>(result)
        assertTrue(failure.message.contains("outside the analyzed SML workspace"))
    }
}
