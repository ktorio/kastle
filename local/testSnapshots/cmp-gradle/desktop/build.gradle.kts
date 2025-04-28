


group = "com.acme"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
     testImplementation(project("../common")) 
     implementation(compose.desktop.currentOs) 

}