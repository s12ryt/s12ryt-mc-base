pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "s12ryt-mc-base"

include("platform-api")
include("platform-core")
include("apps:hello-app")
