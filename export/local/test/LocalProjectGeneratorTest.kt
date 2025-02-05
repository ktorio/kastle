package org.jetbrains.kastle

import kotlinx.io.files.Path
import kotlin.test.Test

class LocalProjectGeneratorTest: ProjectGeneratorTest() {
    override fun createRepository(): KodRepository =
        LocalKodRepository(Path("example"))

    @Test
    fun doATest() {
        // this is here to trick the IDE into running this test
    }
}