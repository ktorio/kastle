package org.jetbrains.kastle

import kotlinx.io.files.Path
import kotlin.test.Test

class LocalProjectGeneratorTest: ProjectGeneratorTest() {
    override suspend fun createRepository(): PackRepository =
        LocalPackRepository(Path("../repository"))

    // Needed for Intellij
    @Test
    fun test() {}

    @Test
    override fun `ktor server gradle with catalog`() {
        super.`ktor server gradle with catalog`()
    }

    @Test
    override fun `compose multiplatform gradle`() {
        super.`compose multiplatform gradle`()
    }

    @Test
    override fun `compose multiplatform amper`() {
        super.`compose multiplatform amper`()
    }
}