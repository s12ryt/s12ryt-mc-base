// 根建置檔：只放置共用倉庫聲明與中性配置，各模組自包含。

plugins {
    base
}

allprojects {
    group = "dev.s12ryt"
    version = "0.1.0"
}

subprojects {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
    }
}
