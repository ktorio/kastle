package org.jetbrains.kastle.utils

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.CatalogArtifact
import org.jetbrains.kastle.CatalogVersion

val CatalogArtifactTest by testSuite("CatalogArtifact") {

    test("serialization") {
        val artifact = CatalogArtifact(
            "module",
            CatalogVersion.Number("1.0.0"),
            kmp = false
        )
        val serialized = Json.encodeToString(artifact)
        println(artifact)
    }

}
