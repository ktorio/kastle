package org.jetbrains.kastle

import io.kotest.core.spec.style.StringSpec
import kotlinx.io.files.Path
import kotlin.random.Random

class LocalPackRepositoryTest: StringSpec(
    PackRepositoryTest(LocalPackRepository(Path("../repository"), random = Random(42L)))
)