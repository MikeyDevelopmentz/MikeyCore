# MikeyCore

shared code for my plugins. plain Java 21 lib, no Bukkit/Velocity/Hikari/MySQL in here so paper and velocity plugins can both use it. MikeyStaff is the only thing using it right now.

whats in it (`mikey.me.core`):

- `communication.protocol.PluginProtocol` signs and checks plugin messages (`wrap`/`unwrap`, HMAC-SHA256 with a shared secret, 30s window, replayed nonces get dropped)
- `communication.LocalCommunication` in process message bus for paper only mode, behind `CommunicationApi`. `CommunicationMode` is `PROXY` or `PAPER`
- `json.JsonUtil` tiny json helpers (`escape`, `extractString`, `extractLong`, etc)
- `persistence.DatabaseConfig` db settings record, `toString` hides the password
- `persistence.ActivePunishmentPolicy` checks if a ban/mute is still active from its start time and duration

## Using it

its not published anywhere, plugins pull it in as a Gradle composite build. put this repo next to the plugin repo and add to the plugins `settings.gradle.kts`:

```kotlin
includeBuild("../MikeyCore") {
    name = "mikey-core"
}
```

then depend on it and shade it into the plugin jar:

```kotlin
implementation("mikey.core:mikey-core:0.1.0-SNAPSHOT")
```

gradle swaps in the local build for that coordinate so changes here show up without publishing.

## Build

needs JDK 21.

```bash
./gradlew build
```

`./gradlew test` for just the tests.
