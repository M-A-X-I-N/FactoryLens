package dev.maxin.factorylens.workspace.satisfactory

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

public enum class UbtCompilerView(
    public val commandValue: String,
) {
    VISUAL_STUDIO_2022("VisualStudio2022"),
    CLANG("Clang"),
}

public data class UbtCompileMetadataRequest(
    public val workspace: SatisfactoryWorkspace,
    public val engine: UnrealEngineInstallation,
    public val outputDirectory: Path,
    public val target: String = "FactoryEditor",
    public val platform: String = "Win64",
    public val configuration: String = "Development",
    public val compilerView: UbtCompilerView = UbtCompilerView.VISUAL_STUDIO_2022,
)

public data class CompileMetadataView(
    public val databasePath: Path,
    public val logPath: Path,
    public val entryCount: Int,
    public val target: String,
    public val platform: String,
    public val configuration: String,
    public val compilerView: UbtCompilerView,
    public val command: List<String>,
)

public sealed interface CompileMetadataResult {
    public data class Success(
        public val view: CompileMetadataView,
    ) : CompileMetadataResult

    public data class Failure(
        public val message: String,
        public val logPath: Path? = null,
    ) : CompileMetadataResult
}

public class UbtCompileMetadataGenerator(
    private val processRunner: ProcessRunner = SystemProcessRunner,
    private val environment: Map<String, String> = System.getenv(),
) {
    public fun generate(request: UbtCompileMetadataRequest): CompileMetadataResult {
        val workspaceRoot = request.workspace.normalizedRoot()
        val engineRoot = request.engine.root.absolute().normalize()
        val outputDirectory = request.outputDirectory.absolute().normalize()

        if (outputDirectory.isWithin(workspaceRoot)) {
            return CompileMetadataResult.Failure(
                "Compile metadata output must be outside the analyzed SML workspace: " +
                    outputDirectory,
            )
        }
        if (outputDirectory.isWithin(engineRoot)) {
            return CompileMetadataResult.Failure(
                "Compile metadata output must be outside the Unreal Engine installation: " +
                    outputDirectory,
            )
        }

        outputDirectory.createDirectories()

        val buildBatch =
            engineRoot.resolve("Engine").resolve("Build").resolve("BatchFiles").resolve("Build.bat")
        if (!buildBatch.isRegularFile()) {
            return CompileMetadataResult.Failure(
                "Unreal Engine Build.bat was not found at: " + buildBatch,
            )
        }

        val databasePath = outputDirectory.resolve("compile_commands.json")
        val logPath = outputDirectory.resolve("ubt-generate-clang-database.log")
        Files.deleteIfExists(databasePath)

        val ubtArguments = listOf(
            request.target,
            request.platform,
            request.configuration,
            "-Project=" + request.workspace.projectFile,
            "-Compiler=" + request.compilerView.commandValue,
            "-Mode=GenerateClangDatabase",
            "-NoExecCodeGenActions",
            "-OutputDir=" + outputDirectory,
            "-WaitMutex",
        )

        val command = windowsBatchCommand(buildBatch, ubtArguments)
        val result = try {
            processRunner.run(
                command = command,
                workingDirectory = buildBatch.parent,
            )
        } catch (error: Exception) {
            return CompileMetadataResult.Failure(
                "Failed to start UnrealBuildTool frontend: " + error.message,
            )
        }

        logPath.writeText(result.output, Charsets.UTF_8)

        if (result.exitCode != 0) {
            return CompileMetadataResult.Failure(
                "UnrealBuildTool exited with code " + result.exitCode + ".",
                logPath = logPath,
            )
        }
        if (!databasePath.isRegularFile()) {
            return CompileMetadataResult.Failure(
                "UnrealBuildTool completed without producing compile_commands.json at: " +
                    databasePath,
                logPath = logPath,
            )
        }

        val entryCount = try {
            countTopLevelArrayObjects(databasePath.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            return CompileMetadataResult.Failure(
                "compile_commands.json could not be validated: " + error.message,
                logPath = logPath,
            )
        }

        if (entryCount <= 0) {
            return CompileMetadataResult.Failure(
                "compile_commands.json contains no translation-unit entries.",
                logPath = logPath,
            )
        }

        return CompileMetadataResult.Success(
            CompileMetadataView(
                databasePath = databasePath,
                logPath = logPath,
                entryCount = entryCount,
                target = request.target,
                platform = request.platform,
                configuration = request.configuration,
                compilerView = request.compilerView,
                command = command,
            ),
        )
    }

    private fun windowsBatchCommand(
        batchFile: Path,
        arguments: List<String>,
    ): List<String> {
        val comspec = environment["COMSPEC"]?.takeIf { it.isNotBlank() } ?: "cmd.exe"
        val quotedCommand = (listOf(batchFile.toString()) + arguments)
            .joinToString(" ") { quoteForCmd(it) }
        val commandText = "\"" + quotedCommand + "\""

        return listOf(
            comspec,
            "/d",
            "/s",
            "/c",
            commandText,
        )
    }

    private fun quoteForCmd(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""

    private fun countTopLevelArrayObjects(json: String): Int {
        var index = 0
        while (index < json.length && json[index].isWhitespace()) {
            index += 1
        }
        require(index < json.length && json[index] == '[') {
            "Compilation database is not a JSON array."
        }
        index += 1

        var depth = 0
        var count = 0
        var inString = false
        var escaped = false

        while (index < json.length) {
            val ch = json[index]

            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                index += 1
                continue
            }

            when (ch) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) {
                        count += 1
                    }
                    depth += 1
                }
                '}' -> {
                    depth -= 1
                    require(depth >= 0) {
                        "Compilation database has unbalanced object braces."
                    }
                }
                ']' -> {
                    require(depth == 0) {
                        "Compilation database array ended inside an object."
                    }
                    return count
                }
            }

            index += 1
        }

        error("Compilation database JSON array is not terminated.")
    }
}

private fun Path.isWithin(parent: Path): Boolean {
    val normalized = absolute().normalize()
    val normalizedParent = parent.absolute().normalize()
    return normalized == normalizedParent || normalized.startsWith(normalizedParent)
}
