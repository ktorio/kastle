package org.jetbrains.kastle

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.io.files.Path
import kotlin.random.Random

val LocalPackRepositoryTest by testSuite {
    testPackRepository(LocalPackRepository(Path(TEST_TEMPLATES_ROOT), random = Random(42L)))
}
