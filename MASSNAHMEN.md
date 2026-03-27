# Oberon — Massnahmen

## Chronologische Liste

[2026-03-27] [v1.0.0] feat: Oberon als eigenstaendiges Projekt extrahiert
- Aus DevLoop v0.008 extrahiert (vorher Modul :oberon im DevLoop-Monorepo)
- 4 Module: :core, :desktop-data, :platform, :oberon
- Eigenes Gradle-Setup (Kotlin JVM, kein Android/Compose Desktop)
- Fat JAR: oberon-all.jar (Headless Server)
- Git-Repository: underdog220/Oberon
- Build erfolgreich, Fat JAR erfolgreich

[2026-03-27] [v1.0.1] feat: Discovery — UDP-Beacon fuer Netzwerk-Erkennung
- OberonBeacon: UDP-Broadcast auf Port 17901 (alle 30s)
- Beacon-Payload: JSON mit Service, Version, Port, Host, Domains, Uptime, Token-Hint
- OberonDiscovery im :core Modul: Client-API (findFirst, findAll, listen)
- Automatisch im OberonServer.main() gestartet
- Docker-Images aus Git entfernt (.tar/.tar.gz in .gitignore)
