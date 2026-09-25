package dev.maxin.factorylens.semantic.clangd

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.maxin.factorylens.core.api.AnalyzerResult
import dev.maxin.factorylens.core.model.AnalysisTargetId
import dev.maxin.factorylens.core.model.AnalyzerDiagnostic
import dev.maxin.factorylens.core.model.AnalyzerError
import dev.maxin.factorylens.core.model.AnalyzerErrorCode
import dev.maxin.factorylens.core.model.CallEdge
import dev.maxin.factorylens.core.model.CallEdgeScope
import dev.maxin.factorylens.core.model.CallExpansion
import dev.maxin.factorylens.core.model.DiagnosticSeverity
import dev.maxin.factorylens.core.model.EvidenceConfidence
import dev.maxin.factorylens.core.model.EvidenceKind
import dev.maxin.factorylens.core.model.EvidenceRecord
import dev.maxin.factorylens.core.model.GraphNode
import dev.maxin.factorylens.core.model.NavigationTargets
import dev.maxin.factorylens.core.model.ResultCompleteness
import dev.maxin.factorylens.core.model.SourceLocation
import dev.maxin.factorylens.core.model.SourcePosition
import dev.maxin.factorylens.core.model.SourceRange
import dev.maxin.factorylens.core.model.SourceRealm
import dev.maxin.factorylens.core.model.SourceRealmClassifier
import dev.maxin.factorylens.core.model.SourceUri
import dev.maxin.factorylens.core.model.SymbolDescriptor
import dev.maxin.factorylens.core.model.SymbolId
import dev.maxin.factorylens.core.model.SymbolKind
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Supported clangd call-hierarchy adapter.
 *
 * Raw LSP CallHierarchyItem objects are retained only inside this adapter. Product callers receive
 * FactoryLens domain identities and graph records.
 */
public class ClangdCallHierarchyAdapter(
    private val session: ClangdBackendSession,
    private val target: AnalysisTargetId,
    private val realmClassifier: SourceRealmClassifier,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val items = ConcurrentHashMap<SymbolId, JsonObject>()
    private val symbols = ConcurrentHashMap<SymbolId, SymbolDescriptor>()
    private val openedDocuments = ConcurrentHashMap<SourceUri, Unit>()

    public fun prepareSymbol(
        sourceFile: Path,
        position: SourcePosition,
    ): AnalyzerResult<SymbolDescriptor> {
        if (closed.get()) {
            return failure(
                code = AnalyzerErrorCode.QUERY_FAILED,
                message = "clangd call-hierarchy adapter is closed.",
                recoverable = false,
            )
        }

        val file = sourceFile.toAbsolutePath().normalize()
        if (!Files.isRegularFile(file)) {
            return failure(
                code = AnalyzerErrorCode.QUERY_FAILED,
                message = "Call-hierarchy source file does not exist: $file",
                recoverable = false,
            )
        }

        val uri = SourceUri(file.toUri().toString())
        val openFailure = ensureDocumentOpen(file, uri)
        if (openFailure != null) {
            return AnalyzerResult.Failure(openFailure)
        }

        val response = try {
            session.request(
                "textDocument/prepareCallHierarchy",
                JsonObject().apply {
                    add(
                        "textDocument",
                        JsonObject().apply { addProperty("uri", uri.value) },
                    )
                    add("position", position.toLspJson())
                },
            )
        } catch (error: Throwable) {
            return failure(
                code = AnalyzerErrorCode.QUERY_FAILED,
                message = "clangd prepareCallHierarchy request failed.",
                recoverable = true,
                details = error.message,
            )
        }

        response.protocolError()?.let { details ->
            return failure(
                code = AnalyzerErrorCode.QUERY_FAILED,
                message = "clangd rejected prepareCallHierarchy.",
                recoverable = true,
                details = details,
            )
        }

        val candidates = response.get("result")
        if (candidates == null || candidates.isJsonNull) {
            return failure(
                code = AnalyzerErrorCode.QUERY_FAILED,
                message = "clangd returned no call-hierarchy symbol at the requested position.",
                recoverable = true,
            )
        }
        if (!candidates.isJsonArray) {
            return failure(
                code = AnalyzerErrorCode.BACKEND_PROTOCOL_ERROR,
                message = "clangd prepareCallHierarchy returned a non-array result.",
                recoverable = false,
                details = candidates.toString(),
            )
        }

        val parsed = candidates.asJsonArray
            .mapNotNull { element -> element.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .mapNotNull(::parseItem)
        if (parsed.isEmpty()) {
            return failure(
                code = AnalyzerErrorCode.QUERY_FAILED,
                message = "clangd returned no usable call-hierarchy symbol at the requested position.",
                recoverable = true,
            )
        }

        val selected = parsed.firstOrNull { item ->
            item.selectionRange.contains(position)
        } ?: parsed.first()
        register(selected)
        return AnalyzerResult.Success(selected.symbol)
    }

    public fun expandOutgoingCalls(origin: SymbolId): AnalyzerResult<CallExpansion> {
        if (closed.get()) {
            return failure(
                code = AnalyzerErrorCode.QUERY_FAILED,
                message = "clangd call-hierarchy adapter is closed.",
                recoverable = false,
            )
        }

        val originItem = items[origin]
            ?: return failure(
                code = AnalyzerErrorCode.QUERY_FAILED,
                message = "Symbol is not prepared in this clangd session: ${origin.value}",
                recoverable = true,
            )
        val originSymbol = symbols[origin]
            ?: return failure(
                code = AnalyzerErrorCode.INTERNAL,
                message = "Prepared clangd symbol is missing its FactoryLens descriptor.",
                recoverable = false,
            )

        val response = try {
            session.request(
                "callHierarchy/outgoingCalls",
                JsonObject().apply { add("item", originItem.deepCopy()) },
            )
        } catch (error: Throwable) {
            return failure(
                code = AnalyzerErrorCode.QUERY_FAILED,
                message = "clangd outgoing-call request failed.",
                recoverable = true,
                details = error.message,
            )
        }

        response.protocolError()?.let { details ->
            return failure(
                code = AnalyzerErrorCode.QUERY_FAILED,
                message = "clangd rejected callHierarchy/outgoingCalls.",
                recoverable = true,
                details = details,
            )
        }

        val diagnostics = mutableListOf(
            AnalyzerDiagnostic(
                code = "clangd-call-hierarchy-partial",
                severity = DiagnosticSeverity.INFO,
                message =
                    "Outgoing call hierarchy is static semantic evidence, not proof of all runtime/framework dispatch.",
            ),
        )
        val result = response.get("result")
        val outgoing = when {
            result == null || result.isJsonNull -> JsonArray()
            result.isJsonArray -> result.asJsonArray
            else -> {
                return failure(
                    code = AnalyzerErrorCode.BACKEND_PROTOCOL_ERROR,
                    message = "clangd outgoingCalls returned a non-array result.",
                    recoverable = false,
                    details = result.toString(),
                )
            }
        }

        val edgeAccumulators = linkedMapOf<SymbolId, EdgeAccumulator>()
        var skippedEntries = 0
        for (entryElement in outgoing) {
            if (!entryElement.isJsonObject) {
                skippedEntries += 1
                continue
            }
            val entry = entryElement.asJsonObject
            val calleeObject = entry.get("to")
                ?.takeIf(JsonElement::isJsonObject)
                ?.asJsonObject
            val callee = calleeObject?.let(::parseItem)
            if (callee == null) {
                skippedEntries += 1
                continue
            }

            register(callee)
            val accumulator = edgeAccumulators.getOrPut(callee.symbol.id) {
                EdgeAccumulator(callee.symbol)
            }
            val ranges = entry.get("fromRanges")
            if (ranges != null && ranges.isJsonArray && hasTrustworthyCallSiteUri(originItem)) {
                for (rangeElement in ranges.asJsonArray) {
                    parseRange(rangeElement)?.let { range ->
                        accumulator.callSites += SourceLocation(
                            uri = SourceUri(originItem.get("uri").asString),
                            range = range,
                        )
                    }
                }
            }
        }

        if (skippedEntries > 0) {
            diagnostics += AnalyzerDiagnostic(
                code = "clangd-call-hierarchy-malformed-entry",
                severity = DiagnosticSeverity.WARNING,
                message = "Skipped $skippedEntries malformed clangd outgoing-call entries.",
            )
        }
        if (!hasTrustworthyCallSiteUri(originItem) && outgoing.size() > 0) {
            diagnostics += AnalyzerDiagnostic(
                code = "clangd-call-site-uri-ambiguous",
                severity = DiagnosticSeverity.INFO,
                message =
                    "clangd returned outgoing call ranges without a trustworthy source-file URI; " +
                    "call-site navigation was omitted rather than mapped through a canonical declaration URI.",
            )
        }

        val nodes = edgeAccumulators.values
            .map { accumulator -> GraphNode(accumulator.symbol) }
        val edges = edgeAccumulators.values.map { accumulator ->
            val callSites = accumulator.callSites.distinct()
            CallEdge(
                caller = origin,
                callee = accumulator.symbol.id,
                scope = if (accumulator.symbol.realm == SourceRealm.TARGET) {
                    CallEdgeScope.TARGET_LOCAL
                } else {
                    CallEdgeScope.BOUNDARY
                },
                callSites = callSites,
                evidence = listOf(
                    EvidenceRecord(
                        kind = EvidenceKind.SEMANTIC_CALL,
                        confidence = EvidenceConfidence.CONFIRMED,
                        summary = "clangd resolved a direct outgoing call.",
                        location = callSites.firstOrNull(),
                        relatedSymbols = listOf(origin, accumulator.symbol.id),
                    ),
                ),
            )
        }

        return AnalyzerResult.Success(
            CallExpansion(
                target = target,
                origin = origin,
                nodes = nodes,
                edges = edges,
                completeness = ResultCompleteness.PARTIAL,
                diagnostics = diagnostics,
            ),
        )
    }

    public fun symbol(symbolId: SymbolId): SymbolDescriptor? = symbols[symbolId]

    private fun ensureDocumentOpen(
        file: Path,
        uri: SourceUri,
    ): AnalyzerError? {
        if (openedDocuments.putIfAbsent(uri, Unit) != null) {
            return null
        }

        val text = try {
            Files.readString(file, StandardCharsets.UTF_8).removePrefix("\uFEFF")
        } catch (error: Throwable) {
            openedDocuments.remove(uri)
            return AnalyzerError(
                code = AnalyzerErrorCode.QUERY_FAILED,
                message = "Could not read source file for clangd: $file",
                recoverable = true,
                details = error.message,
            )
        }

        return try {
            session.notify(
                "textDocument/didOpen",
                JsonObject().apply {
                    add(
                        "textDocument",
                        JsonObject().apply {
                            addProperty("uri", uri.value)
                            addProperty("languageId", "cpp")
                            addProperty("version", 1)
                            addProperty("text", text)
                        },
                    )
                },
            )
            null
        } catch (error: Throwable) {
            openedDocuments.remove(uri)
            AnalyzerError(
                code = AnalyzerErrorCode.QUERY_FAILED,
                message = "Could not open source document in clangd: $file",
                recoverable = true,
                details = error.message,
            )
        }
    }

    private fun parseItem(item: JsonObject): ParsedItem? {
        val name = item.stringOrNull("name")?.takeIf(String::isNotBlank) ?: return null
        val kindNumber = item.get("kind")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asInt
            ?: return null
        val uriValue = item.stringOrNull("uri") ?: return null
        val uri = try { SourceUri(uriValue) } catch (_: IllegalArgumentException) { return null }
        val range = parseRange(item.get("range")) ?: return null
        val selectionRange = parseRange(item.get("selectionRange")) ?: return null
        val symbolId = symbolId(
            uri = uri,
            name = name,
            kind = kindNumber,
            selectionRange = selectionRange,
        )
        val symbol = SymbolDescriptor(
            id = symbolId,
            displayName = name,
            qualifiedName = null,
            kind = mapSymbolKind(kindNumber),
            realm = realmClassifier.classify(uri),
            navigation = NavigationTargets(
                declaration = SourceLocation(
                    uri = uri,
                    range = selectionRange,
                ),
            ),
        )
        return ParsedItem(
            raw = item.deepCopy(),
            symbol = symbol,
            range = range,
            selectionRange = selectionRange,
        )
    }

    private fun register(item: ParsedItem) {
        items.putIfAbsent(item.symbol.id, item.raw)
        symbols.putIfAbsent(item.symbol.id, item.symbol)
    }

    private fun symbolId(
        uri: SourceUri,
        name: String,
        kind: Int,
        selectionRange: SourceRange,
    ): SymbolId {
        val canonical = buildString {
            append(uri.value)
            append('\u0000')
            append(name)
            append('\u0000')
            append(kind)
            append('\u0000')
            append(selectionRange.start.line)
            append(':')
            append(selectionRange.start.column)
            append('-')
            append(selectionRange.end.line)
            append(':')
            append(selectionRange.end.column)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return SymbolId("clangd-call:$digest")
    }

    private fun mapSymbolKind(kind: Int): SymbolKind =
        when (kind) {
            3 -> SymbolKind.NAMESPACE
            5, 10, 11, 22, 23, 26 -> SymbolKind.TYPE
            6 -> SymbolKind.METHOD
            7, 8 -> SymbolKind.FIELD
            9 -> SymbolKind.CONSTRUCTOR
            12 -> SymbolKind.FUNCTION
            13, 14 -> SymbolKind.VARIABLE
            else -> SymbolKind.OTHER
        }

    private fun hasTrustworthyCallSiteUri(item: JsonObject): Boolean {
        val uri = item.stringOrNull("uri") ?: return false
        val path = try {
            val parsed = java.net.URI(uri)
            if (!parsed.scheme.equals("file", ignoreCase = true)) {
                return false
            }
            Path.of(parsed)
        } catch (_: Exception) {
            return false
        }

        return when (path.fileName?.toString()?.substringAfterLast('.', "")?.lowercase()) {
            "cpp", "cc", "cxx" -> true
            else -> false
        }
    }

    private fun parseRange(element: JsonElement?): SourceRange? {
        if (element == null || !element.isJsonObject) {
            return null
        }
        val value = element.asJsonObject
        val start = parsePosition(value.get("start")) ?: return null
        val end = parsePosition(value.get("end")) ?: return null
        return try {
            SourceRange(start = start, end = end)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun parsePosition(element: JsonElement?): SourcePosition? {
        if (element == null || !element.isJsonObject) {
            return null
        }
        val value = element.asJsonObject
        val line = value.get("line")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asInt
            ?: return null
        val column = value.get("character")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asInt
            ?: return null
        return try {
            SourcePosition(line = line, column = column)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun JsonObject.protocolError(): String? =
        get("error")?.takeUnless(JsonElement::isJsonNull)?.toString()

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString

    private fun SourcePosition.toLspJson(): JsonObject =
        JsonObject().apply {
            addProperty("line", line)
            addProperty("character", column)
        }

    private fun SourceRange.contains(position: SourcePosition): Boolean =
        !position.isBefore(start) && position.isBefore(end)

    private fun SourcePosition.isBefore(other: SourcePosition): Boolean =
        line < other.line || (line == other.line && column < other.column)

    private fun <T> failure(
        code: AnalyzerErrorCode,
        message: String,
        recoverable: Boolean,
        details: String? = null,
    ): AnalyzerResult<T> =
        AnalyzerResult.Failure(
            AnalyzerError(
                code = code,
                message = message,
                recoverable = recoverable,
                details = details,
            ),
        )

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        for (uri in openedDocuments.keys) {
            try {
                session.notify(
                    "textDocument/didClose",
                    JsonObject().apply {
                        add(
                            "textDocument",
                            JsonObject().apply { addProperty("uri", uri.value) },
                        )
                    },
                )
            } catch (_: Throwable) {
            }
        }
        openedDocuments.clear()
    }

    private data class ParsedItem(
        val raw: JsonObject,
        val symbol: SymbolDescriptor,
        val range: SourceRange,
        val selectionRange: SourceRange,
    )

    private data class EdgeAccumulator(
        val symbol: SymbolDescriptor,
        val callSites: MutableList<SourceLocation> = mutableListOf(),
    )
}
