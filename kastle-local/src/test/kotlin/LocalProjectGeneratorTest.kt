package org.jetbrains.kastle

import kotlinx.io.files.Path
import kotlin.random.Random

val LocalProjectGeneratorTest by
    ProjectGeneratorTest("Local") {
        LocalPackRepository(Path(TEST_TEMPLATES_ROOT), random = Random(42L))
    }
