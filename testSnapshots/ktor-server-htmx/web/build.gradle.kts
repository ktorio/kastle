
plugins {
    alias(libs.plugins.kotlin.multiplatform)

}


kotlin {
    js {
        browser()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(npm("htmx.org", "2.0.8"))
        }

    }
}
