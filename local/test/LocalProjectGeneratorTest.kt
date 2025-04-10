package org.jetbrains.kastle

import kotlinx.io.files.Path
import kotlin.test.Test

class LocalProjectGeneratorTest: ProjectGeneratorTest() {
    override fun createRepository(): PackRepository =
        LocalPackRepository(Path("../example"))

    // Needed for Intellij
    @Test
    fun test() {}
}