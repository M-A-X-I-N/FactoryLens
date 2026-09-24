package dev.maxin.factorylens.workspace.satisfactory

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.normalize

public sealed interface EngineResolutionResult {
    public data class Success(
        public val engine: UnrealEngineInstallation,
    ) : EngineResolutionResult

    public data class Failure(
        public val message: String,
    ) : EngineResolutionResult
}

public class UnrealEngineResolver(
    private val processRunner: ProcessRunner = SystemProcessRunner,
    private val operatingSystemName: String = System.getProperty("os.name"),
) {
    public fun resolve(
        workspace: SatisfactoryWorkspace,
        explicitRoot: Path? = null,
    ): EngineResolutionResult {
        if (explicitRoot != null) {
            return validateEngineRoot(
                explicitRoot,
                workspace.engineAssociation,
                EngineResolutionSource.EXPLICIT,
            )
        }

        if (!operatingSystemName.lowercase().contains("windows")) {
            return EngineResolutionResult.Failure(
                "Automatic Unreal Engine association lookup is currently supported only on Windows; " +
                    "provide an explicit engine root.",
            )
        }

        val result = processRunner.run(
            command = listOf(
                "reg.exe",
                "query",
                "HKCU\\SOFTWARE\\Epic Games\\Unreal Engine\\Builds",
                "/v",
                workspace.engineAssociation,
            ),
            workingDirectory = null,
        )

        if (result.exitCode != 0) {
            return EngineResolutionResult.Failure(
                "Registered Unreal Engine association '" + workspace.engineAssociation + "' was not found.",
            )
        }

        val rootValue = parseRegistryStringValue(
            output = result.output,
            valueName = workspace.engineAssociation,
        ) ?: return EngineResolutionResult.Failure(
            "Windows registry lookup returned no usable path for '" +
                workspace.engineAssociation +
                "'.",
        )

        return validateEngineRoot(
            Path.of(rootValue),
            workspace.engineAssociation,
            EngineResolutionSource.WINDOWS_REGISTRY,
        )
    }

    private fun validateEngineRoot(
        root: Path,
        association: String,
        source: EngineResolutionSource,
    ): EngineResolutionResult {
        val normalizedRoot = root.absolute().normalize()
        val buildBatch =
            normalizedRoot.resolve("Engine").resolve("Build").resolve("BatchFiles").resolve("Build.bat")

        if (!Files.isRegularFile(buildBatch)) {
            return EngineResolutionResult.Failure(
                "Unreal Engine Build.bat was not found at: " + buildBatch,
            )
        }

        return EngineResolutionResult.Success(
            UnrealEngineInstallation(
                root = normalizedRoot,
                association = association,
                source = source,
            ),
        )
    }

    private fun parseRegistryStringValue(
        output: String,
        valueName: String,
    ): String? {
        val line = output.lineSequence()
            .firstOrNull { candidate ->
                candidate.contains(valueName) && candidate.contains("REG_SZ")
            }
            ?: return null

        return line
            .substringAfter("REG_SZ", missingDelimiterValue = "")
            .trim()
            .takeIf { it.isNotEmpty() }
    }
}
