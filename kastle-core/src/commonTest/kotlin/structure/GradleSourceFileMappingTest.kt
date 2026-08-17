package org.jetbrains.kastle.structure

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe
import kotlinx.io.bytestring.ByteString
import org.jetbrains.kastle.CatalogArtifact
import org.jetbrains.kastle.GradleProjectSettings
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.PackManifest
import org.jetbrains.kastle.PackSources
import org.jetbrains.kastle.PackagingStyle
import org.jetbrains.kastle.Platform
import org.jetbrains.kastle.ProjectDescriptor
import org.jetbrains.kastle.ProjectModules
import org.jetbrains.kastle.SourceModule
import org.jetbrains.kastle.SourceModuleManifest
import org.jetbrains.kastle.SourceTemplate
import org.jetbrains.kastle.StaticSource
import org.jetbrains.kastle.gen.Project
import org.jetbrains.kastle.utils.StringLiteral

private fun projectWithSources(
    targets: List<String>,
    platforms: Set<Platform> = setOf(Platform.JVM),
    packId: PackId = BuildToolModules.GRADLE_PACK_ID,
): Project {
    val module = SourceModule(
        manifest = SourceModuleManifest(platforms = platforms),
        sources = targets.map { SourceTemplate(target = StringLiteral(it), text = "") },
    )
    return Project(
        descriptor = ProjectDescriptor(name = "test", group = "test.group"),
        packs = listOf(
            PackDescriptor(
                manifest = PackManifest(id = packId, name = packId.id),
                propertyValues = emptyMap(),
                sources = PackSources(),
            ),
        ),
        properties = emptyMap(),
        slotSources = emptyMap(),
        moduleSources = ProjectModules.fromList(listOf(module)),
        commonSources = emptyList(),
        versions = emptyMap(),
        libraries = emptyMap<String, CatalogArtifact>(),
        gradle = GradleProjectSettings(),
        packaging = PackagingStyle.FLAT,
    )
}

private fun mappedTargets(
    relativeTargets: List<String>,
    platforms: Set<Platform> = setOf(Platform.JVM),
    packId: PackId = BuildToolModules.GRADLE_PACK_ID,
): List<String> {
    val project = projectWithSources(relativeTargets.map { "file:$it" }, platforms, packId)
    return GradleSourceMapping(project).moduleSources.modules.single().sources.map { it.target.toString() }
}

private fun mappedTarget(
    relativeTarget: String,
    platforms: Set<Platform> = setOf(Platform.JVM),
): String = mappedTargets(listOf(relativeTarget), platforms).single()

val GradleSourceFileMappingTest by testSuite("GradleSourceMapping") {

    test("maps a top-level src folder to the JVM main source set") {
        mappedTarget("src/Foo.kt", platforms = setOf(Platform.JVM)) shouldBe "file:src/main/kotlin/Foo.kt"
    }

    test("maps a top-level src folder to the Android main source set") {
        mappedTarget("src/Foo.kt", platforms = setOf(Platform.ANDROID)) shouldBe "file:src/main/kotlin/Foo.kt"
    }

    test("maps a top-level src folder to a platform-named main source set") {
        mappedTarget("src/Foo.kt", platforms = setOf(Platform.IOS)) shouldBe "file:src/iosMain/kotlin/Foo.kt"
    }

    test("maps a top-level src folder to commonMain when there isn't a single platform") {
        mappedTarget("src/Foo.kt", platforms = emptySet()) shouldBe "file:src/commonMain/kotlin/Foo.kt"
        mappedTarget("src/Foo.kt", platforms = setOf(Platform.JVM, Platform.IOS)) shouldBe "file:src/commonMain/kotlin/Foo.kt"
    }

    test("maps a top-level test folder to the corresponding test source set") {
        mappedTarget("test/FooTest.kt", platforms = setOf(Platform.JVM)) shouldBe "file:src/test/kotlin/FooTest.kt"
        mappedTarget("test/FooTest.kt", platforms = setOf(Platform.IOS)) shouldBe "file:src/iosTest/kotlin/FooTest.kt"
    }

    test("an explicit @platform suffix overrides the module's platforms") {
        mappedTarget("src@ios/Foo.kt", platforms = setOf(Platform.JVM)) shouldBe "file:src/iosMain/kotlin/Foo.kt"
        mappedTarget("test@ios/FooTest.kt", platforms = setOf(Platform.JVM)) shouldBe "file:src/iosTest/kotlin/FooTest.kt"
    }

    test("maps resources and testResources folders") {
        mappedTarget("resources/application.conf") shouldBe "file:src/main/resources/application.conf"
        mappedTarget("testResources/application.conf") shouldBe "file:src/test/resources/application.conf"
    }

    test("keeps res and composeResources as their own top-level category") {
        mappedTarget("res/values/strings.xml", platforms = setOf(Platform.ANDROID)) shouldBe "file:src/main/res/values/strings.xml"
        mappedTarget("composeResources/drawable/icon.xml") shouldBe "file:src/main/composeResources/drawable/icon.xml"
    }

    test("categorizes .proto files under a proto folder instead of kotlin") {
        mappedTarget("src/foo.proto") shouldBe "file:src/main/proto/foo.proto"
    }

    test("leaves AndroidManifest.xml without a file category") {
        mappedTarget("src/AndroidManifest.xml", platforms = setOf(Platform.ANDROID)) shouldBe "file:src/main/AndroidManifest.xml"
    }

    test("only remaps the leading path segment, leaving inner src-like folders untouched") {
        mappedTarget("src/main/src/Foo.kt") shouldBe "file:src/main/kotlin/main/src/Foo.kt"
    }

    test("ignores src, test, and resources folders that appear deeper in the path") {
        val targets = listOf(
            "main/src/Foo.kt",
            "some/nested/test/Bar.kt",
            "config/resources/app.conf",
            "a/b/res/values/strings.xml",
            "a/composeResources/drawable/icon.xml",
        )

        mappedTargets(targets) shouldBe targets.map { "file:$it" }
    }

    test("also remaps sources when only a Maven pack is present") {
        mappedTargets(listOf("src/Foo.kt"), packId = BuildToolModules.MAVEN_PACK_ID) shouldBe listOf("file:src/main/kotlin/Foo.kt")
    }

    test("does nothing when neither a Gradle nor a Maven pack is present") {
        val project = projectWithSources(listOf("file:src/Foo.kt"), packId = PackId("com.acme", "other"))

        GradleSourceMapping(project) shouldBe project
    }

    test("does not touch non-file targets, such as slots") {
        val project = projectWithSources(listOf("slot:some/src/thing"))

        val result = GradleSourceMapping(project)
        result.moduleSources.modules.single().sources.single().target.toString() shouldBe "slot:some/src/thing"
    }

    test("remaps static sources as well as templated ones") {
        val module = SourceModule(
            manifest = SourceModuleManifest(platforms = setOf(Platform.JVM)),
            sources = listOf(StaticSource(target = StringLiteral("file:resources/logo.png"), contents = ByteString())),
        )
        val project = Project(
            descriptor = ProjectDescriptor(name = "test", group = "test.group"),
            packs = listOf(
                PackDescriptor(
                    manifest = PackManifest(id = BuildToolModules.GRADLE_PACK_ID, name = "gradle"),
                    propertyValues = emptyMap(),
                    sources = PackSources(),
                ),
            ),
            properties = emptyMap(),
            slotSources = emptyMap(),
            moduleSources = ProjectModules.fromList(listOf(module)),
            commonSources = emptyList(),
            versions = emptyMap(),
            libraries = emptyMap<String, CatalogArtifact>(),
            gradle = GradleProjectSettings(),
            packaging = PackagingStyle.FLAT,
        )

        val result = GradleSourceMapping(project)
        result.moduleSources.modules.single().sources.single().target.toString() shouldBe "file:src/main/resources/logo.png"
    }
}
