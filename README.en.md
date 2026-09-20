# s12ryt-mc-base

[![CI](https://github.com/s12ryt/s12ryt-mc-base/actions/workflows/ci.yml/badge.svg)](https://github.com/s12ryt/s12ryt-mc-base/actions/workflows/ci.yml)

[简体中文](README.zh-CN.md) | **English (current)** | [繁體中文（主版本）](README.md)

---

A **Minecraft multi-application platform** shipped as a **Paper 1.21.4 plugin.jar** — think Docker containers for your Minecraft server: dynamically load small projects ("Apps") into a running server, where each App gets its own web routes, SQLite database, logger, and scheduler.

> 🎮 **Zero in-game commands**: All management is done through the web console — no `/commands` polluting your server.

## ✨ Features

- 🐳 **Container-style App management**: scans the `apps/` directory, isolates each App with its own `URLClassLoader`, supports hot load / unload / reload at runtime
- 🌐 **Embedded web console**: Javalin 6 + Vue 3, admin password login (a strong random password is generated on first launch)
- 📦 **Per-App isolated resources**: dedicated SQLite file (`apps/{id}/data.db`), dedicated log stream (ring buffer + file), dedicated config file
- ⏰ **Full App capabilities**: custom web routes, scheduled tasks, async execution, main-thread dispatch, access to the Bukkit API (`org.bukkit.Server`)
- 🛡️ **Fault isolation**: one App crashing never takes down the platform or other Apps
- 🔐 **PBKDF2 password storage** + Bearer token auth with 24-hour expiration

## 📂 Repository Layout

```
s12ryt-mc-base/
├── platform-api/      # App developer API (the only module Apps depend on)
├── platform-core/     # Platform core + web console (shaded into plugin.jar)
│   └── web-console/   # Vue 3 + Vite frontend
├── apps/hello-app/    # Demo App (showcases all platform capabilities)
└── .github/workflows/ # CI: build + 263 unit tests + Paper smoke test
```

## 🚀 Quick Start

### 1. Install

Drop `platform-core/build/libs/s12ryt-mc-base-0.1.0.jar` into your Paper server's `plugins/` directory and start the server.

### 2. Get the admin password

On first launch, the platform generates a random 16-character password and prints it to the server log:

```
[s12ryt-mc-base] First launch, admin password generated: XXXXXXXXXXXXXXXX
```

### 3. Log in to the web console

Open `http://your-server-ip:8080/console/` in your browser (port is configurable via `web.port` in `plugins/s12ryt-mc-base/config.yml`) and enter the password.

### 4. Deploy your first App

Place an App jar (e.g. `hello-app-0.1.0.jar`) into `plugins/s12ryt-mc-base/apps/`, then restart the server or reload via the web console.

## 🧑‍💻 Writing Your Own App

### 1. Add the dependency (Gradle Kotlin DSL example)

```kotlin
repositories {
    // Apps only need platform-api (no Javalin / Bukkit required)
}
dependencies {
    compileOnly(files("platform-api-0.1.0.jar")) // or publish to a maven repo
}
```

### 2. Implement the App interface

```java
public class MyApp implements App {
    @Override
    public void onEnable(AppContext ctx) {
        // Web routes
        ctx.router().get("/hello", req ->
            AppHttpResponse.json("{\"msg\":\"hi\"}"));

        // SQLite
        try (Connection c = ctx.storage().connection()) {
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS t(id INTEGER)");
        } catch (SQLException ignored) {}

        // Scheduled task (async)
        ctx.scheduler().scheduleAtFixedDelay(0, 5000, () ->
            ctx.logger().info("heartbeat"));

        // Logging
        ctx.logger().info("MyApp ready");
    }

    @Override
    public void onDisable() { /* release resources */ }
}
```

### 3. Declare app.properties (at the jar root)

```properties
app.id=my-app
app.name=My App
app.version=1.0.0
app.main=com.example.MyApp
```

### 4. Deploy

Drop the jar into `plugins/s12ryt-mc-base/apps/`. App routes are mounted under `http://server:8080/apps/my-app/...`.

## 🌐 Web Console

| Page | Function |
|------|----------|
| Login | Admin password login |
| App list | App status (RUNNING/FAILED), reload / unload buttons |
| Logs | Live App log viewer (2s polling) |

## 📡 Built-in API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/health` | Health check (no auth) |
| POST | `/api/auth/login` | Login, returns token (no auth) |
| POST | `/api/auth/logout` | Logout |
| POST | `/api/auth/change-password` | Change password |
| GET | `/api/apps` | List Apps |
| POST | `/api/apps/{id}/reload` | Reload an App |
| POST | `/api/apps/{id}/unload` | Unload an App |
| GET | `/api/apps/{id}/logs` | Read App logs |

Protected APIs require the `Authorization: Bearer <token>` header.

## 🔨 Building from Source

Requirements: JDK 21+, Node.js 18+ (for the frontend build)

```bash
./gradlew build
# Output:
#   platform-core/build/libs/s12ryt-mc-base-0.1.0.jar  ← plugin.jar (shaded)
#   apps/hello-app/build/libs/hello-app-0.1.0.jar      ← demo App
```

## ✅ Quality

- **263 unit tests**, all green (JUnit 5 — covering auth, container, routing, storage, logging, scheduling, web API)
- **Fully automated CI smoke test**: GitHub Actions downloads real Paper 1.21.4, boots a server, and verifies plugin load, App startup, and web API responses

## 📄 Tech Stack

| Layer | Technology |
|-------|------------|
| Base | Paper 1.21.4 / Java 21 |
| Web backend | Javalin 6.6.0 |
| Frontend | Vue 3 + Vite |
| Storage | SQLite (sqlite-jdbc 3.53.2.1) |
| JSON | Gson 2.11.0 |
| Testing | JUnit 5 |
| Build | Gradle Kotlin DSL + Shadow |

## 📄 License

No license specified. All rights reserved.

---

**Other languages**: [简体中文](README.zh-CN.md) | [繁體中文（主版本）](README.md)
