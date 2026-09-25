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
        assertTrue(commandText.contains("-NoExecCodeGenActions"))
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

    @Test
    public fun rejectsOutputInsideEngineInstallation(): Unit {
        val fixture = createFixture()
        val result = UbtCompileMetadataGenerator(
            processRunner = ProcessRunner { _, _ ->
                error("UBT must not run for an unsafe output path")
            },
        ).generate(
            UbtCompileMetadataRequest(
                workspace = fixture.workspace,
                engine = fixture.engine,
                outputDirectory = fixture.engine.root.resolve("work/factorylens"),
            ),
        )

        val failure = assertIs<CompileMetadataResult.Failure>(result)
        assertTrue(failure.message.contains("outside the Unreal Engine installation"))
    }

    @Test
    public fun rejectsEmptyCompileDatabase(): Unit {
        val fixture = createFixture()
        val result = UbtCompileMetadataGenerator(
            processRunner = ProcessRunner { _, _ ->
                fixture.output.createDirectories()
                fixture.output.resolve("compile_commands.json").writeText("[]")
                ProcessResult(exitCode = 0, output = "Generated compile database")
            },
            environment = mapOf("COMSPEC" to "cmd.exe"),
        ).generate(fixture.request())

        val failure = assertIs<CompileMetadataResult.Failure>(result)
        assertTrue(failure.message.contains("no translation-unit entries"))
    }

    @Test
    public fun rejectsMalformedCompileDatabase(): Unit {
        val fixture = createFixture()
        val result = UbtCompileMetadataGenerator(
            processRunner = ProcessRunner { _, _ ->
                fixture.output.createDirectories()
                fixture.output.resolve("compile_commands.json").writeText("{not-an-array}")
                ProcessResult(exitCode = 0, output = "Generated compile database")
            },
            environment = mapOf("COMSPEC" to "cmd.exe"),
        ).generate(fixture.request())

        val failure = assertIs<CompileMetadataResult.Failure>(result)
        assertTrue(failure.message.contains("not a JSON array"))
    }

    @Test
    public fun clangCompilerViewIsRepresentable(): Unit {
        val fixture = createFixture()
        var capturedCommand: List<String>? = null
        val result = UbtCompileMetadataGenerator(
            processRunner = ProcessRunner { command, _ ->
                capturedCommand = command
                fixture.output.createDirectories()
                fixture.output.resolve("compile_commands.json").writeText(
                    "[{\"directory\":\"C:/workspace\",\"command\":\"clang-cl.exe\",\"file\":\"C:/workspace/A.cpp\"}]",
                )
                ProcessResult(exitCode = 0, output = "Generated compile database")
            },
            environment = mapOf("COMSPEC" to "cmd.exe"),
        ).generate(fixture.request(UbtCompilerView.CLANG))

        assertIs<CompileMetadataResult.Success>(result)
        assertTrue(capturedCommand.orEmpty().joinToString(" ").contains("-Compiler=Clang"))
    }

    private data class Fixture(
        val workspace: SatisfactoryWorkspace,
        val engine: UnrealEngineInstallation,
        val output: java.nio.file.Path,
    ) {
        fun request(
            compilerView: UbtCompilerView = UbtCompilerView.VISUAL_STUDIO_2022,
        ): UbtCompileMetadataRequest =
            UbtCompileMetadataRequest(
                workspace = workspace,
                engine = engine,
                outputDirectory = output,
                compilerView = compilerView,
            )
    }

    private fun createFixture(): Fixture {
        val workspaceRoot = Files.createTempDirectory("factorylens-workspace")
        val projectFile = workspaceRoot.resolve("FactoryGame.uproject")
        projectFile.writeText("{\"EngineAssociation\":\"5.6.1-CSS\"}")
        val modsRoot = workspaceRoot.resolve("Mods").createDirectories()

        val engineRoot = Files.createTempDirectory("factorylens-engine")
        engineRoot.resolve("Engine/Build/BatchFiles").createDirectories()
        engineRoot.resolve("Engine/Build/BatchFiles/Build.bat").writeText("@echo off")

        return Fixture(
            workspace = SatisfactoryWorkspace(
                root = workspaceRoot,
                projectFile = projectFile,
                modsRoot = modsRoot,
                engineAssociation = "5.6.1-CSS",
            ),
            engine = UnrealEngineInstallation(
                root = engineRoot,
                association = "5.6.1-CSS",
                source = EngineResolutionSource.EXPLICIT,
            ),
            output = Files.createTempDirectory("factorylens-output").resolve("compile-view"),
        )
    }

}
