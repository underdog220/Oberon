# Oberon — Projektstatus

## Aktueller Stand
Eigenstaendiges Projekt, extrahiert aus DevLoop. Headless Ktor-Server (Port 17900) mit Gateway v2 API, LLM-Integration, DSGVO-Engine, Plan-Analyse und virtuellem Instanz-Management.

## Aktuelle Version
v1.0.0 (Initial Release — aus DevLoop v0.008 extrahiert)

## Module
- `:core` — Domain-Modelle, Enums, Repository-Interfaces
- `:desktop-data` — SQLite-Persistenz (14 Tabellen)
- `:platform` — Shared Services (ContextBooster, VirtualInstanceManager, DataSync)
- `:oberon` — Ktor-Server (Hauptmodul, 17 Endpoints)

## Naechste geplante Stufe
- Oberon Live-Test mit DevLoop Desktop
- Admin-UI Webinterface
- DSGVO: NER-basierter PII-Scanner

## Offene Punkte
- Admin-UI: LLM-Config-Seite im /admin Webinterface
- DSGVO: NER-basierter PII-Scanner (ML) als Ergaenzung zum Regex-Scanner
- Plan-Analyse: Raumtyp-Erkennung verfeinern
- API-Key-Rotation: Hinweis an Nutzer

## Letzte Aenderung
2026-03-27
