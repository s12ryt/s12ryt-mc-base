plugins {
    id("net.neoforged.moddev") version "2.0.147"
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

neoForge {
    // MC 1.21.4 對應的 NeoForge 版本（官方 maven 最新 21.1.x）
    version = "21.1.251"
}

dependencies {
    api(project(":platform-api"))
    implementation(project(":platform-core"))
}

// NeoForge JarJar：把 platform-core 的 shade jar（含 Javalin/SQLite/Gson 全部依賴）
// 打包進 mod jar 的 META-INF/jarjar/。
// 復用 platform-core 暴露的 shadeBundle consumable configuration
// （attributes: Bundling=SHADOWED，見 platform-core/build.gradle.kts）。
dependencies {
    jarJar(project(path = ":platform-core", configuration = "shadeBundle"))
}
