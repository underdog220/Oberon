# SQL Server auf QNAP NAS

## Voraussetzungen
- QNAP mit Container Station (Docker-Support)
- Mindestens 2GB freier RAM (4GB+ empfohlen)
- x86/AMD64 Prozessor (ARM wird von SQL Server nicht unterstuetzt)

## Installation

### 1. Container Station
1. QNAP Web-UI → Container Station oeffnen
2. "Create Application" klicken
3. `docker-compose.yml` hochladen oder Inhalt einfuegen
4. "Create" klicken → Container startet

### 2. Oberon konfigurieren
In `~/.oberon/oberon.env` (auf dem Rechner wo Oberon laeuft):

```properties
OBERON_DB_BROKER_ENABLED=true
OBERON_DB_BROKER_JDBC_URL=jdbc:sqlserver://NAS-IP:1433;encrypt=false;trustServerCertificate=true
OBERON_DB_BROKER_ADMIN_USER=sa
OBERON_DB_BROKER_ADMIN_PASSWORD=Oberon!Sql2026
OBERON_DB_BROKER_DB_PREFIX=oberon_
```

Ersetze `NAS-IP` durch die IP deines QNAP NAS (z.B. `192.168.200.100`).

### 3. Testen
```bash
# Oberon starten
java -jar oberon-all.jar

# Datenbank provisionieren
curl -X POST http://localhost:17900/api/v2/database/provision \
  -H "Authorization: Bearer oberon-dev-token" \
  -H "Content-Type: application/json" \
  -d '{"appName": "dictopic"}'

# Status pruefen
curl http://localhost:17900/api/v2/database/status \
  -H "Authorization: Bearer oberon-dev-token"
```

## Sicherheit
- **SA-Passwort aendern!** Das Default-Passwort `Oberon!Sql2026` ist nur fuer Entwicklung.
- Port 1433 ist nur im lokalen Netzwerk erreichbar (kein Port-Forwarding noetig).
- Jede App bekommt einen eigenen DB-User mit eingeschraenkten Rechten.

## Speicherverbrauch
- SQL Server Express: max 1.4GB RAM (Express-Limit)
- `MSSQL_MEMORY_LIMIT_MB=1024` begrenzt auf 1GB
- Kann auf 512MB reduziert werden fuer leichte Nutzung

## Backup
Die Datenbank-Dateien liegen im Docker-Volume `sqldata`.
QNAP sichert Volumes automatisch wenn Backup-Jobs konfiguriert sind.
