package org.jetbrains.kastle

import io.kotest.core.spec.style.StringSpec
import kotlinx.io.files.Path
import kotlin.random.Random

class LocalPackRepositoryTest: StringSpec(
    PackRepositoryTest(LocalPackRepository(Path(TEST_TEMPLATES_ROOT), random = Random(42L)))
)
