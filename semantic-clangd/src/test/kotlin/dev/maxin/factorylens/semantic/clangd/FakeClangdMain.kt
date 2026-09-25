package dev.maxin.factorylens.semantic.clangd

import com.google.gson.Gson
import com.google.gson.JsonArray
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
        var prepareSequence = 0
        var shutdownReceived = false
        val targetCalleeUri = args
            .firstOrNull { it.startsWith("--target-callee-uri=") }
            ?.substringAfter('=')
        val boundaryCalleeUri = args
            .firstOrNull { it.startsWith("--boundary-callee-uri=") }
            ?.substringAfter('=')
        val overrideHeaderUri = args
            .firstOrNull { it.startsWith("--override-header-uri=") }
            ?.substringAfter('=')
        val overrideBaseUri = args
            .firstOrNull { it.startsWith("--override-base-uri=") }
            ?.substringAfter('=')

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

                "textDocument/prepareCallHierarchy" -> {
                    prepareSequence += 1
                    val params = message.getAsJsonObject("params")
                    val uri = params
                        .getAsJsonObject("textDocument")
                        .get("uri")
                        .asString
                    val position = params.getAsJsonObject("position")
                    val line = position?.get("line")?.asInt ?: 2
                    val overrideFixture = uri == overrideHeaderUri && line == 2
                    send(
                        output,
                        gson,
                        response(
                            message,
                            JsonArray().apply {
                                add(
                                    callHierarchyItem(
                                        name =
                                            if (overrideFixture) {
                                                "ProjectClass::Tick"
                                            } else {
                                                "RootMethod"
                                            },
                                        uri = uri,
                                        line = line,
                                        startCharacter =
                                            if (overrideFixture) 9 else 4,
                                        endCharacter =
                                            if (overrideFixture) 13 else 14,
                                        opaqueSequence = prepareSequence,
                                    ),
                                )
                            },
                        ),
                    )
                }

                "textDocument/documentSymbol" -> {
                    val uri = message
                        .getAsJsonObject("params")
                        .getAsJsonObject("textDocument")
                        .get("uri")
                        .asString
                    val result =
                        if (uri == overrideHeaderUri) {
                            JsonArray().apply {
                                add(
                                    JsonObject().apply {
                                        addProperty("name", "ProjectClass")
                                        addProperty("kind", 5)
                                        add("range", rangeJson(0, 0, 8, 1))
                                        add("selectionRange", rangeJson(0, 6, 0, 18))
                                        add(
                                            "children",
                                            JsonArray().apply {
                                                add(
                                                    JsonObject().apply {
                                                        addProperty("name", "Tick")
                                                        addProperty("kind", 6)
                                                        add("range", rangeJson(2, 4, 2, 30))
                                                        add(
                                                            "selectionRange",
                                                            rangeJson(2, 9, 2, 13),
                                                        )
                                                    },
                                                )
                                                add(
                                                    JsonObject().apply {
                                                        addProperty("name", "CheckCopy")
                                                        addProperty("kind", 6)
                                                        add("range", rangeJson(5, 4, 5, 25))
                                                        add(
                                                            "selectionRange",
                                                            rangeJson(5, 9, 5, 18),
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            }
                        } else {
                            JsonArray()
                        }
                    send(output, gson, response(message, result))
                }

                "textDocument/ast" -> {
                    val uri = message
                        .getAsJsonObject("params")
                        .getAsJsonObject("textDocument")
                        .get("uri")
                        .asString
                    val result =
                        if (uri == overrideHeaderUri) {
                            JsonObject().apply {
                                addProperty("kind", "CXXRecord")
                                addProperty("detail", "ProjectClass")
                                add("range", rangeJson(0, 0, 8, 1))
                                add(
                                    "children",
                                    JsonArray().apply {
                                        add(
                                            JsonObject().apply {
                                                addProperty("kind", "CXXMethod")
                                                addProperty("detail", "Tick")
                                                add("range", rangeJson(2, 4, 2, 30))
                                                add(
                                                    "children",
                                                    JsonArray().apply {
                                                        add(
                                                            JsonObject().apply {
                                                                addProperty("role", "attribute")
                                                                addProperty("kind", "Override")
                                                                addProperty("detail", "override")
                                                                add(
                                                                    "range",
                                                                    rangeJson(2, 20, 2, 28),
                                                                )
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                        add(
                                            JsonObject().apply {
                                                addProperty("kind", "CXXMethod")
                                                addProperty("detail", "CheckCopy")
                                                add("range", rangeJson(5, 4, 5, 25))
                                                add("children", JsonArray())
                                            },
                                        )
                                    },
                                )
                            }
                        } else {
                            JsonNull.INSTANCE
                        }
                    send(output, gson, response(message, result))
                }

                "textDocument/definition" -> {
                    val params = message.getAsJsonObject("params")
                    val uri = params
                        .getAsJsonObject("textDocument")
                        .get("uri")
                        .asString
                    val position = params.getAsJsonObject("position")
                    val line = position?.get("line")?.asInt
                    val result =
                        if (
                            uri == overrideHeaderUri &&
                            line == 2 &&
                            overrideBaseUri != null
                        ) {
                            JsonArray().apply {
                                add(
                                    JsonObject().apply {
                                        addProperty("uri", overrideBaseUri)
                                        add("range", rangeJson(40, 4, 40, 18))
                                    },
                                )
                            }
                        } else {
                            JsonArray()
                        }
                    send(output, gson, response(message, result))
                }

                "callHierarchy/outgoingCalls" -> {
                    val targetUri = targetCalleeUri
                    val boundaryUri = boundaryCalleeUri
                    val result = JsonArray()
                    if (targetUri != null) {
                        result.add(
                            outgoingCall(
                                callee = callHierarchyItem(
                                    name = "LocalCallee",
                                    uri = targetUri,
                                    line = 4,
                                    startCharacter = 2,
                                    endCharacter = 13,
                                    opaqueSequence = 100,
                                ),
                                fromRanges = listOf(
                                    intArrayOf(2, 20, 2, 31),
                                    intArrayOf(3, 8, 3, 19),
                                ),
                            ),
                        )
                        result.add(
                            outgoingCall(
                                callee = callHierarchyItem(
                                    name = "LocalCallee",
                                    uri = targetUri,
                                    line = 4,
                                    startCharacter = 2,
                                    endCharacter = 13,
                                    opaqueSequence = 101,
                                ),
                                fromRanges = listOf(
                                    intArrayOf(3, 8, 3, 19),
                                ),
                            ),
                        )
                    }
                    if (boundaryUri != null) {
                        result.add(
                            outgoingCall(
                                callee = callHierarchyItem(
                                    name = "EngineBoundary",
                                    uri = boundaryUri,
                                    line = 7,
                                    startCharacter = 1,
                                    endCharacter = 15,
                                    opaqueSequence = 200,
                                ),
                                fromRanges = listOf(
                                    intArrayOf(5, 4, 5, 18),
                                ),
                            ),
                        )
                    }
                    send(output, gson, response(message, result))
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

    private fun callHierarchyItem(
        name: String,
        uri: String,
        line: Int,
        startCharacter: Int,
        endCharacter: Int,
        opaqueSequence: Int,
    ): JsonObject =
        JsonObject().apply {
            addProperty("name", name)
            addProperty("kind", 6)
            addProperty("uri", uri)
            add(
                "range",
                rangeJson(line, 0, line, endCharacter + 8),
            )
            add(
                "selectionRange",
                rangeJson(line, startCharacter, line, endCharacter),
            )
            add(
                "data",
                JsonObject().apply {
                    addProperty("opaqueSequence", opaqueSequence)
                },
            )
        }

    private fun outgoingCall(
        callee: JsonObject,
        fromRanges: List<IntArray>,
    ): JsonObject =
        JsonObject().apply {
            add("to", callee)
            add(
                "fromRanges",
                JsonArray().apply {
                    fromRanges.forEach { coordinates ->
                        add(
                            rangeJson(
                                coordinates[0],
                                coordinates[1],
                                coordinates[2],
                                coordinates[3],
                            ),
                        )
                    }
                },
            )
        }

    private fun rangeJson(
        startLine: Int,
        startCharacter: Int,
        endLine: Int,
        endCharacter: Int,
    ): JsonObject =
        JsonObject().apply {
            add(
                "start",
                JsonObject().apply {
                    addProperty("line", startLine)
                    addProperty("character", startCharacter)
                },
            )
            add(
                "end",
                JsonObject().apply {
                    addProperty("line", endLine)
                    addProperty("character", endCharacter)
                },
            )
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
