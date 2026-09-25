package dev.maxin.factorylens.semantic.clangd

import com.google.gson.Gson
import dev.maxin.factorylens.core.api.AnalyzerResult
import dev.maxin.factorylens.core.model.AnalysisTargetId
import dev.maxin.factorylens.core.model.CallEdgeScope
import dev.maxin.factorylens.core.model.EvidenceConfidence
import dev.maxin.factorylens.core.model.EvidenceKind
import dev.maxin.factorylens.core.model.ResultCompleteness
import dev.maxin.factorylens.core.model.SourcePosition
import dev.maxin.factorylens.core.model.SourceRealm
import dev.maxin.factorylens.core.model.SourceRealmClassifier
import dev.maxin.factorylens.core.model.SourceUri
import dev.maxin.factorylens.core.model.SymbolId
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

public class ClangdCallHierarchyAdapterTest {
    @Test
    public fun preparesStableSymbolAndExpandsTargetAndBoundaryCalls(): Unit {
        val fixture = createFixture()
        val start = ClangdBackendSessionFactory.start(fixture.config)
        val session = assertIs<ClangdBackendStartResult.Success>(start).session
        val classifier = SourceRealmClassifier { uri ->
            when (uri) {
                SourceUri(fixture.rootFile.toUri().toString()),
                SourceUri(fixture.targetCallee.toUri().toString()),
                    -> SourceRealm.TARGET
                SourceUri(fixture.boundaryCallee.toUri().toString()) ->
                    SourceRealm.UNREAL_ENGINE
                else -> SourceRealm.OTHER_EXTERNAL
            }
        }
        val adapter = ClangdCallHierarchyAdapter(
            session = session,
            target = AnalysisTargetId("rss"),
            realmClassifier = classifier,
        )

        try {
            val firstPrepared = assertIs<AnalyzerResult.Success<*>>(
                adapter.prepareSymbol(
                    fixture.rootFile,
                    SourcePosition(line = 2, column = 6),
                ),
            ).value
            val secondPrepared = assertIs<AnalyzerResult.Success<*>>(
                adapter.prepareSymbol(
                    fixture.rootFile,
                    SourcePosition(line = 2, column = 6),
                ),
            ).value

            val firstSymbol = firstPrepared as dev.maxin.factorylens.core.model.SymbolDescriptor
            val secondSymbol = secondPrepared as dev.maxin.factorylens.core.model.SymbolDescriptor
            assertEquals(firstSymbol.id, secondSymbol.id)
            assertEquals(SourceRealm.TARGET, firstSymbol.realm)
            assertNull(firstSymbol.navigation.preferred())

            val expansion = assertIs<AnalyzerResult.Success<*>>(
                adapter.expandOutgoingCalls(firstSymbol.id),
            ).value as dev.maxin.factorylens.core.model.CallExpansion

            assertEquals(AnalysisTargetId("rss"), expansion.target)
            assertEquals(firstSymbol.id, expansion.origin)
            assertEquals(ResultCompleteness.PARTIAL, expansion.completeness)
            assertEquals(2, expansion.nodes.size)
            assertEquals(2, expansion.edges.size)
            assertTrue(
                expansion.diagnostics.any { it.code == "clangd-call-hierarchy-partial" },
            )

            val localNode = expansion.nodes.single { it.symbol.displayName == "LocalCallee" }
            val boundaryNode = expansion.nodes.single { it.symbol.displayName == "EngineBoundary" }
            assertEquals(SourceRealm.TARGET, localNode.symbol.realm)
            assertEquals(SourceRealm.UNREAL_ENGINE, boundaryNode.symbol.realm)
            assertNotEquals(localNode.symbol.id, boundaryNode.symbol.id)

            val localEdge = expansion.edges.single { it.callee == localNode.symbol.id }
            val boundaryEdge = expansion.edges.single { it.callee == boundaryNode.symbol.id }
            assertEquals(CallEdgeScope.TARGET_LOCAL, localEdge.scope)
            assertEquals(CallEdgeScope.BOUNDARY, boundaryEdge.scope)
            assertEquals(2, localEdge.callSites.size)
            assertEquals(1, boundaryEdge.callSites.size)
            assertEquals(
                SourceUri(fixture.rootFile.toUri().toString()),
                localEdge.callSites.first().uri,
            )
            assertEquals(EvidenceKind.SEMANTIC_CALL, localEdge.evidence.single().kind)
            assertEquals(
                EvidenceConfidence.CONFIRMED,
                localEdge.evidence.single().confidence,
            )
            assertEquals(localNode.symbol, adapter.symbol(localNode.symbol.id))
        } finally {
            adapter.close()
            session.close()
        }
    }

    @Test
    public fun rejectsUnknownOriginWithoutSendingRawIdentityToClangd(): Unit {
        val fixture = createFixture()
        val start = ClangdBackendSessionFactory.start(fixture.config)
        val session = assertIs<ClangdBackendStartResult.Success>(start).session
        val adapter = ClangdCallHierarchyAdapter(
            session = session,
            target = AnalysisTargetId("rss"),
            realmClassifier = SourceRealmClassifier { SourceRealm.TARGET },
        )

        try {
            val result = assertIs<AnalyzerResult.Failure>(
                adapter.expandOutgoingCalls(SymbolId("not-prepared")),
            )
            assertEquals(
                dev.maxin.factorylens.core.model.AnalyzerErrorCode.QUERY_FAILED,
                result.error.code,
            )
            assertTrue(result.error.recoverable)
        } finally {
            adapter.close()
            session.close()
        }
    }

    private fun createFixture(): Fixture {
        val root = Files.createTempDirectory("factorylens-call-hierarchy")
        val workspace = root.resolve("workspace").createDirectories()
        val targetDir = workspace.resolve("Mods/RSS/Source/RSS/Private").createDirectories()
        val engineDir = root.resolve("Engine/Source/Runtime/Core/Private").createDirectories()
        val rootFile = targetDir.resolve("Root.cpp")
        val targetCallee = targetDir.resolve("Local.cpp")
        val boundaryCallee = engineDir.resolve("Boundary.cpp")
        rootFile.writeText(
            listOf(
                "// line 0",
                "// line 1",
                "    RootMethod();",
                "    LocalCallee();",
                "// line 4",
                "    EngineBoundary();",
            ).joinToString("\n"),
        )
        targetCallee.writeText("void LocalCallee() {}\n")
        boundaryCallee.writeText("void EngineBoundary() {}\n")

        val compileDatabase = root.resolve("compile-db").createDirectories()
        compileDatabase.resolve("compile_commands.json").writeText("[]")
        val shutdownMarker = root.resolve("shutdown-marker.txt")

        val launcherArguments = listOf(
            "-cp",
            fakeClangdClasspath(),
            FakeClangdMain::class.java.name,
            "--fake-version=20.1.8",
            "--marker=$shutdownMarker",
            "--target-callee-uri=${targetCallee.toUri()}",
            "--boundary-callee-uri=${boundaryCallee.toUri()}",
        )

        return Fixture(
            config = ClangdBackendConfig(
                executable = javaExecutable(),
                compileCommandsDirectory = compileDatabase,
                workspaceRoot = workspace,
                stderrLog = root.resolve("clangd-stderr.log"),
                launcherArguments = launcherArguments,
            ),
            rootFile = rootFile,
            targetCallee = targetCallee,
            boundaryCallee = boundaryCallee,
        )
    }

    private fun fakeClangdClasspath(): String {
        val explicitLocations = listOf(
            FakeClangdMain::class.java,
            Gson::class.java,
            kotlin.Unit::class.java,
        ).mapNotNull { type ->
            type.protectionDomain?.codeSource?.location?.toURI()?.let { uri ->
                Path.of(uri).toString()
            }
        }
        val inheritedClasspath = System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .filter(String::isNotBlank)
        return (explicitLocations + inheritedClasspath)
            .distinct()
            .joinToString(File.pathSeparator)
    }

    private fun javaExecutable(): Path {
        val executable = if (System.getProperty("os.name").startsWith("Windows")) {
            "java.exe"
        } else {
            "java"
        }
        return Path.of(System.getProperty("java.home"), "bin", executable)
    }

    private data class Fixture(
        val config: ClangdBackendConfig,
        val rootFile: Path,
        val targetCallee: Path,
        val boundaryCallee: Path,
    )
}
