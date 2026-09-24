package dev.maxin.factorylens.workspace.satisfactory

import java.nio.file.Path
import kotlin.io.path.absolute

public data class ProcessResult(
    public val exitCode: Int,
    public val output: String,
)

public fun interface ProcessRunner {
    public fun run(
        command: List<String>,
        workingDirectory: Path?,
    ): ProcessResult
}

public object SystemProcessRunner : ProcessRunner {
    override fun run(
        command: List<String>,
        workingDirectory: Path?,
    ): ProcessResult {
        require(command.isNotEmpty()) { "Process command must not be empty." }

        val builder = ProcessBuilder(command)
            .redirectErrorStream(true)

        if (workingDirectory != null) {
            builder.directory(workingDirectory.absolute().normalize().toFile())
        }

        val process = builder.start()
        val output = process.inputStream
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val exitCode = process.waitFor()

        return ProcessResult(exitCode = exitCode, output = output)
    }
}
