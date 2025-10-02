package org.jetbrains.kastle

import kotlinx.io.files.Path

class LocalPackRepositoryTest: PackRepositoryTest(
    LocalPackRepository(Path("../repository"))
)