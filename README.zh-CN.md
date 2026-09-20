# s12ryt-mc-base

[![CI](https://github.com/s12ryt/s12ryt-mc-base/actions/workflows/ci.yml/badge.svg)](https://github.com/s12ryt/s12ryt-mc-base/actions/workflows/ci.yml)

**简体中文（当前页面）** | [English](README.en.md) | [繁體中文（主版本）](README.md)

---

以 **MC 多应用平台**形式存在的**类 Docker 容器系统**——支持 **Paper / Fabric / NeoForge / Forge**（均为 Minecraft 1.21.4），让你把各种小项目（App）动态放进 Minecraft 服务器里运行，每个 App 都有自己独立的 Web 路由、SQLite 数据库、日志与调度器。

| 支持平台 | 版本 | 安装位置 |
|---------|------|---------|
| **Paper** | 1.21.4 | `plugins/` |
| **Fabric** | 1.21.4（需 fabric-api） | `mods/` |
| **NeoForge** | 21.1.x | `mods/` |
| **Forge** | 54.x（1.21.4） | `mods/` |

> 🎮 **零游戏内指令**：所有管理操作都通过 Web 控制台完成，不会污染游戏内的指令空间。

## ✨ 核心特性

- 🐳 **容器式 App 管理**：`apps/` 目录扫描、独立 `URLClassLoader` 隔离、运行时热加载／卸载／重载
- 🌐 **内嵌 Web 控制台**：Javalin 6 + Vue 3，管理员密码登录（首次启动自动生成强密码）
- 📦 **每 App 独立资源**：独立 SQLite（`apps/{id}/data.db`）、独立日志（ring buffer + 文件）、独立配置文件
- ⏰ **App 能力全开**：Web 路由挂载、定时调度、异步执行、主线程跳回、可访问 Bukkit API（`org.bukkit.Server`）
- 🛡️ **故障隔离**：单一 App 崩溃不影响平台与其他 App
- 🔐 **PBKDF2 密码存储** + 24 小时过期的 Bearer token 认证

## 📂 仓库结构

```
s12ryt-mc-base/
├── platform-api/      # App 开发者 API（唯一需要依赖的模块）
├── platform-core/     # 平台核心 + Web 控制台（shade 打包成 plugin.jar）
│   └── web-console/   # Vue 3 + Vite 前端
├── apps/hello-app/    # 示范 App（展示全部平台能力）
└── .github/workflows/ # CI：构建 + 263 单元测试 + Paper 烟雾测试
```

## 🚀 快速开始

### 1. 安装

把 `platform-core/build/libs/s12ryt-mc-base-0.1.0.jar` 放进 Paper 服务器的 `plugins/` 目录，然后启动服务器。

### 2. 获取管理员密码

首次启动时，平台会自动生成 16 字符随机密码并打印在服务器日志：

```
[s12ryt-mc-base] 首次启动，管理员密码已生成：XXXXXXXXXXXXXXXX
```

### 3. 登录 Web 控制台

浏览器打开 `http://你的服务器IP:8080/console/`（端口号可在 `plugins/s12ryt-mc-base/config.yml` 的 `web.port` 修改），输入密码即可。

### 4. 放入你的第一个 App

把 App jar（例如 `hello-app-0.1.0.jar`）放到 `plugins/s12ryt-mc-base/apps/` 目录，重启服务器或通过 Web 控制台重载即可。

## 🧑‍💻 开发你自己的 App

### 1. 加入依赖（Gradle Kotlin DSL 为例）

```kotlin
repositories {
    // App 只需要 platform-api（不需要 Javalin / Bukkit）
}
dependencies {
    compileOnly(files("platform-api-0.1.0.jar")) // 或发布到 maven 后引用
}
```

### 2. 实现 App 接口

```java
public class MyApp implements App {
    @Override
    public void onEnable(AppContext ctx) {
        // Web 路由
        ctx.router().get("/hello", req ->
            AppHttpResponse.json("{\"msg\":\"hi\"}"));

        // SQLite
        try (Connection c = ctx.storage().connection()) {
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS t(id INTEGER)");
        } catch (SQLException ignored) {}

        // 定时任务（异步）
        ctx.scheduler().scheduleAtFixedDelay(0, 5000, () ->
            ctx.logger().info("heartbeat"));

        // 日志
        ctx.logger().info("MyApp ready");
    }

    @Override
    public void onDisable() { /* 清理资源 */ }
}
```

### 3. 声明 app.properties（放在 jar 根目录）

```properties
app.id=my-app
app.name=My App
app.version=1.0.0
app.main=com.example.MyApp
```

### 4. 部署

jar 丢进 `plugins/s12ryt-mc-base/apps/`，App 路由挂载在 `http://服务器:8080/apps/my-app/...` 下。

## 🌐 Web 控制台功能

| 页面 | 功能 |
|------|------|
| 登录页 | 管理员密码登录 |
| App 列表 | 显示各 App 状态（RUNNING/FAILED）、重载／卸载按钮 |
| 日志页 | 即时轮询查看 App 日志（2 秒） |

## 📡 内置 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 健康检查（无需认证） |
| POST | `/api/auth/login` | 登录获取 token（无需认证） |
| POST | `/api/auth/logout` | 登出 |
| POST | `/api/auth/change-password` | 变更密码 |
| GET | `/api/apps` | App 列表 |
| POST | `/api/apps/{id}/reload` | 重载 App |
| POST | `/api/apps/{id}/unload` | 卸载 App |
| GET | `/api/apps/{id}/logs` | 读取 App 日志 |

受保护 API 需带 `Authorization: Bearer <token>` 请求头。

## 🔨 从源码构建

需求：JDK 21+、Node.js 18+（前端构建）

```bash
./gradlew build
# 产出：
#   platform-core/build/libs/s12ryt-mc-base-0.1.0.jar    ← Paper plugin.jar（shade）
#   platform-fabric/build/libs/platform-fabric-0.1.0.jar  ← Fabric mod
#   platform-neoforge/build/libs/platform-neoforge-0.1.0.jar ← NeoForge mod
#   apps/hello-app/build/libs/hello-app-0.1.0.jar         ← 示范 App

# Forge 版需单独构建（ForgeGradle 6 不支持 Gradle 9，为嵌套独立构建）：
cd platform-forge && ./gradlew build
# 产出：platform-forge/build/libs/platform-forge.jar ← Forge mod
```

## ✅ 质量保证

- **274 个单元测试**全绿（JUnit 5，涵盖认证、容器、路由、存储、日志、调度、Web API、ServerAdapter SPI）
- **CI 全自动烟雾测试**：GitHub Actions 真实下载 Paper 1.21.4 启动服务器，验证 plugin 加载、App 启动、Web API 响应

## 📄 技术栈

| 层 | 技术 |
|----|------|
| 平台 | Paper 1.21.4 / Fabric 1.21.4 / NeoForge 21.1.x / Forge 54.x / Java 21 |
| Web 后端 | Javalin 6.6.0 |
| 前端 | Vue 3 + Vite |
| 存储 | SQLite（sqlite-jdbc 3.53.2.1） |
| JSON | Gson 2.11.0 |
| 测试 | JUnit 5 |
| 构建 | Gradle Kotlin DSL + Shadow / Loom / ModDevGradle / ForgeGradle 6 |

## 📄 授权

未指定授权，保留所有权利。

---

**其他语言**：[English](README.en.md) | [繁體中文（主版本）](README.md)
