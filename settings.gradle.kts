pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.kaolinmc.com/releases")
        }
        gradlePluginPortal()
    }
}


rootProject.name = "boot"
include("object-container")
