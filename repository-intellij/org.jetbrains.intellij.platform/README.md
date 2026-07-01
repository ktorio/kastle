The IntelliJ Platform repository is structure in a specific way to workaround some KASTLE default behaviors.

## Classic and Modular plugin sources are in the same directory

Having them close to each other allows for adjusting both layouts consistently.
They also share some basic things like Gradle Wrapper files, 

[//]: # (TODO: gradle.properties - ? Ask Nikita if we need org.gradle.jvmargs &#40;or create slot for additional props&#41;.)
[//]: # (TODO: .gitignore)

We want to have Modular plugin in the Architecture group, so the Modular pack works as enabler for modular plugin setup.
