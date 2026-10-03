# MikeyCore

shared code for my plugins. plain java 21 except a tiny paper main class so the jar can sit in `plugins/` without paper rejecting it. staff and holograms both use it. they declare it as a hard plugin dependency instead of shading it, so the jar has to be installed or the plugin will not load.

whats in it (`mikey.me.core`):

- `communication.protocol.PluginProtocol` signs and checks plugin messages (`wrap`/`unwrap`, HMAC-SHA256 with a shared secret, 30s window, replayed nonces get dropped)
- `communication.LocalCommunication` in process message bus for paper only mode, behind `CommunicationApi`. `CommunicationMode` is `PROXY` or `PAPER`
- `json.JsonUtil` tiny json helpers (`escape`, `extractString`, `extractLong`, etc)
- `registry.CoreRegistry` `init` sets `plugins/MikeyCore`. `register("staff")` makes `plugins/MikeyCore/staff/` and `database()` reads `database.yml` in that folder
- `persistence.YamlFile` reads and writes a plain yaml file (maps, lists, numbers, bools). no bukkit. `read` on a missing file is an empty map
- `persistence.DatabaseConfig` db settings record, `toString` hides the password
- `persistence.DatabasePool` hikari pool from that config. `connection()` borrows one, `close()` stops it. the plugin opens `plugins/MikeyCore/database.yml` on enable and closes it on disable. no mysql just means nothing connects until something asks
- `persistence.ActivePunishmentPolicy` checks if a ban/mute is still active from its start time and duration
- `web.WebServer` tiny http server. `start(port, token)` binds, `route(method, path, handler)` adds a route, `stop()` closes it. empty token stays on 127.0.0.1. a token listens on every interface and has to be sent as `Authorization: Bearer`, `X-Token`, or `?token=`. no extra deps, jdk http server
- `world.WorldPoint` a world name plus x y z. `within` uses a show distance and a bigger hide distance so stuff doesnt flicker on the edge
- `text.Lines` add/set/remove on a list of strings. line numbers are 1-based, a bad one comes back empty
- `text.Placeholders` `contains` / `any` for a `%placeholder%` token. `100%` and `%%` dont count
- `text.Keys.simple` ids matching `[a-z0-9_-]+`
- `text.Colors` `&` codes, hex (`&#rrggbb`, `<#rrggbb>`, `{#rrggbb}`, `#rrggbb`, `&x&r&r&g&g&b&b`) and `<#start>text</#end>` gradients. `color()` reads `#rrggbb` / `#aarrggbb`
- `text.Animations` `&u`, `<RAINBOW>`, and `<#ANIM:wave|burn|typewriter|scroll|colors>`. `frame` is one tick of that text

## using it

its not published anywhere, plugins pull it in as a Gradle composite build. put this repo next to the plugin repo and add to the plugins `settings.gradle.kts`:

```kotlin
includeBuild("../MikeyCore") {
    name = "mikey-core"
}
```

then depend on it with compileOnly, dont shade it:

```kotlin
compileOnly("mikey.core:mikey-core:0.1.0-SNAPSHOT")
```

gradle swaps in the local build for that coordinate so changes here show up without publishing.

## build

needs JDK 21.

```bash
./gradlew build
```

`./gradlew test` for just the tests.
