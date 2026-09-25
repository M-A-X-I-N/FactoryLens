package dev.maxin.factorylens.semantic.clangd

import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

internal object FakeClangdMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val version = args
            .firstOrNull { it.startsWith("--fake-version=") }
            ?.substringAfter('=')
            ?: "20.1.8"
        if ("--version" in args) {
            println("clangd version $version")
            return
        }

        val marker = args
            .firstOrNull { it.startsWith("--marker=") }
            ?.substringAfter('=')
            ?.let { Path.of(it) }
        val advertiseCallHierarchy = "--no-call-hierarchy" !in args
        val input = BufferedInputStream(System.`in`)
        val output = BufferedOutputStream(System.out)
        val gson = Gson()
        var pingSequence = 0
        var shutdownReceived = false

        while (true) {
            val message = readMessage(input) ?: return
            when (message.get("method")?.asString) {
                "initialize" -> {
                    send(
                        output,
                        gson,
                        response(
                            message,
                            JsonObject().apply {
                                add(
                                    "capabilities",
                                    JsonObject().apply {
                                        addProperty(
                                            "callHierarchyProvider",
                                            advertiseCallHierarchy,
                                        )
                                    },
                                )
                                add(
                                    "serverInfo",
                                    JsonObject().apply {
                                        addProperty("name", "fake-clangd")
                                        addProperty("version", version)
                                    },
                                )
                            },
                        ),
                    )
                }

                "initialized" -> {
                    sendProgress(output, gson, "begin")
                    sendProgress(output, gson, "end")
                }

                "factorylens/testPing" -> {
                    pingSequence += 1
                    send(
                        output,
                        gson,
                        response(
                            message,
                            JsonObject().apply {
                                addProperty("sequence", pingSequence)
                            },
                        ),
                    )
                }

                "shutdown" -> {
                    shutdownReceived = true
                    send(output, gson, response(message, JsonNull.INSTANCE))
                }

                "exit" -> {
                    if (shutdownReceived && marker != null) {
                        marker.parent?.let { Files.createDirectories(it) }
                        Files.writeString(marker, "clean")
                    }
                    return
                }

                else -> {
                    if (message.has("id")) {
                        send(output, gson, response(message, JsonNull.INSTANCE))
                    }
                }
            }
        }
    }

    private fun response(
        request: JsonObject,
        result: com.google.gson.JsonElement,
    ): JsonObject =
        JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", request.get("id").deepCopy())
            add("result", result)
        }

    private fun sendProgress(
        output: BufferedOutputStream,
        gson: Gson,
        kind: String,
    ) {
        send(
            output,
            gson,
            JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("method", "$/progress")
                add(
                    "params",
                    JsonObject().apply {
                        addProperty("token", "backgroundIndexProgress")
                        add(
                            "value",
                            JsonObject().apply {
                                addProperty("kind", kind)
                            },
                        )
                    },
                )
            },
        )
    }

    private fun send(
        output: BufferedOutputStream,
        gson: Gson,
        message: JsonObject,
    ) {
        val payload = gson.toJson(message).toByteArray(StandardCharsets.UTF_8)
        output.write(
            "Content-Length: ${payload.size}\r\n\r\n"
                .toByteArray(StandardCharsets.US_ASCII),
        )
        output.write(payload)
        output.flush()
    }

    private fun readMessage(input: BufferedInputStream): JsonObject? {
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readHeaderLine(input) ?: return null
            if (line.isEmpty()) {
                break
            }
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase()] =
                    line.substring(separator + 1).trim()
            }
        }

        val length = headers["content-length"]?.toIntOrNull() ?: return null
        val body = input.readNBytes(length)
        if (body.size != length) {
            return null
        }

        return JsonParser.parseString(
            body.toString(StandardCharsets.UTF_8),
        ).asJsonObject
    }

    private fun readHeaderLine(input: BufferedInputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value < 0) {
                return if (bytes.size() == 0) null else bytes.toString(StandardCharsets.US_ASCII)
            }
            when (value) {
                '\n'.code -> return bytes.toString(StandardCharsets.US_ASCII)
                '\r'.code -> Unit
                else -> bytes.write(value)
            }
        }
    }
}
