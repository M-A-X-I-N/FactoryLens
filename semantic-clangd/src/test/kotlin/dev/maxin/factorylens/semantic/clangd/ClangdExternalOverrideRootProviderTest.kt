package dev.maxin.factorylens.semantic.clangd

import com.google.gson.Gson
import dev.maxin.factorylens.core.api.AnalysisTarget
import dev.maxin.factorylens.core.api.AnalyzerResult
import dev.maxin.factorylens.core.api.ProgressReporter
import dev.maxin.factorylens.core.model.AnalyzerProgress
import dev.maxin.factorylens.core.model.AnalysisTargetId
import dev.maxin.factorylens.core.model.EvidenceConfidence
import dev.maxin.factorylens.core.model.EvidenceKind
import dev.maxin.factorylens.core.model.ResultCompleteness
import dev.maxin.factorylens.core.model.RootKind
import dev.maxin.factorylens.core.model.RootPriority
import dev.maxin.factorylens.core.model.SourceRealm
import dev.maxin.factorylens.core.model.SourceRealmClassifier
import dev.maxin.factorylens.core.model.SourceUri
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

public class ClangdExternalOverrideRootProviderTest {
    @Test
    public fun discoversForegroundOverrideWithExternalBaseProvenance(): Unit {
        val root = Files.createTempDirectory("factorylens-external-override")
        val workspace = root.resolve("workspace").createDirectories()
        val targetRoot = workspace.resolve("Mods/RSS/Source/RSS").createDirectories()
        val header = targetRoot.resolve("Public/ProjectClass.h")
        header.parent.createDirectories()
        header.writeText(
            listOf(
                "class ProjectClass {",
                "public:",
                "    void Tick() override;",
                "",
                "",
                "    void CheckCopy();",
                "};",
            ).joinToString("\n"),
        )
        val baseHeader = root.resolve("Engine/Source/BaseClass.h")
        baseHeader.parent.createDirectories()
        baseHeader.writeText("class BaseClass { virtual void Tick(); };\n")

        val compileDatabase = root.resolve("compile-db").createDirectories()
        compileDatabase.resolve("compile_commands.json").writeText("[]")
        val shutdownMarker = root.resolve("shutdown-marker.txt")
        val config = ClangdBackendConfig(
            executable = javaExecutable(),
            compileCommandsDirectory = compileDatabase,
            workspaceRoot = workspace,
            stderrLog = root.resolve("clangd-stderr.log"),
            launcherArguments = listOf(
                "-cp",
                fakeClangdClasspath(),
                FakeClangdMain::class.java.name,
                "--fake-version=20.1.8",
                "--marker=${shutdownMarker}",
                "--override-header-uri=${header.toUri()}",
                "--override-base-uri=${baseHeader.toUri()}",
            ),
        )
        val session = assertIs<ClangdBackendStartResult.Success>(
            ClangdBackendSessionFactory.start(config),
        ).session

        val realmClassifier = SourceRealmClassifier { uri ->
            when (uri) {
                SourceUri(header.toUri().toString()) -> SourceRealm.TARGET
                SourceUri(baseHeader.toUri().toString()) -> SourceRealm.UNREAL_ENGINE
                else -> SourceRealm.OTHER_EXTERNAL
            }
        }
        val target = AnalysisTarget(
            id = AnalysisTargetId("rss"),
            displayName = "RSS",
            sourceRoots = listOf(SourceUri(targetRoot.toUri().toString())),
        )
        val callHierarchy = ClangdCallHierarchyAdapter(
            session = session,
            target = target.id,
            realmClassifier = realmClassifier,
        )
        val provider = ClangdExternalOverrideRootProvider(
            session = session,
            analysisTarget = target,
            realmClassifier = realmClassifier,
            callHierarchy = callHierarchy,
        )
        val progress = mutableListOf<AnalyzerProgress>()

        try {
            val discovery = assertIs<AnalyzerResult.Success<*>>(
                provider.discoverRoots(ProgressReporter(progress::add)),
            ).value as dev.maxin.factorylens.core.model.RootDiscovery

            assertEquals(ResultCompleteness.COMPLETE, discovery.completeness)
            assertEquals(1, discovery.roots.size)
            val rootDescriptor = discovery.roots.single()

            assertEquals(RootKind.EXTERNAL_OVERRIDE, rootDescriptor.kind)
            assertEquals(RootPriority.SECONDARY, rootDescriptor.priority)
            assertEquals("ProjectClass::Tick", rootDescriptor.label)
            assertEquals("Tick", rootDescriptor.symbol.displayName)
            assertEquals(null, rootDescriptor.symbol.qualifiedName)
            assertEquals(SourceRealm.TARGET, rootDescriptor.symbol.realm)
            assertTrue(rootDescriptor.id.value.startsWith("external-override:"))
            assertEquals(1, rootDescriptor.evidence.size)

            val evidence = rootDescriptor.evidence.single()
            assertEquals(EvidenceKind.FOREGROUND_OVERRIDE_VERIFICATION, evidence.kind)
            assertEquals(EvidenceConfidence.CONFIRMED, evidence.confidence)
            assertEquals(
                SourceUri(baseHeader.toUri().toString()),
                assertNotNull(evidence.location).uri,
            )
            assertTrue(evidence.summary.contains("UNREAL_ENGINE"))
            assertEquals(
                rootDescriptor.symbol,
                callHierarchy.symbol(rootDescriptor.symbol.id),
            )

            assertTrue(
                discovery.diagnostics.any {
                    it.code == "external-override-foreground-ast"
                },
            )
            assertEquals(0L, progress.first().completedUnits)
            assertEquals(1L, progress.last().completedUnits)

            val expansion = callHierarchy.expandOutgoingCalls(rootDescriptor.symbol.id)
            assertIs<AnalyzerResult.Success<*>>(expansion)
        } finally {
            provider.close()
            callHierarchy.close()
            session.close()
        }

        assertEquals("clean", Files.readString(shutdownMarker))
    }

    @Test
    public fun rejectsGeneratedOverrideBasesFromGenericExternalRoots(): Unit {
        val root = Files.createTempDirectory("factorylens-generated-override")
        val workspace = root.resolve("workspace").createDirectories()
        val targetRoot = workspace.resolve("Mods/RSS/Source/RSS").createDirectories()
        val header = targetRoot.resolve("Public/ProjectClass.h")
        header.parent.createDirectories()
        header.writeText(
            listOf(
                "class ProjectClass {",
                "public:",
                "    void Tick() override;",
                "};",
            ).joinToString("\n"),
        )
        val generatedBase = root.resolve("Intermediate/Build/BaseClass.generated.h")
        generatedBase.parent.createDirectories()
        generatedBase.writeText("class BaseClass { virtual void Tick(); };\n")

        val compileDatabase = root.resolve("compile-db").createDirectories()
        compileDatabase.resolve("compile_commands.json").writeText("[]")
        val config = ClangdBackendConfig(
            executable = javaExecutable(),
            compileCommandsDirectory = compileDatabase,
            workspaceRoot = workspace,
            stderrLog = root.resolve("clangd-stderr.log"),
            launcherArguments = listOf(
                "-cp",
                fakeClangdClasspath(),
                FakeClangdMain::class.java.name,
                "--fake-version=20.1.8",
                "--override-header-uri=${header.toUri()}",
                "--override-base-uri=${generatedBase.toUri()}",
            ),
        )
        val session = assertIs<ClangdBackendStartResult.Success>(
            ClangdBackendSessionFactory.start(config),
        ).session
        val target = AnalysisTarget(
            id = AnalysisTargetId("rss"),
            displayName = "RSS",
            sourceRoots = listOf(SourceUri(targetRoot.toUri().toString())),
        )
        val classifier = SourceRealmClassifier { uri ->
            when (uri) {
                SourceUri(header.toUri().toString()) -> SourceRealm.TARGET
                SourceUri(generatedBase.toUri().toString()) -> SourceRealm.GENERATED
                else -> SourceRealm.OTHER_EXTERNAL
            }
        }
        val callHierarchy = ClangdCallHierarchyAdapter(
            session = session,
            target = target.id,
            realmClassifier = classifier,
        )
        val provider = ClangdExternalOverrideRootProvider(
            session = session,
            analysisTarget = target,
            realmClassifier = classifier,
            callHierarchy = callHierarchy,
        )

        try {
            val discovery = assertIs<AnalyzerResult.Success<*>>(
                provider.discoverRoots(),
            ).value as dev.maxin.factorylens.core.model.RootDiscovery

            assertTrue(discovery.roots.isEmpty())
            assertEquals(ResultCompleteness.COMPLETE, discovery.completeness)
        } finally {
            provider.close()
            callHierarchy.close()
            session.close()
        }
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
        val executable =
            if (System.getProperty("os.name").startsWith("Windows")) {
                "java.exe"
            } else {
                "java"
            }
        return Path.of(System.getProperty("java.home"), "bin", executable)
    }
}
