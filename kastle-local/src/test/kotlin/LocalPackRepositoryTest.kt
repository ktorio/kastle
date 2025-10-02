package org.jetbrains.kastle

import io.kotest.core.spec.style.StringSpec
import kotlinx.io.files.Path

class LocalPackRepositoryTest: StringSpec(
    PackRepositoryTest(LocalPackRepository(Path("../repository")))
)