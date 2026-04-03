package org.jetbrains.kastle

import io.kotest.core.spec.style.StringSpec
import kotlinx.io.files.Path
import kotlin.random.Random

class LocalIntellijProjectGeneratorTest : StringSpec(
    IntellijProjectGeneratorTest {
        LocalPackRepository(Path("../repository-intellij"), random = Random(42L))
    }
)
