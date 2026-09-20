// Fabric 平台入口模組：使用 Loom 構建 Fabric mod。
// platform-core 以 shade jar（已內嵌 Javalin/Jetty/Jackson/SQLite/Gson 等全部依賴）
// 作為單一 Jar-in-Jar 打包進 mod jar——避免逐一 include 傳遞依賴（脆弱且不完整）。

plugins {
    id("fabric-loom") version "1.17.21"
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

// platform-core 的 shade jar 產物（含全部 runtime 依賴的 fat jar，~30MB）。
// Jar 內的 Bukkit 相關類別（S12rytPlugin、BukkitServerAdapter）在 Fabric 端
// 不會被載入（Java 類別懶載入：只有實際引用時才觸發 NoClassDefFoundError，
// 而 Fabric 入口注入的是 FabricServerAdapter），因此安全。
//
// Loom 1.17 的 include 要求 artifact 是 module component（帶 capabilities），
// 純 files(...) 不被接受（processIncludeJars 報錯）。因此 platform-core 暴露
// consumable configuration「shadeBundle」（attributes: Bundling=SHADOWED），
// 這裡以 configuration 消費形式 include。

dependencies {
    // Minecraft + mappings + loader（Loom 管理）
    minecraft("com.mojang:minecraft:1.21.4")
    mappings("net.fabricmc:yarn:1.21.4+build.8:v2")
    modImplementation("net.fabricmc:fabric-loader:0.19.5")

    // Fabric API（ServerLifecycleEvents 等）
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.119.4+1.21.4")

    // 平台模組（編譯用；platform-api 類別已包含在 platform-core shade jar 內）
    api(project(":platform-api"))
    implementation(project(":platform-core"))

    // 打包用：platform-core shade jar 作為單一 nested jar
    // （以 shadeBundle configuration 消費，攜帶 task dependency，
    //   Loom 會先構建 shadowJar 再打包）
    "include"(project(path = ":platform-core", configuration = "shadeBundle"))
}
