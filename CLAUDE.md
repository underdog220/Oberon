# Oberon — Projektinstruktionen fuer Claude Code

## Sprache
- Kommunikation: **Deutsch**
- Code-Kommentare: Deutsch (bestehende Konvention beibehalten)

## Projekt-Uebersicht
Oberon ist die zentrale KI-Plattform: Headless Ktor-Server mit Gateway v2 API, virtuellem Instanz-Management, persistentem Gedaechtnis, LLM-Gateway und DSGVO-Engine. Wird von DevLoop Desktop, Android-App und anderen Clients genutzt.

## Tech-Stack
- **Sprache:** Kotlin (JVM 17)
- **Server:** Ktor 3.1 (Netty), Headless
- **Persistenz:** SQLite (JDBC)
- **Build:** Gradle Kotlin DSL

## Module
| Modul | Zweck |
|-------|-------|
| `:core` | Domain-Modelle, Enums, Repository-Interfaces |
| `:desktop-data` | SQLite-Persistenz (14 Tabellen) |
| `:platform` | Shared Services (ContextBooster, VirtualInstanceManager, DataSync) |
| `:oberon` | Ktor-Server (17 Endpoints, Port 17900) |

## Build & Test
```bash
# Kompilieren
./gradlew :oberon:compileKotlin

# Tests
./gradlew :platform:test :oberon:test :core:test

# Fat JAR bauen
./gradlew :oberon:fatJar

# Server starten
java -jar oberon/build/libs/oberon-all.jar
```

## Konventionen
- Keine Umlaute in Quellcode-Dateien (ue, ae, oe)
- Kein Over-Engineering: minimale Aenderungen
- Aenderungen immer kompilieren bevor sie als fertig gelten
