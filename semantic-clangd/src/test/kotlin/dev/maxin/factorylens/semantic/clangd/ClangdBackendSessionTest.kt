package dev.maxin.factorylens.semantic.clangd

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

public class ClangdBackendSessionTest {
    @Test
    public fun startsInitializesReusesOneProcessAndShutsDownCleanly(): Unit {
        val fixture = createFixture()
        val result = ClangdBackendSessionFactory.start(fixture.config)
        val session = assertIs<ClangdBackendStartResult.Success>(result).session

        try {
            val initialState = session.state()
            assertEquals(ClangdBackendStatus.READY, initialState.status)
            assertEquals(20, session.version.major)
            assertEquals(true, initialState.capabilities?.callHierarchyProvider)

            val processId = session.processId
            val first = session.request("factorylens/testPing", JsonObject())
            val second = session.request("factorylens/testPing", JsonObject())

            assertEquals(1, first.getAsJsonObject("result").get("sequence").asInt)
            assertEquals(2, second.getAsJsonObject("result").get("sequence").asInt)
            assertEquals(processId, session.processId)

            waitForIndexCompletion(session)
            assertEquals(
                ClangdBackgroundIndexStatus.COMPLETE,
                session.state().backgroundIndex,
            )
        } finally {
            session.close()
        }

        assertEquals(ClangdBackendStatus.STOPPED, session.state().status)
        assertEquals("clean", Files.readString(fixture.shutdownMarker))
    }

    @Test
    public fun rejectsKnownIncompatibleMajorBeforeStartingSession(): Unit {
        val fixture = createFixture(version = "22.1.8")

        val result = ClangdBackendSessionFactory.start(fixture.config)
        val failure = assertIs<ClangdBackendStartResult.Failure>(result).error

        assertEquals(
            ClangdBackendFailureCode.INCOMPATIBLE_VERSION,
            failure.code,
        )
        assertTrue(failure.message.contains("requires clangd major 20"))
        assertTrue(!Files.exists(fixture.shutdownMarker))
    }

    @Test
    public fun rejectsBackendWithoutRequiredCallHierarchyCapability(): Unit {
        val fixture = createFixture(advertiseCallHierarchy = false)

        val result = ClangdBackendSessionFactory.start(fixture.config)
        val failure = assertIs<ClangdBackendStartResult.Failure>(result).error

        assertEquals(
            ClangdBackendFailureCode.INCOMPATIBLE_CAPABILITIES,
            failure.code,
        )
        assertTrue(failure.message.contains("call-hierarchy support"))
    }

    @Test
    public fun rejectsMissingCompileDatabaseBeforeStartingBackend(): Unit {
        val fixture = createFixture()
        Files.delete(fixture.config.compileCommandsDirectory.resolve("compile_commands.json"))

        val result = ClangdBackendSessionFactory.start(fixture.config)
        val failure = assertIs<ClangdBackendStartResult.Failure>(result).error

        assertEquals(
            ClangdBackendFailureCode.COMPILE_DATABASE_NOT_FOUND,
            failure.code,
        )
    }

    private fun waitForIndexCompletion(session: ClangdBackendSession) {
        repeat(100) {
            if (session.state().backgroundIndex == ClangdBackgroundIndexStatus.COMPLETE) {
                return
            }
            Thread.sleep(10)
        }
    }

    private fun createFixture(
        version: String = "20.1.8",
        advertiseCallHierarchy: Boolean = true,
    ): Fixture {
        val root = Files.createTempDirectory("factorylens-clangd-session")
        val workspace = root.resolve("workspace").createDirectories()
        val compileDatabase = root.resolve("compile-db").createDirectories()
        compileDatabase.resolve("compile_commands.json").writeText("[]")
        val shutdownMarker = root.resolve("shutdown-marker.txt")

        val launcherArguments = buildList {
            add("-cp")
            add(fakeClangdClasspath())
            add(FakeClangdMain::class.java.name)
            add("--fake-version=$version")
            add("--marker=$shutdownMarker")
            if (!advertiseCallHierarchy) {
                add("--no-call-hierarchy")
            }
        }

        return Fixture(
            config = ClangdBackendConfig(
                executable = javaExecutable(),
                compileCommandsDirectory = compileDatabase,
                workspaceRoot = workspace,
                stderrLog = root.resolve("clangd-stderr.log"),
                launcherArguments = launcherArguments,
            ),
            shutdownMarker = shutdownMarker,
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
        val shutdownMarker: Path,
    )
}
