plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.6.1"
}

// ─── 前端構建整合 ─────────────────────────────────────────
// 定義 npm build task：執行 vite build，產出 web-console/dist/
val buildWebConsole = tasks.register<Exec>("buildWebConsole") {
    group = "build"
    description = "Builds the Vue 3 web console (vite build)"
    workingDir = file("web-console")
    // Windows: npm.cmd；其他平台: npm
    if (System.getProperty("os.name").lowercase().contains("win")) {
        commandLine("cmd", "/c", "npm", "run", "build")
    } else {
        commandLine("npm", "run", "build")
    }
    // 僅當 dist/ 不存在或 web-console/src/ 有變更時才重建
    inputs.dir("web-console/src")
    inputs.dir("web-console/public")
    inputs.file("web-console/package.json")
    inputs.file("web-console/vite.config.js")
    inputs.file("web-console/index.html")
    outputs.dir("web-console/dist")
}

// 讓 processResources 依賴前端構建，確保 dist/ 在打包 jar 前就緒
tasks.processResources {
    dependsOn(buildWebConsole)
    // 把 web-console/dist/ 複製到 resources/web-console/
    from("web-console/dist") {
        into("web-console")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// 獨立 source set：打成 jar 供 AppContainer ClassLoader 隔離測試使用。
// 「故意」不進 testCompileClasspath / testRuntimeClasspath，確保測試 App 類別
// 只能由 App 專屬 ClassLoader 從 jar 載入（而非從 test classpath 解析）。
sourceSets {
    create("testApps") {
        java.srcDir("src/testApps/java")
    }
}

dependencies {
    api(project(":platform-api"))

    // Bukkit/Paper API（provided scope，不由本 plugin 攜帶）
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // 內嵌至 plugin.jar（javalin-bundle 含 Jackson + Logback + 測試工具）
    implementation("io.javalin:javalin-bundle:6.6.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.xerial:sqlite-jdbc:3.53.2.1")

    // 測試 App fixture 編譯用（僅 platform-api + paper-api，不依賴 platform-core）
    "testAppsCompileOnly"(project(":platform-api"))
    "testAppsCompileOnly"("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // JavalinTest 整合測試用
    testImplementation("org.assertj:assertj-core:3.26.3")
    // FakeAppContext 實作 AppContext（getServer() 回傳 org.bukkit.Server），測試端需要 paper-api
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.named("compileTestAppsJava"))
    systemProperty(
        "testAppsClassesDir",
        layout.buildDirectory.dir("classes/java/testApps").get().asFile.absolutePath,
    )
    testLogging {
        events("failed", "skipped")
        showExceptions = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// ─── Shadow（fat jar）配置 ──────────────────────────────────
// 產出單一 plugin.jar，內嵌所有依賴（Javalin + Gson + SQLite-JDBC）+ 前端資源。
// Paper plugin 需求：plugin.yml 在 jar 根目錄（resources/ 預設就在根目錄）。
tasks.shadowJar {
    archiveBaseName = "s12ryt-mc-base"
    archiveClassifier = "" // 不加 -all 後綴，直接是主 jar
    archiveVersion = "0.1.0"

    // 合併 META-INF/services（JDBC driver、Javalin 等 ServiceLoader）
    mergeServiceFiles()

    // 重複條目一律排除（保留第一次出現者）。
    // Paper 的 PluginRemapper 讀取 jar 時遇到 duplicate entries 會直接失敗
    // （「Failed to remap plugin jar」），因此不可用 INCLUDE。
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // 排除多依賴合併時大量重複的 metadata（不影響運行）
    exclude("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/LICENSE.md")
    exclude("META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/NOTICE.md")
    exclude("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST")
    exclude("META-INF/versions/*/module-info.class", "META-INF/versions/9/module-info.class")
    exclude("META-INF/*.version", "META-INF/maven/*/pom.xml", "META-INF/maven/*/pom.properties")

    // 排除不必要的簽章檔
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    // 排除潛在衝突的模組描述
    exclude("module-info.class", "META-INF/modules/*")
}

// 讓 build 最終產出 shadowJar（而非普通 jar）
tasks.build {
    dependsOn(tasks.shadowJar)
}
