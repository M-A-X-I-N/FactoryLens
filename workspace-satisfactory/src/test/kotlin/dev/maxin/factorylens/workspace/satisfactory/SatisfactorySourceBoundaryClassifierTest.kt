package dev.maxin.factorylens.workspace.satisfactory

import dev.maxin.factorylens.core.api.AnalysisTarget
import dev.maxin.factorylens.core.model.AnalysisTargetId
import dev.maxin.factorylens.core.model.SourceRealm
import dev.maxin.factorylens.core.model.SourceUri
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

public class SatisfactorySourceBoundaryClassifierTest {
    @Test
    public fun classifiesSupportedSatisfactorySourceRealms(): Unit {
        val fixture = fixture()
        val classifier = fixture.requiredClassifier

        assertEquals(
            SourceRealm.TARGET,
            classifier.classify(fixture.rssRoot.resolve("Private/RssApiClient.cpp")),
        )
        assertEquals(
            SourceRealm.TARGET,
            classifier.classify(fixture.extraTargetRoot.resolve("Private/Extra.cpp")),
        )
        assertEquals(
            SourceRealm.GENERATED,
            classifier.classify(
                fixture.rssMod.resolve(
                    "Intermediate/Build/Win64/x64/UnrealEditor/Development/RSS/Module.RSS.gen.cpp",
                ),
            ),
        )
        assertEquals(
            SourceRealm.DEPENDENCY_MOD,
            classifier.classify(
                fixture.workspace.modsRoot.resolve(
                    "FicsitWiremod/Source/FicsitWiremod/Private/FicsitWiremodModule.cpp",
                ),
            ),
        )
        assertEquals(
            SourceRealm.SML,
            classifier.classify(
                fixture.workspace.modsRoot.resolve("SML/Source/SML/Private/SMLModule.cpp"),
            ),
        )
        assertEquals(
            SourceRealm.FACTORY_GAME,
            classifier.classify(
                fixture.workspace.root.resolve("Source/FactoryGame/Private/FGCharacter.cpp"),
            ),
        )
        assertEquals(
            SourceRealm.FACTORY_GAME,
            classifier.classify(
                fixture.workspace.root.resolve(
                    "Source/FactoryDedicatedServer/Private/FactoryDSModule.cpp",
                ),
            ),
        )
        assertEquals(
            SourceRealm.UNREAL_ENGINE,
            classifier.classify(
                fixture.engine.root.resolve("Engine/Source/Runtime/Core/Public/CoreMinimal.h"),
            ),
        )
        assertEquals(
            SourceRealm.OTHER_EXTERNAL,
            classifier.classify(
                fixture.workspace.root.resolve(
                    "Plugins/Wwise/Source/AkAudio/Private/AkAudioDevice.cpp",
                ),
            ),
        )
        assertEquals(
            SourceRealm.OTHER_EXTERNAL,
            classifier.classify(
                fixture.workspace.root.parent.resolve("ExternalSdk/include/ExternalApi.h"),
            ),
        )
    }

    @Test
    public fun generatedClassificationWinsOverOwningRealm(): Unit {
        val fixture = fixture()
        val classifier = fixture.requiredClassifier

        assertEquals(
            SourceRealm.GENERATED,
            classifier.classify(
                fixture.rssRoot.resolve(
                    "Intermediate/Build/Win64/RSS/RssApiClient.generated.h",
                ),
            ),
        )
        assertEquals(
            SourceRealm.GENERATED,
            classifier.classify(
                fixture.engine.root.resolve(
                    "Engine/Intermediate/Build/Win64/UnrealEditor/Core/Module.Core.gen.cpp",
                ),
            ),
        )
        assertEquals(
            SourceRealm.GENERATED,
            classifier.classify(
                fixture.workspace.root.resolve(
                    "Plugins/SignificanceISPC/Source/SignificanceISPC.ispc.generated.h",
                ),
            ),
        )
    }

    @Test
    public fun classifiesFileUrisAndLeavesUnsupportedUrisUnknown(): Unit {
        val fixture = fixture()
        val classifier = fixture.requiredClassifier
        val targetFile = fixture.rssRoot.resolve("Public/RssApiClient.h")

        assertEquals(
            SourceRealm.TARGET,
            classifier.classify(SourceUri(targetFile.toUri().toString())),
        )
        assertEquals(
            SourceRealm.UNKNOWN,
            classifier.classify(SourceUri("untitled:FactoryLensScratch.cpp")),
        )
        assertEquals(
            SourceRealm.UNKNOWN,
            classifier.classify(SourceUri("not a uri")),
        )
    }

    @Test
    public fun resolvesRelativePathsAgainstWorkspaceRoot(): Unit {
        val fixture = fixture()

        assertEquals(
            SourceRealm.FACTORY_GAME,
            fixture.requiredClassifier.classify(
                Path.of("Source/FactoryGame/Private/FGGameMode.cpp"),
            ),
        )
    }

    @Test
    public fun classifiesCanonicalTargetPathsBehindSymlinksWhenSupported(): Unit {
        val fixture = fixture(createClassifier = false)
        val physicalRoot = fixture.workspace.root.parent
            .resolve("physical-rss")
            .createDirectories()
        val logicalRoot = fixture.workspace.modsRoot
            .resolve("GameFeatures/LinkedRSS")
        logicalRoot.parent.createDirectories()

        try {
            Files.createSymbolicLink(logicalRoot, physicalRoot)
        } catch (_: Exception) {
            // Windows CI may not grant symlink creation. The real Windows workspace exercises the
            // equivalent junction/canonical-path behavior through manual semantic validation.
            return
        }

        val sourceFile = physicalRoot
            .resolve("Private/Linked.cpp")
        sourceFile.parent.createDirectories()
        Files.writeString(sourceFile, "void Linked() {}\n")

        val target = AnalysisTarget(
            id = AnalysisTargetId("linked-rss"),
            displayName = "Linked RSS",
            sourceRoots = listOf(SourceUri(logicalRoot.toUri().toString())),
        )
        val classifier = SatisfactorySourceBoundaryClassifier(
            workspace = fixture.workspace,
            engine = fixture.engine,
            target = target,
        )

        assertEquals(
            SourceRealm.TARGET,
            classifier.classify(sourceFile),
        )
        assertEquals(
            SourceRealm.TARGET,
            classifier.classify(logicalRoot.resolve("Private/Linked.cpp")),
        )
    }

    @Test
    public fun rejectsNonFileTargetRoots(): Unit {
        val fixture = fixture(createClassifier = false)
        val invalidTarget = AnalysisTarget(
            id = AnalysisTargetId("invalid"),
            displayName = "Invalid",
            sourceRoots = listOf(SourceUri("untitled:NotAFileRoot")),
        )

        assertFailsWith<IllegalArgumentException> {
            SatisfactorySourceBoundaryClassifier(
                workspace = fixture.workspace,
                engine = fixture.engine,
                target = invalidTarget,
            )
        }
    }

    private fun fixture(createClassifier: Boolean = true): Fixture {
        val root = Files.createTempDirectory("factorylens-boundary")
        val workspaceRoot = root.resolve("SML").createDirectories()
        val modsRoot = workspaceRoot.resolve("Mods").createDirectories()
        val projectFile = workspaceRoot.resolve("FactoryGame.uproject")
        Files.writeString(projectFile, """{"EngineAssociation":"5.6.1-CSS"}""")
        val engineRoot = root.resolve("Unreal Engine - CSS").createDirectories()

        val workspace = SatisfactoryWorkspace(
            root = workspaceRoot,
            projectFile = projectFile,
            modsRoot = modsRoot,
            engineAssociation = "5.6.1-CSS",
        )
        val engine = UnrealEngineInstallation(
            root = engineRoot,
            association = "5.6.1-CSS",
            source = EngineResolutionSource.EXPLICIT,
        )
        val rssMod = modsRoot.resolve("GameFeatures/RSS").createDirectories()
        val rssRoot = rssMod.resolve("Source/RSS").createDirectories()
        val extraTargetRoot = rssMod.resolve("Source/RSSExtra").createDirectories()
        val target = AnalysisTarget(
            id = AnalysisTargetId("rss"),
            displayName = "RSS2",
            sourceRoots = listOf(
                SourceUri(rssRoot.toUri().toString()),
                SourceUri(extraTargetRoot.toUri().toString()),
            ),
        )

        return Fixture(
            workspace = workspace,
            engine = engine,
            rssMod = rssMod,
            rssRoot = rssRoot,
            extraTargetRoot = extraTargetRoot,
            classifier = if (createClassifier) {
                SatisfactorySourceBoundaryClassifier(workspace, engine, target)
            } else {
                null
            },
        )
    }

    private data class Fixture(
        val workspace: SatisfactoryWorkspace,
        val engine: UnrealEngineInstallation,
        val rssMod: Path,
        val rssRoot: Path,
        val extraTargetRoot: Path,
        val classifier: SatisfactorySourceBoundaryClassifier?,
    ) {
        val requiredClassifier: SatisfactorySourceBoundaryClassifier
            get() = requireNotNull(classifier)
    }
}
