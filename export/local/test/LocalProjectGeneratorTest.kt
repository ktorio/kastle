package org.jetbrains.kastle

import kotlinx.io.files.Path

class LocalProjectGeneratorTest: ProjectGeneratorTest() {
    override fun createRepository(): FeatureRepository =
        LocalFeatureRepository(Path(resources, "features"))
}