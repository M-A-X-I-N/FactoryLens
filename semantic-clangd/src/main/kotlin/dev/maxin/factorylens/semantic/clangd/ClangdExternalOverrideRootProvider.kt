package dev.maxin.factorylens.semantic.clangd

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.maxin.factorylens.core.api.AnalysisTarget
import dev.maxin.factorylens.core.api.AnalyzerResult
import dev.maxin.factorylens.core.api.ProgressReporter
import dev.maxin.factorylens.core.api.RootProvider
import dev.maxin.factorylens.core.model.AnalyzerDiagnostic
import dev.maxin.factorylens.core.model.AnalyzerError
import dev.maxin.factorylens.core.model.AnalyzerErrorCode
import dev.maxin.factorylens.core.model.AnalyzerProgress
import dev.maxin.factorylens.core.model.AnalyzerStage
import dev.maxin.factorylens.core.model.DiagnosticSeverity
import dev.maxin.factorylens.core.model.EvidenceConfidence
import dev.maxin.factorylens.core.model.EvidenceKind
import dev.maxin.factorylens.core.model.EvidenceRecord
import dev.maxin.factorylens.core.model.ResultCompleteness
import dev.maxin.factorylens.core.model.RootDescriptor
import dev.maxin.factorylens.core.model.RootDiscovery
import dev.maxin.factorylens.core.model.RootId
import dev.maxin.factorylens.core.model.RootKind
import dev.maxin.factorylens.core.model.RootPriority
import dev.maxin.factorylens.core.model.SourceLocation
import dev.maxin.factorylens.core.model.SourcePosition
import dev.maxin.factorylens.core.model.SourceRange
import dev.maxin.factorylens.core.model.SourceRealm
import dev.maxin.factorylens.core.model.SourceRealmClassifier
import dev.maxin.factorylens.core.model.SourceUri
import dev.maxin.factorylens.core.model.SymbolDescriptor
import dev.maxin.factorylens.core.model.SymbolId
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.extension

/**
 * Discovers generic external virtual overrides through clangd's foreground Clang AST.
 *
 * Discovery intentionally does not parse C++ source text or depend on the background index's
 * incomplete reverse OverriddenBy relation. documentSymbol supplies authored classes/methods,
 * textDocument/ast supplies semantic OverrideAttr/FinalAttr nodes, and definition lookup at the
 * attribute resolves the concrete overridden declaration.
 *
 * Every accepted project method is also prepared through the shared call-hierarchy adapter so the
 * RootDescriptor symbol identity can immediately be consumed by supported outgoing-call expansion.
 */
public class ClangdExternalOverrideRootProvider(
    private val session: ClangdBackendSession,
    private val analysisTarget: AnalysisTarget,
    private val realmClassifier: SourceRealmClassifier,
    private val callHierarchy: ClangdCallHierarchyAdapter,
) : RootProvider {
    override val target = analysisTarget.id

    private val closed = AtomicBoolean(false)
    private val targetRoots = analysisTarget.sourceRoots.map { sourceRoot ->
        requireNotNull(pathFromFileUri(sourceRoot)) {
            "External-override target source roots must use file URIs: ${sourceRoot.value}"
        }
    }

    override fun discoverRoots(
        progress: ProgressReporter,
    ): AnalyzerResult<RootDiscovery> {
        if (closed.get()) {
            return failure(
                code = AnalyzerErrorCode.QUERY_FAILED,
                message = "clangd external-override provider is closed.",
                recoverable = false,
            )
        }

        val headers = discoverHeaders()
        if (headers.isEmpty()) {
            return AnalyzerResult.Success(
                RootDiscovery(
                    target = target,
                    roots = emptyList(),
                    completeness = ResultCompleteness.COMPLETE,
                    diagnostics = listOf(
                        AnalyzerDiagnostic(
                            code = "external-override-no-headers",
                            severity = DiagnosticSeverity.INFO,
                            message =
                                "No C/C++ headers were found beneath the configured target roots.",
                        ),
                    ),
                ),
            )
        }

        val roots = linkedMapOf<SymbolId, RootAccumulator>()
        val diagnostics = mutableListOf<AnalyzerDiagnostic>()
        var degraded = false

        progress.report(
            AnalyzerProgress(
                stage = AnalyzerStage.DISCOVERING_ROOTS,
                message = "Scanning target headers for semantic external overrides.",
                completedUnits = 0,
                totalUnits = headers.size.toLong(),
            ),
        )

        headers.forEachIndexed { index, header ->
            val result = inspectHeader(header, roots)
            if (result != null) {
                degraded = true
                diagnostics += result
            }

            progress.report(
                AnalyzerProgress(
                    stage = AnalyzerStage.DISCOVERING_ROOTS,
                    message = "Scanned ${index + 1} of ${headers.size} target headers.",
                    completedUnits = (index + 1).toLong(),
                    totalUnits = headers.size.toLong(),
                ),
            )
        }

        diagnostics += AnalyzerDiagnostic(
            code = "external-override-foreground-ast",
            severity = DiagnosticSeverity.INFO,
            message =
                "External overrides were discovered from foreground Clang AST override relations; " +
                "results do not require a complete reverse background index.",
        )

        return AnalyzerResult.Success(
            RootDiscovery(
                target = target,
                roots = roots.values
                    .map(RootAccumulator::descriptor)
                    .sortedWith(
                        compareBy(
                            { root -> root.label ?: root.symbol.qualifiedName ?: root.symbol.displayName },
                            { root -> root.symbol.id.value },
                        ),
                    ),
                completeness =
                    if (degraded) {
                        ResultCompleteness.PARTIAL
                    } else {
                        ResultCompleteness.COMPLETE
                    },
                diagnostics = diagnostics.distinct(),
            ),
        )
    }

    private fun inspectHeader(
        header: Path,
        roots: MutableMap<SymbolId, RootAccumulator>,
    ): AnalyzerDiagnostic? {
        val uriValue = try {
            session.openDocument(header)
        } catch (error: Throwable) {
            return diagnostic(
                code = "external-override-open-failed",
                message = "Could not open target header in clangd: $header",
                details = error.message,
            )
        }
        val uri = SourceUri(uriValue)

        val symbolsResponse = request(
            method = "textDocument/documentSymbol",
            params = JsonObject().apply {
                add(
                    "textDocument",
                    JsonObject().apply { addProperty("uri", uri.value) },
                )
            },
        ) ?: return diagnostic(
            code = "external-override-document-symbol-failed",
            message = "clangd documentSymbol failed for target header: $header",
        )

        symbolsResponse.protocolError()?.let { error ->
            return diagnostic(
                code = "external-override-document-symbol-rejected",
                message = "clangd rejected documentSymbol for target header: $header",
                details = error,
            )
        }

        val rawSymbols = symbolsResponse.get("result")
        if (rawSymbols == null || rawSymbols.isJsonNull) {
            return null
        }
        if (!rawSymbols.isJsonArray) {
            return diagnostic(
                code = "external-override-document-symbol-malformed",
                message = "clangd returned a non-array documentSymbol result for: $header",
                details = rawSymbols.toString(),
            )
        }

        var degraded: AnalyzerDiagnostic? = null
        for (classSymbol in classSymbols(rawSymbols.asJsonArray)) {
            val classRange = classSymbol.get("range")
                ?.takeIf(JsonElement::isJsonObject)
                ?.asJsonObject
                ?: continue

            val astResponse = request(
                method = "textDocument/ast",
                params = JsonObject().apply {
                    add(
                        "textDocument",
                        JsonObject().apply { addProperty("uri", uri.value) },
                    )
                    add("range", classRange.deepCopy())
                },
            )
            if (astResponse == null) {
                degraded = diagnostic(
                    code = "external-override-ast-failed",
                    message = "clangd AST request failed for a class in: $header",
                )
                continue
            }
            astResponse.protocolError()?.let { error ->
                degraded = diagnostic(
                    code = "external-override-ast-rejected",
                    message = "clangd rejected an AST request for: $header",
                    details = error,
                )
                continue
            }

            val ast = astResponse.get("result")
            if (ast == null || ast.isJsonNull || !ast.isJsonObject) {
                continue
            }

            val className = classSymbol.stringOrNull("name") ?: continue
            for (method in methodSymbols(classSymbol)) {
                val methodName = method.stringOrNull("name") ?: continue
                val methodRange = parseRange(method.get("selectionRange"))
                    ?: parseRange(method.get("range"))
                    ?: continue
                val methodPosition = methodRange.start
                val astMethod = findAstMethod(
                    root = ast.asJsonObject,
                    methodName = methodName,
                    position = methodPosition,
                ) ?: continue

                val overrideAttributes = overrideAttributes(astMethod)
                if (overrideAttributes.isEmpty()) {
                    continue
                }

                val externalBases = linkedSetOf<ExternalBase>()
                for (attribute in overrideAttributes) {
                    val attributeRange = parseRange(attribute.get("range")) ?: continue
                    val definitions = definitionLocations(
                        uri = uri,
                        position = attributeRange.start,
                    )
                    if (definitions == null) {
                        degraded = diagnostic(
                            code = "external-override-definition-failed",
                            message =
                                "clangd could not resolve an override base for " +
                                "$className::$methodName.",
                        )
                        continue
                    }

                    for (location in definitions) {
                        val realm = realmClassifier.classify(location.uri)
                        if (isSupportedExternalBaseRealm(realm)) {
                            externalBases += ExternalBase(
                                location = location,
                                realm = realm,
                            )
                        }
                    }
                }

                if (externalBases.isEmpty()) {
                    continue
                }

                val prepared = callHierarchy.prepareSymbol(
                    sourceFile = header,
                    position = methodPosition,
                )
                val symbol = when (prepared) {
                    is AnalyzerResult.Failure -> {
                        degraded = diagnostic(
                            code = "external-override-call-symbol-failed",
                            message =
                                "External override $className::$methodName was proven, " +
                                "but its call-hierarchy symbol could not be prepared.",
                            details = prepared.error.message,
                        )
                        continue
                    }

                    is AnalyzerResult.Success -> prepared.value
                }

                if (symbol.realm != SourceRealm.TARGET) {
                    degraded = diagnostic(
                        code = "external-override-non-target-symbol",
                        message =
                            "clangd prepared $className::$methodName outside the target realm " +
                            "(${symbol.realm}); the candidate was skipped.",
                    )
                    continue
                }

                val qualifiedName = "$className::$methodName"
                val accumulator = roots.getOrPut(symbol.id) {
                    RootAccumulator(
                        symbol = symbol,
                        label = qualifiedName,
                    )
                }
                externalBases.forEach { base ->
                    accumulator.addEvidence(
                        EvidenceRecord(
                            kind = EvidenceKind.FOREGROUND_OVERRIDE_VERIFICATION,
                            confidence = EvidenceConfidence.CONFIRMED,
                            summary =
                                "$qualifiedName semantically overrides a declaration " +
                                "outside the analysis target (${base.realm}).",
                            location = base.location,
                            relatedSymbols = listOf(symbol.id),
                        ),
                    )
                }
            }
        }

        return degraded
    }

    private fun discoverHeaders(): List<Path> =
        targetRoots
            .flatMap { root ->
                when {
                    Files.isRegularFile(root) && isHeader(root) -> listOf(root)
                    Files.isDirectory(root) -> {
                        Files.walk(root).use { stream ->
                            stream
                                .filter { path -> Files.isRegularFile(path) }
                                .filter { path -> isHeader(path) }
                                .map { path -> path.toAbsolutePath().normalize() }
                                .toList()
                        }
                    }

                    else -> emptyList()
                }
            }
            .distinct()
            .sortedBy { path -> path.toString().lowercase() }

    private fun isHeader(path: Path): Boolean =
        path.extension.lowercase() in setOf("h", "hh", "hpp", "hxx")

    private fun isSupportedExternalBaseRealm(realm: SourceRealm): Boolean =
        realm == SourceRealm.DEPENDENCY_MOD ||
            realm == SourceRealm.SML ||
            realm == SourceRealm.FACTORY_GAME ||
            realm == SourceRealm.UNREAL_ENGINE ||
            realm == SourceRealm.OTHER_EXTERNAL

    private fun classSymbols(symbols: JsonArray): List<JsonObject> {
        val result = mutableListOf<JsonObject>()

        fun walk(element: JsonElement) {
            if (!element.isJsonObject) {
                return
            }
            val symbol = element.asJsonObject
            val kind = symbol.intOrNull("kind")
            if (kind in setOf(5, 11, 23)) {
                result += symbol
            }
            symbol.get("children")
                ?.takeIf(JsonElement::isJsonArray)
                ?.asJsonArray
                ?.forEach(::walk)
        }

        symbols.forEach(::walk)
        return result
    }

    private fun methodSymbols(classSymbol: JsonObject): List<JsonObject> =
        classSymbol
            .get("children")
            ?.takeIf(JsonElement::isJsonArray)
            ?.asJsonArray
            ?.mapNotNull { element ->
                element
                    .takeIf(JsonElement::isJsonObject)
                    ?.asJsonObject
                    ?.takeIf { child -> child.intOrNull("kind") == 6 }
            }
            ?: emptyList()

    private fun findAstMethod(
        root: JsonObject,
        methodName: String,
        position: SourcePosition,
    ): JsonObject? {
        val candidates = mutableListOf<JsonObject>()

        fun walk(node: JsonObject) {
            if (
                node.stringOrNull("kind") == "CXXMethod" &&
                node.stringOrNull("detail") == methodName &&
                parseRange(node.get("range"))?.contains(position) == true
            ) {
                candidates += node
            }
            node.get("children")
                ?.takeIf(JsonElement::isJsonArray)
                ?.asJsonArray
                ?.forEach { child ->
                    if (child.isJsonObject) {
                        walk(child.asJsonObject)
                    }
                }
        }

        walk(root)
        return candidates.minWithOrNull(
            compareBy(
                { candidate -> parseRange(candidate.get("range"))?.lineSpan() ?: Int.MAX_VALUE },
                { candidate -> parseRange(candidate.get("range"))?.columnSpan() ?: Int.MAX_VALUE },
            ),
        )
    }

    private fun overrideAttributes(method: JsonObject): List<JsonObject> {
        val result = mutableListOf<JsonObject>()

        fun walk(node: JsonObject) {
            if (
                node.stringOrNull("role") == "attribute" &&
                node.stringOrNull("kind") in setOf("Override", "Final")
            ) {
                result += node
            }
            node.get("children")
                ?.takeIf(JsonElement::isJsonArray)
                ?.asJsonArray
                ?.forEach { child ->
                    if (child.isJsonObject) {
                        walk(child.asJsonObject)
                    }
                }
        }

        walk(method)
        return result
    }

    private fun definitionLocations(
        uri: SourceUri,
        position: SourcePosition,
    ): List<SourceLocation>? {
        val response = request(
            method = "textDocument/definition",
            params = JsonObject().apply {
                add(
                    "textDocument",
                    JsonObject().apply { addProperty("uri", uri.value) },
                )
                add("position", position.toJson())
            },
        ) ?: return null

        if (response.protocolError() != null) {
            return null
        }

        val raw = response.get("result") ?: return emptyList()
        if (raw.isJsonNull) {
            return emptyList()
        }
        val items =
            if (raw.isJsonArray) {
                raw.asJsonArray.toList()
            } else {
                listOf(raw)
            }

        return items.mapNotNull { item ->
            if (!item.isJsonObject) {
                return@mapNotNull null
            }
            val value = item.asJsonObject
            val uriValue: String
            val rangeElement: JsonElement?
            if (value.stringOrNull("targetUri") != null) {
                uriValue = value.stringOrNull("targetUri") ?: return@mapNotNull null
                rangeElement =
                    value.get("targetSelectionRange")
                        ?.takeIf(JsonElement::isJsonObject)
                        ?: value.get("targetRange")
            } else {
                uriValue = value.stringOrNull("uri") ?: return@mapNotNull null
                rangeElement = value.get("range")
            }

            val range = parseRange(rangeElement) ?: return@mapNotNull null
            val sourceUri = try {
                SourceUri(uriValue)
            } catch (_: IllegalArgumentException) {
                return@mapNotNull null
            }
            SourceLocation(uri = sourceUri, range = range)
        }.distinct()
    }

    private fun request(
        method: String,
        params: JsonObject,
    ): JsonObject? =
        try {
            session.request(method, params)
        } catch (_: Throwable) {
            null
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
        val line = value.intOrNull("line") ?: return null
        val column = value.intOrNull("character") ?: return null
        return try {
            SourcePosition(line = line, column = column)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun SourceRange.contains(position: SourcePosition): Boolean =
        !position.isBefore(start) && !end.isBefore(position)

    private fun SourcePosition.isBefore(other: SourcePosition): Boolean =
        line < other.line || (line == other.line && column < other.column)

    private fun SourceRange.lineSpan(): Int = end.line - start.line

    private fun SourceRange.columnSpan(): Int = end.column - start.column

    private fun SourcePosition.toJson(): JsonObject =
        JsonObject().apply {
            addProperty("line", line)
            addProperty("character", column)
        }

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString

    private fun JsonObject.intOrNull(name: String): Int? =
        get(name)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asInt

    private fun JsonObject.protocolError(): String? =
        get("error")
            ?.takeUnless(JsonElement::isJsonNull)
            ?.toString()

    private fun pathFromFileUri(sourceUri: SourceUri): Path? =
        try {
            val uri = URI(sourceUri.value)
            if (!uri.scheme.equals("file", ignoreCase = true)) {
                null
            } else {
                Path.of(uri).toAbsolutePath().normalize()
            }
        } catch (_: Exception) {
            null
        }

    private fun diagnostic(
        code: String,
        message: String,
        details: String? = null,
    ): AnalyzerDiagnostic =
        AnalyzerDiagnostic(
            code = code,
            severity = DiagnosticSeverity.WARNING,
            message =
                if (details.isNullOrBlank()) {
                    message
                } else {
                    "$message ($details)"
                },
        )

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
        closed.set(true)
    }

    private data class ExternalBase(
        val location: SourceLocation,
        val realm: SourceRealm,
    )

    private data class RootAccumulator(
        val symbol: SymbolDescriptor,
        val label: String,
        val evidence: MutableList<EvidenceRecord> = mutableListOf(),
    ) {
        fun addEvidence(record: EvidenceRecord) {
            if (record !in evidence) {
                evidence += record
            }
        }

        fun descriptor(): RootDescriptor =
            RootDescriptor(
                id = RootId("external-override:${symbol.id.value}"),
                symbol = symbol,
                kind = RootKind.EXTERNAL_OVERRIDE,
                priority = RootPriority.SECONDARY,
                evidence = evidence.toList(),
                label = label,
            )
    }
}
