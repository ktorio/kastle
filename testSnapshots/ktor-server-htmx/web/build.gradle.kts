
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}


kotlin {
    js {
        browser()
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(npm("htmx.org", "2.0.8"))
        }

    }
}

