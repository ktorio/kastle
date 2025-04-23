
plugins {
    
    id("io.ktor.plugin")
        
    
}

group = "com.acme"
version = "1.0.0-SNAPSHOT"


repositories {
    mavenCentral()
    
}

// TODO multiplatform
dependencies {
    
    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-cio:3.0.3")
}