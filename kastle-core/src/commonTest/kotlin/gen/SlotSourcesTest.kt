package org.jetbrains.kastle.gen

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe
import org.jetbrains.kastle.CatalogArtifact
import org.jetbrains.kastle.GradleProjectSettings
import org.jetbrains.kastle.PackagingStyle
import org.jetbrains.kastle.Platform
import org.jetbrains.kastle.ProjectDescriptor
import org.jetbrains.kastle.ProjectModules
import org.jetbrains.kastle.SourceFile
import org.jetbrains.kastle.SourceModule
import org.jetbrains.kastle.SourceModuleManifest
import org.jetbrains.kastle.SourceTemplate
import org.jetbrains.kastle.SourcesByUrl
import org.jetbrains.kastle.utils.StringLiteral

private const val README_SLOT = "slot://build.etc/readme/instructions"
private const val OTHER_SLOT = "slot://org.gradle/gradle/buildRoot"

private fun slotSource(target: String, text: String): SourceFile =
    SourceTemplate(target = StringLiteral(target), text = text)

private fun projectWith(slotSources: SourcesByUrl, module: SourceModule): Project =
    Project(
        descriptor = ProjectDescriptor(name = "test", group = "test.group"),
        packs = emptyList(),
        properties = emptyMap(),
        slotSources = slotSources,
        moduleSources = ProjectModules.fromList(listOf(module)),
        commonSources = emptyList(),
        versions = emptyMap(),
        libraries = emptyMap<String, CatalogArtifact>(),
        gradle = GradleProjectSettings(),
        packaging = PackagingStyle.FLAT,
    )

private fun SourcesByUrl.texts(): Map<String, List<String>> =
    mapValues { (_, sources) -> sources.map { (it as SourceTemplate).text } }

val SlotSourcesTest by testSuite("SlotSources") {

    test("module slot contributions do not replace project contributions for the same slot") {
        val module = SourceModule(
            manifest = SourceModuleManifest(platforms = setOf(Platform.JVM)),
            sources = listOf(
                slotSource(README_SLOT, "sbom"),
                slotSource(OTHER_SLOT, "gradle"),
            ),
        )
        val project = projectWith(
            slotSources = mapOf(
                README_SLOT to listOf(slotSource(README_SLOT, "maven"), slotSource(README_SLOT, "database")),
            ),
            module = module,
        )

        project.slotSourcesFor(module).texts() shouldBe mapOf(
            README_SLOT to listOf("maven", "database", "sbom"),
            OTHER_SLOT to listOf("gradle"),
        )
    }

    test("merging with an empty map keeps the original contributions") {
        val sources = mapOf(README_SLOT to listOf(slotSource(README_SLOT, "maven")))

        (sources mergedWith emptyMap()).texts() shouldBe mapOf(README_SLOT to listOf("maven"))
        (emptyMap<String, List<SourceFile>>() mergedWith sources).texts() shouldBe mapOf(README_SLOT to listOf("maven"))
    }
}
