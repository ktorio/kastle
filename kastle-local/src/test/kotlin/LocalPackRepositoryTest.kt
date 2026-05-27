package org.jetbrains.kastle

import kotlinx.io.files.Path
import kotlin.random.Random

val LocalPackRepositoryTest by PackRepositoryTest("Local", LocalPackRepository(Path(TEST_TEMPLATES_ROOT), random = Random(42L)))
