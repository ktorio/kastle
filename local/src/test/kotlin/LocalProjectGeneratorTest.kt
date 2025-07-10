package org.jetbrains.kastle

import kotlinx.io.files.Path

class LocalProjectGeneratorTest: ProjectGeneratorTest({
    LocalPackRepository(Path("../repository"))
})