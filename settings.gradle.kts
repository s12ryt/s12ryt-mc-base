pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // Fabric Loom plugin 與相關構建依賴
        maven("https://maven.fabricmc.net/") {
            name = "fabricmc"
        }
        // NeoForge ModDevGradle plugin 與相關構建依賴
        maven("https://maven.neoforged.net/releases") {
            name = "neoforged"
        }
    }
}

rootProject.name = "s12ryt-mc-base"

include("platform-api")
include("platform-core")
include("apps:hello-app")
include("platform-fabric")
include("platform-neoforge")
