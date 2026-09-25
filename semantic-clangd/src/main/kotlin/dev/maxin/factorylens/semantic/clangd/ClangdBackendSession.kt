package dev.maxin.factorylens.semantic.clangd

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

public class ClangdBackendSession internal constructor(
    private val config: ClangdBackendConfig,
    private val process: Process,
    private val connection: JsonRpcConnection,
    public val version: ClangdVersion,
) : AutoCloseable {
    private val closing = AtomicBoolean(false)
    private val openedDocuments = ConcurrentHashMap<String, Unit>()
    private val state = AtomicReference(
        ClangdBackendState(
            status = ClangdBackendStatus.STARTING,
            backgroundIndex = ClangdBackgroundIndexStatus.UNKNOWN,
            processId = process.pid(),
            version = version,
            capabilities = null,
            message = "Initializing clangd.",
        ),
    )

    public val processId: Long
        get() = process.pid()

    public fun state(): ClangdBackendState = state.get()

    internal fun initialize(): ClangdBackendFailure? {
        connection.notificationHandler = ::handleNotification
        connection.terminalFailureHandler = ::handleTerminalFailure

        val initializeResponse = try {
            connection.request(
                "initialize",
                initializeParams(config.workspaceRoot),
            )
        } catch (error: Throwable) {
            return ClangdBackendFailure(
                code = ClangdBackendFailureCode.INITIALIZE_FAILED,
                message = "clangd did not complete LSP initialization.",
                details = error.message,
            )
        }

        if (initializeResponse.has("error")) {
            return ClangdBackendFailure(
                code = ClangdBackendFailureCode.INITIALIZE_FAILED,
                message = "clangd rejected LSP initialization.",
                details = initializeResponse.get("error").toString(),
            )
        }

        val capabilities = parseCapabilities(initializeResponse)
        if (!capabilities.callHierarchyProvider) {
            return ClangdBackendFailure(
                code = ClangdBackendFailureCode.INCOMPATIBLE_CAPABILITIES,
                message =
                    "clangd does not advertise call-hierarchy support required by FactoryLens.",
                details = initializeResponse.get("result")?.toString(),
            )
        }

        state.set(
            ClangdBackendState(
                status = ClangdBackendStatus.READY,
                backgroundIndex = ClangdBackgroundIndexStatus.UNKNOWN,
                processId = process.pid(),
                version = version,
                capabilities = capabilities,
                message = "clangd is initialized and ready for semantic queries.",
            ),
        )

        try {
            connection.notify("initialized", JsonObject())
        } catch (error: Throwable) {
            return ClangdBackendFailure(
                code = ClangdBackendFailureCode.PROTOCOL_ERROR,
                message = "Failed to complete clangd initialization handshake.",
                details = error.message,
            )
        }

        return null
    }

    internal fun request(
        method: String,
        params: JsonElement = JsonObject(),
    ): JsonObject {
        val snapshot = state.get()
        check(snapshot.status == ClangdBackendStatus.READY) {
            "clangd request requires READY state; current state is ${snapshot.status}."
        }
        return connection.request(method, params)
    }

    internal fun notify(
        method: String,
        params: JsonElement = JsonObject(),
    ) {
        val snapshot = state.get()
        check(snapshot.status == ClangdBackendStatus.READY) {
            "clangd notification requires READY state; current state is ${snapshot.status}."
        }
        connection.notify(method, params)
    }

    internal fun openDocument(file: Path): String {
        val normalized = file.toAbsolutePath().normalize()
        require(Files.isRegularFile(normalized)) {
            "clangd document is not a regular file: $normalized"
        }

        val uri = normalized.toUri().toString()
        if (openedDocuments.putIfAbsent(uri, Unit) != null) {
            return uri
        }

        val text = try {
            Files.readString(normalized, StandardCharsets.UTF_8).removePrefix("\uFEFF")
        } catch (error: Throwable) {
            openedDocuments.remove(uri)
            throw error
        }

        try {
            notify(
                "textDocument/didOpen",
                JsonObject().apply {
                    add(
                        "textDocument",
                        JsonObject().apply {
                            addProperty("uri", uri)
                            addProperty("languageId", "cpp")
                            addProperty("version", 1)
                            addProperty("text", text)
                        },
                    )
                },
            )
        } catch (error: Throwable) {
            openedDocuments.remove(uri)
            throw error
        }

        return uri
    }

    private fun handleNotification(message: JsonObject) {
        if (message.get("method")?.asString != "$/progress") {
            return
        }

        val params = message.getAsJsonObject("params") ?: return
        if (params.get("token")?.asString != "backgroundIndexProgress") {
            return
        }

        val value = params.getAsJsonObject("value") ?: return
        val indexStatus = when (value.get("kind")?.asString) {
            "begin", "report" -> ClangdBackgroundIndexStatus.RUNNING
            "end" -> ClangdBackgroundIndexStatus.COMPLETE
            else -> return
        }

        state.updateAndGet { current ->
            if (current.status == ClangdBackendStatus.READY) {
                current.copy(backgroundIndex = indexStatus)
            } else {
                current
            }
        }
    }

    private fun handleTerminalFailure(error: Throwable) {
        if (closing.get()) {
            return
        }

        state.updateAndGet { current ->
            if (current.status == ClangdBackendStatus.STOPPED) {
                current
            } else {
                current.copy(
                    status = ClangdBackendStatus.ERROR,
                    message = "clangd connection terminated unexpectedly: ${error.message}",
                )
            }
        }
    }

    internal fun abortStartup() {
        closing.set(true)
        try {
            process.destroy()
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.SECONDS)
            }
        } catch (_: Throwable) {
            process.destroyForcibly()
        } finally {
            connection.close()
            state.set(
                state.get().copy(
                    status = ClangdBackendStatus.STOPPED,
                    message = "clangd startup aborted.",
                ),
            )
        }
    }

    override fun close() {
        if (!closing.compareAndSet(false, true)) {
            return
        }

        try {
            if (process.isAlive) {
                for (uri in openedDocuments.keys) {
                    try {
                        connection.notify(
                            "textDocument/didClose",
                            JsonObject().apply {
                                add(
                                    "textDocument",
                                    JsonObject().apply {
                                        addProperty("uri", uri)
                                    },
                                )
                            },
                        )
                    } catch (_: Throwable) {
                    }
                }
                openedDocuments.clear()

                try {
                    connection.request("shutdown", JsonObject())
                } catch (_: Throwable) {
                }

                try {
                    connection.notify("exit", JsonObject())
                } catch (_: Throwable) {
                }

                if (!process.waitFor(
                        config.shutdownTimeout.toMillis(),
                        TimeUnit.MILLISECONDS,
                    )
                ) {
                    process.destroy()
                    if (!process.waitFor(1, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                        process.waitFor(1, TimeUnit.SECONDS)
                    }
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
        } finally {
            connection.close()
            state.set(
                state.get().copy(
                    status = ClangdBackendStatus.STOPPED,
                    message = "clangd session stopped.",
                ),
            )
        }
    }

    private fun initializeParams(workspaceRoot: Path): JsonObject =
        JsonObject().apply {
            addProperty("processId", ProcessHandle.current().pid())
            addProperty("rootUri", workspaceRoot.toUri().toString())
            add(
                "clientInfo",
                JsonObject().apply {
                    addProperty("name", "FactoryLens")
                },
            )
            add(
                "capabilities",
                JsonObject().apply {
                    add(
                        "window",
                        JsonObject().apply {
                            addProperty("workDoneProgress", true)
                        },
                    )
                    add(
                        "textDocument",
                        JsonObject().apply {
                            add(
                                "callHierarchy",
                                JsonObject().apply {
                                    addProperty("dynamicRegistration", false)
                                },
                            )
                            add(
                                "documentSymbol",
                                JsonObject().apply {
                                    addProperty(
                                        "hierarchicalDocumentSymbolSupport",
                                        true,
                                    )
                                },
                            )
                            add(
                                "typeHierarchy",
                                JsonObject().apply {
                                    addProperty("dynamicRegistration", false)
                                },
                            )
                        },
                    )
                },
            )
            add(
                "workspaceFolders",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("uri", workspaceRoot.toUri().toString())
                            addProperty(
                                "name",
                                workspaceRoot.fileName?.toString() ?: "workspace",
                            )
                        },
                    )
                },
            )
        }

    private fun parseCapabilities(response: JsonObject): ClangdServerCapabilities {
        val provider = response
            .getAsJsonObject("result")
            ?.getAsJsonObject("capabilities")
            ?.get("callHierarchyProvider")

        val supportsCallHierarchy = when {
            provider == null || provider.isJsonNull -> false
            provider.isJsonObject -> true
            provider.isJsonPrimitive && provider.asJsonPrimitive.isBoolean -> provider.asBoolean
            else -> false
        }

        return ClangdServerCapabilities(
            callHierarchyProvider = supportsCallHierarchy,
        )
    }
}

public object ClangdBackendSessionFactory {
    private val versionPattern = Regex(
        """clangd version\s+(\d+)(?:\.(\d+))?(?:\.(\d+))?""",
        RegexOption.IGNORE_CASE,
    )

    public fun start(config: ClangdBackendConfig): ClangdBackendStartResult {
        validateConfig(config)?.let { failure ->
            return ClangdBackendStartResult.Failure(failure)
        }

        val version = when (val probed = probeVersion(config)) {
            is VersionProbe.Success -> probed.version
            is VersionProbe.Failure -> {
                return ClangdBackendStartResult.Failure(probed.error)
            }
        }

        if (version.major != config.requiredMajorVersion) {
            return ClangdBackendStartResult.Failure(
                ClangdBackendFailure(
                    code = ClangdBackendFailureCode.INCOMPATIBLE_VERSION,
                    message =
                        "FactoryLens currently requires clangd major ${config.requiredMajorVersion}; found ${version.major}.",
                    details = version.raw,
                ),
            )
        }

        val process = try {
            config.stderrLog.parent?.let { parent -> Files.createDirectories(parent) }
            ProcessBuilder(sessionCommand(config))
                .directory(config.workspaceRoot.toFile())
                .redirectError(config.stderrLog.toFile())
                .start()
        } catch (error: IOException) {
            return ClangdBackendStartResult.Failure(
                ClangdBackendFailure(
                    code = ClangdBackendFailureCode.START_FAILED,
                    message = "Failed to start clangd.",
                    details = error.message,
                ),
            )
        }

        val connection = JsonRpcConnection(
            process = process,
            requestTimeout = config.requestTimeout,
        )
        val session = ClangdBackendSession(
            config = config,
            process = process,
            connection = connection,
            version = version,
        )

        val initializeFailure = session.initialize()
        if (initializeFailure != null) {
            session.abortStartup()
            return ClangdBackendStartResult.Failure(initializeFailure)
        }

        return ClangdBackendStartResult.Success(session)
    }

    private fun validateConfig(config: ClangdBackendConfig): ClangdBackendFailure? {
        if (!Files.isRegularFile(config.executable)) {
            return ClangdBackendFailure(
                code = ClangdBackendFailureCode.EXECUTABLE_NOT_FOUND,
                message = "Configured clangd executable does not exist: ${config.executable}",
            )
        }

        val compileDatabase = config.compileCommandsDirectory.resolve("compile_commands.json")
        if (!Files.isRegularFile(compileDatabase)) {
            return ClangdBackendFailure(
                code = ClangdBackendFailureCode.COMPILE_DATABASE_NOT_FOUND,
                message = "compile_commands.json was not found: $compileDatabase",
            )
        }

        if (!Files.isDirectory(config.workspaceRoot)) {
            return ClangdBackendFailure(
                code = ClangdBackendFailureCode.INVALID_WORKSPACE,
                message = "Workspace root is not a directory: ${config.workspaceRoot}",
            )
        }

        return null
    }

    private fun probeVersion(config: ClangdBackendConfig): VersionProbe {
        val process = try {
            ProcessBuilder(versionCommand(config))
                .redirectErrorStream(true)
                .start()
        } catch (error: IOException) {
            return VersionProbe.Failure(
                ClangdBackendFailure(
                    code = ClangdBackendFailureCode.VERSION_PROBE_FAILED,
                    message = "Failed to execute clangd --version.",
                    details = error.message,
                ),
            )
        }

        val completed = try {
            process.waitFor(10, TimeUnit.SECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            return VersionProbe.Failure(
                ClangdBackendFailure(
                    code = ClangdBackendFailureCode.VERSION_PROBE_FAILED,
                    message = "Interrupted while probing clangd version.",
                    details = error.message,
                ),
            )
        }

        if (!completed) {
            process.destroyForcibly()
            return VersionProbe.Failure(
                ClangdBackendFailure(
                    code = ClangdBackendFailureCode.VERSION_PROBE_FAILED,
                    message = "clangd --version timed out.",
                ),
            )
        }

        val output = process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8).trim()
        if (process.exitValue() != 0) {
            return VersionProbe.Failure(
                ClangdBackendFailure(
                    code = ClangdBackendFailureCode.VERSION_PROBE_FAILED,
                    message = "clangd --version exited with code ${process.exitValue()}.",
                    details = output,
                ),
            )
        }

        val match = versionPattern.find(output)
            ?: return VersionProbe.Failure(
                ClangdBackendFailure(
                    code = ClangdBackendFailureCode.VERSION_PROBE_FAILED,
                    message = "Could not parse clangd version output.",
                    details = output,
                ),
            )

        return VersionProbe.Success(
            ClangdVersion(
                raw = output,
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].takeIf(String::isNotEmpty)?.toInt(),
                patch = match.groupValues[3].takeIf(String::isNotEmpty)?.toInt(),
            ),
        )
    }

    private fun versionCommand(config: ClangdBackendConfig): List<String> =
        buildList {
            add(config.executable.toString())
            addAll(config.launcherArguments)
            add("--version")
        }

    private fun sessionCommand(config: ClangdBackendConfig): List<String> =
        buildList {
            add(config.executable.toString())
            addAll(config.launcherArguments)
            add("--compile-commands-dir=${config.compileCommandsDirectory}")
            add(
                if (config.backgroundIndex) {
                    "--background-index"
                } else {
                    "--background-index=0"
                },
            )
            add("--log=info")
        }

    private sealed interface VersionProbe {
        data class Success(
            val version: ClangdVersion,
        ) : VersionProbe

        data class Failure(
            val error: ClangdBackendFailure,
        ) : VersionProbe
    }
}
