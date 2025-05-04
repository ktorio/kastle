// Use version catalog


group = "com.acme"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
     testImplementation(project("../shared")) 
     implementation("androidx.activity:activity-compose:1.7.2") 

}