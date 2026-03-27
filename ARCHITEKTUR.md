# Oberon — Architektur

## Moduluebersicht

| Modul | Zweck |
|-------|-------|
| `:core` | Domain-Modelle (18 Enums, 16 Models, 12 Repository-Interfaces) |
| `:desktop-data` | SQLite-Persistenz (14 Tabellen) |
| `:platform` | Shared Services (ContextBooster, VirtualInstanceManager, ScopeResolver, DataSync) |
| `:oberon` | Headless Ktor-Server (zentrale KI-Plattform, Port 17900) |

## Technologie-Stack

| Komponente | Technologie |
|-----------|-------------|
| Sprache | Kotlin (JVM 17) |
| Server | Ktor 3.1 (Netty) |
| Persistenz | SQLite (via JDBC) |
| Build | Gradle Kotlin DSL |
| LLM-Gateway | OberonLlmService (OpenAI-kompatibel) |
| DSGVO | FastPiiScanner + AnonymizationEngine |
| TLS | Self-signed Zertifikate (automatisch) |

## API-Endpoints (17)

| Bereich | Endpoints |
|---------|----------|
| Platform | GET /api/v2/platform/status, GET /api/v2/domains |
| Instances | CRUD /api/v2/instances, Messages, Context, Resume |
| Chat | POST /api/v2/instances/{id}/chat, POST .../vision |
| Memory | GET /api/v2/memory |
| Clients | Register/List Gateway-Clients |
| Audit | Audit-Logs |
| Admin | LLM-Config, Admin-Dashboard |
| DSGVO | PII-Scan, Anonymisierung |
| Plan | /api/v2/plan/analyze, /api/v2/plan/flaeche |

## Wichtige Architekturentscheidungen

- **Headless Server**: Kein UI, reiner REST-API-Server
- **Gateway v2**: Authentifizierung per Bearer Token oder X-DevLoop-Token
- **Virtuelle Instanzen**: Pro Projekt/Domaene ein Fokusraum mit eigenem Kontext
- **ContextBooster**: Assembliert Kontext aus Memory + Resume + Konversation fuer LLM
- **Domaenen**: SYSTEM (Coding/Infra) und GUTACHTEN (Gutachten/Bewertung)
- **DSGVO-First**: PII-Scanner + Anonymisierung vor jedem LLM-Aufruf
