package dev.maxin.factorylens.cli

import dev.maxin.factorylens.core.FactoryLensProduct
import dev.maxin.factorylens.semantic.clangd.ClangdBackendDescriptor
import dev.maxin.factorylens.workspace.satisfactory.CompileMetadataResult
import dev.maxin.factorylens.workspace.satisfactory.EngineResolutionResult
import dev.maxin.factorylens.workspace.satisfactory.SatisfactoryWorkspaceDiscovery
import dev.maxin.factorylens.workspace.satisfactory.UbtCompileMetadataGenerator
import dev.maxin.factorylens.workspace.satisfactory.UbtCompileMetadataRequest
import dev.maxin.factorylens.workspace.satisfactory.UbtCompilerView
import dev.maxin.factorylens.workspace.satisfactory.UnrealEngineResolver
import dev.maxin.factorylens.workspace.satisfactory.WorkspaceDiscoveryResult
import dev.maxin.factorylens.workspace.satisfactory.WorkspaceMutationAudit
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.system.exitProcess

public fun main(args: Array<String>) {
    val exitCode = when (args.firstOrNull()) {
        "compile-metadata" -> runCompileMetadata(args.drop(1))
        null, "help", "--help", "-h" -> {
            printHelp()
            0
        }
        else -> {
            System.err.println("Unknown FactoryLens CLI command: " + args.first())
            printHelp()
            2
        }
    }

    if (exitCode != 0) {
        exitProcess(exitCode)
    }
}

private fun printHelp(): Unit {
    println(FactoryLensProduct.NAME + " CLI")
    println(ClangdBackendDescriptor.describe())
    println()
    println("Commands:")
    println("  compile-metadata [options]  Discover an SML workspace and generate UBT compile metadata.")
    println()
    println("compile-metadata options:")
    println("  --sml-root PATH       SML Starter Project root; defaults to SML_PROJECT_ROOT/.env.")
    println("  --engine-root PATH    Explicit Unreal Engine root; otherwise EngineAssociation registry lookup.")
    println("  --output PATH         Output directory outside the analyzed workspace.")
    println("  --compiler msvc|clang UBT compiler view; default: msvc.")
    println("  --skip-audit          Skip pre/post workspace mutation audit.")
}

private fun runCompileMetadata(arguments: List<String>): Int {
    val options = parseOptions(arguments) ?: return 2
    val cwd = Path.of("").absolute().normalize()
    val dotenv = readDotEnv(cwd.resolve(".env"))

    val smlRootText =
        options.values["--sml-root"] ?:
        System.getenv("SML_PROJECT_ROOT") ?:
        dotenv["SML_PROJECT_ROOT"]

    if (smlRootText.isNullOrBlank()) {
        System.err.println(
            "SML project root is required via --sml-root, SML_PROJECT_ROOT, or repository .env.",
        )
        return 2
    }

    val smlRoot = Path.of(smlRootText).let { path ->
        if (path.isAbsolute) path else cwd.resolve(path)
    }

    val workspace = when (val result = SatisfactoryWorkspaceDiscovery.discover(smlRoot)) {
        is WorkspaceDiscoveryResult.Success -> result.workspace
        is WorkspaceDiscoveryResult.Failure -> {
            System.err.println("Workspace discovery failed: " + result.message)
            return 2
        }
    }

    val explicitEngineText =
        options.values["--engine-root"] ?:
        System.getenv("FACTORYLENS_ENGINE_ROOT") ?:
        dotenv["FACTORYLENS_ENGINE_ROOT"]
    val explicitEngine = explicitEngineText
        ?.takeIf { it.isNotBlank() }
        ?.let { Path.of(it) }
        ?.let { path -> if (path.isAbsolute) path else cwd.resolve(path) }

    val engine = when (
        val result = UnrealEngineResolver().resolve(
            workspace = workspace,
            explicitRoot = explicitEngine,
        )
    ) {
        is EngineResolutionResult.Success -> result.engine
        is EngineResolutionResult.Failure -> {
            System.err.println("Engine resolution failed: " + result.message)
            return 2
        }
    }

    val compiler = when (options.values["--compiler"]?.lowercase() ?: "msvc") {
        "msvc", "visualstudio2022" -> UbtCompilerView.VISUAL_STUDIO_2022
        "clang" -> UbtCompilerView.CLANG
        else -> {
            System.err.println("--compiler must be either msvc or clang.")
            return 2
        }
    }

    val defaultFolder = when (compiler) {
        UbtCompilerView.VISUAL_STUDIO_2022 -> "msvc"
        UbtCompilerView.CLANG -> "clang"
    }
    val output = options.values["--output"]
        ?.let { Path.of(it) }
        ?.let { path -> if (path.isAbsolute) path else cwd.resolve(path) }
        ?: cwd.resolve("work/factorylens/compile-metadata").resolve(defaultFolder)

    val auditEnabled = "--skip-audit" !in options.flags
    val before = if (auditEnabled) {
        println("Capturing pre-UBT workspace fingerprint...")
        WorkspaceMutationAudit.capture(workspace.root)
    } else {
        null
    }

    println("Workspace    : " + workspace.root)
    println("Project      : " + workspace.projectFile)
    println("Engine assoc : " + workspace.engineAssociation)
    println("Engine       : " + engine.root)
    println("Engine source: " + engine.source)
    println("Compiler view: " + compiler.commandValue)
    println("Output       : " + output)
    println()
    println("Generating FactoryEditor / Win64 / Development compile metadata...")

    val result = UbtCompileMetadataGenerator().generate(
        UbtCompileMetadataRequest(
            workspace = workspace,
            engine = engine,
            outputDirectory = output,
            compilerView = compiler,
        ),
    )

    val success = when (result) {
        is CompileMetadataResult.Success -> result.view
        is CompileMetadataResult.Failure -> {
            System.err.println("Compile metadata generation failed: " + result.message)
            if (result.logPath != null) {
                System.err.println("UBT log: " + result.logPath)
            }
            return 2
        }
    }

    val mutations = if (before != null) {
        println("Capturing post-UBT workspace fingerprint...")
        val after = WorkspaceMutationAudit.capture(workspace.root)
        WorkspaceMutationAudit.diff(before, after)
    } else {
        emptyList()
    }

    val auditPath = output.resolve("workspace-mutation-audit.txt")
    if (before != null) {
        val report = buildString {
            appendLine("workspace=" + workspace.root)
            appendLine("mutation_count=" + mutations.size)
            for (mutation in mutations) {
                appendLine(mutation.relativePath)
                appendLine("  before=" + mutation.before)
                appendLine("  after=" + mutation.after)
            }
        }
        auditPath.writeText(report)
    }

    println()
    println("status=success")
    println("workspace=" + workspace.root)
    println("engine=" + engine.root)
    println("engine_source=" + engine.source)
    println("database=" + success.databasePath)
    println("entries=" + success.entryCount)
    println("compiler=" + success.compilerView.commandValue)
    println("ubt_log=" + success.logPath)
    if (before != null) {
        println("workspace_mutations=" + mutations.size)
        println("workspace_audit=" + auditPath)
    } else {
        println("workspace_mutations=audit-skipped")
    }

    if (mutations.isNotEmpty()) {
        System.err.println()
        System.err.println(
            "Workspace mutation audit detected changes. Inspect the audit before treating B100 as read-only.",
        )
        return 3
    }

    return 0
}

private data class ParsedOptions(
    val values: Map<String, String>,
    val flags: Set<String>,
)

private fun parseOptions(arguments: List<String>): ParsedOptions? {
    val values = linkedMapOf<String, String>()
    val flags = linkedSetOf<String>()
    val valueOptions = setOf(
        "--sml-root",
        "--engine-root",
        "--output",
        "--compiler",
    )
    val flagOptions = setOf("--skip-audit")

    var index = 0
    while (index < arguments.size) {
        val argument = arguments[index]

        if (argument in flagOptions) {
            flags += argument
            index += 1
            continue
        }

        if (argument !in valueOptions) {
            System.err.println("Unknown compile-metadata option: " + argument)
            return null
        }

        val value = arguments.getOrNull(index + 1)
        if (value == null || value.startsWith("--")) {
            System.err.println("Missing value for " + argument)
            return null
        }

        values[argument] = value
        index += 2
    }

    return ParsedOptions(values = values, flags = flags)
}

private fun readDotEnv(path: Path): Map<String, String> {
    if (!path.exists()) {
        return emptyMap()
    }

    return path.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
        .associate { line ->
            val key = line.substringBefore("=").trim()
            val value = line.substringAfter("=").trim().trim('"', '\'')
            key to value
        }
}
