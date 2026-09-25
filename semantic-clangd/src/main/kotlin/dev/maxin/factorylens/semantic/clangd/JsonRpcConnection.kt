package dev.maxin.factorylens.semantic.clangd

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

internal class JsonRpcProtocolException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

internal class JsonRpcConnection(
    private val process: Process,
    private val requestTimeout: Duration,
) : AutoCloseable {
    private val gson = Gson()
    private val input = BufferedInputStream(process.inputStream)
    private val output = BufferedOutputStream(process.outputStream)
    private val writeLock = Any()
    private val nextRequestId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableFuture<JsonObject>>()

    @Volatile
    private var terminalFailure: Throwable? = null

    @Volatile
    internal var notificationHandler: ((JsonObject) -> Unit)? = null

    @Volatile
    internal var terminalFailureHandler: ((Throwable) -> Unit)? = null

    private val readerThread = Thread.ofPlatform()
        .name("factorylens-clangd-json-rpc")
        .daemon(true)
        .start(::readLoop)

    internal fun request(
        method: String,
        params: JsonElement = JsonObject(),
    ): JsonObject {
        val existingFailure = terminalFailure
        if (existingFailure != null) {
            throw JsonRpcProtocolException(
                "clangd JSON-RPC connection is unavailable.",
                existingFailure,
            )
        }

        val requestId = nextRequestId.getAndIncrement()
        val future = CompletableFuture<JsonObject>()
        pending[requestId] = future

        val message = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", requestId)
            addProperty("method", method)
            add("params", params)
        }

        try {
            send(message)
        } catch (error: Throwable) {
            pending.remove(requestId)
            throw error
        }

        try {
            return future.get(
                requestTimeout.toMillis(),
                TimeUnit.MILLISECONDS,
            )
        } catch (error: TimeoutException) {
            pending.remove(requestId)
            throw JsonRpcProtocolException(
                "Timed out waiting for clangd response to $method.",
                error,
            )
        } catch (error: ExecutionException) {
            throw JsonRpcProtocolException(
                "clangd request failed: $method.",
                error.cause ?: error,
            )
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw JsonRpcProtocolException(
                "Interrupted while waiting for clangd response to $method.",
                error,
            )
        }
    }

    internal fun notify(
        method: String,
        params: JsonElement = JsonObject(),
    ) {
        send(
            JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("method", method)
                add("params", params)
            },
        )
    }

    private fun send(message: JsonObject) {
        val payload = gson.toJson(message).toByteArray(StandardCharsets.UTF_8)
        val header = "Content-Length: ${payload.size}\r\n\r\n"
            .toByteArray(StandardCharsets.US_ASCII)

        synchronized(writeLock) {
            val existingFailure = terminalFailure
            if (existingFailure != null) {
                throw JsonRpcProtocolException(
                    "clangd JSON-RPC connection is unavailable.",
                    existingFailure,
                )
            }

            output.write(header)
            output.write(payload)
            output.flush()
        }
    }

    private fun readLoop() {
        try {
            while (true) {
                val message = readMessage()
                    ?: throw EOFException(
                        "clangd closed stdout with exit code ${process.poll()}.",
                    )
                dispatch(message)
            }
        } catch (error: Throwable) {
            terminalFailure = error
            pending.values.forEach { future ->
                future.completeExceptionally(error)
            }
            pending.clear()
            terminalFailureHandler?.invoke(error)
        }
    }

    private fun dispatch(message: JsonObject) {
        val method = message.get("method")
        val id = message.get("id")

        if (method != null && id != null) {
            respondToServerRequest(message, id)
            return
        }

        if (method != null) {
            notificationHandler?.invoke(message)
            return
        }

        if (id != null && id.isJsonPrimitive && id.asJsonPrimitive.isNumber) {
            val requestId = id.asLong
            pending.remove(requestId)?.complete(message)
        }
    }

    private fun respondToServerRequest(
        request: JsonObject,
        id: JsonElement,
    ) {
        val result = when (request.get("method")?.asString) {
            "workspace/configuration" -> {
                val items = request
                    .getAsJsonObject("params")
                    ?.getAsJsonArray("items")
                JsonArray().apply {
                    repeat(items?.size() ?: 0) {
                        add(JsonNull.INSTANCE)
                    }
                }
            }

            else -> JsonNull.INSTANCE
        }

        send(
            JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                add("id", id.deepCopy())
                add("result", result)
            },
        )
    }

    private fun readMessage(): JsonObject? {
        val headers = linkedMapOf<String, String>()

        while (true) {
            val line = readHeaderLine() ?: return null
            if (line.isEmpty()) {
                break
            }

            val separator = line.indexOf(':')
            if (separator <= 0) {
                continue
            }

            headers[line.substring(0, separator).trim().lowercase()] =
                line.substring(separator + 1).trim()
        }

        val contentLength = headers["content-length"]?.toIntOrNull()
            ?: throw JsonRpcProtocolException(
                "clangd LSP message is missing a valid Content-Length header: $headers",
            )
        if (contentLength <= 0) {
            throw JsonRpcProtocolException(
                "clangd LSP Content-Length must be positive: $headers",
            )
        }

        val body = input.readNBytes(contentLength)
        if (body.size != contentLength) {
            throw EOFException(
                "clangd stdout ended mid-message: expected $contentLength bytes, got ${body.size}.",
            )
        }

        val parsed = JsonParser.parseString(
            body.toString(StandardCharsets.UTF_8),
        )
        if (!parsed.isJsonObject) {
            throw JsonRpcProtocolException(
                "clangd LSP payload was not a JSON object.",
            )
        }

        return parsed.asJsonObject
    }

    private fun readHeaderLine(): String? {
        val bytes = ByteArrayOutputStream()

        while (true) {
            val value = input.read()
            if (value < 0) {
                return if (bytes.size() == 0) {
                    null
                } else {
                    bytes.toString(StandardCharsets.US_ASCII)
                }
            }

            when (value) {
                '\n'.code -> return bytes.toString(StandardCharsets.US_ASCII)
                '\r'.code -> Unit
                else -> bytes.write(value)
            }
        }
    }

    override fun close() {
        try {
            output.close()
        } catch (_: Throwable) {
        }
        try {
            input.close()
        } catch (_: Throwable) {
        }

        if (readerThread !== Thread.currentThread()) {
            try {
                readerThread.join(1_000)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}
