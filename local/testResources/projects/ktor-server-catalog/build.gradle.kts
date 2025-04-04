
plugins {
    
    
    alias(libs.plugins.ktor)
}

group = "com.acme"
version = "1.0.0-SNAPSHOT"


repositories {
    mavenCentral()
    
}

// TODO multiplatform
dependencies {
    
    
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
}