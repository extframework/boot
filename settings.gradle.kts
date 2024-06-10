pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.extframework.dev/releases")
        }
        gradlePluginPortal()
    }
}


rootProject.name = "boot"
include("test")
include("object-container")
include("blackbox-test")
