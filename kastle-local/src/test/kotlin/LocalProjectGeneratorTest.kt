package org.jetbrains.kastle

import io.kotest.core.spec.style.StringSpec
import kotlinx.io.files.Path

class LocalProjectGeneratorTest: StringSpec(
    ProjectGeneratorTest {
        LocalPackRepository(Path("../repository"))
    }
)