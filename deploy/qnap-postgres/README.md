# PostgreSQL auf QNAP NAS

## Voraussetzungen
- QNAP mit Container Station (Docker-Support)
- Mindestens 512MB freier RAM
- x86/AMD64 oder ARM64 Prozessor

## Installation

### 1. Container Station
1. QNAP Web-UI → Container Station oeffnen
2. "Create Application" klicken
3. `docker-compose.yml` hochladen oder Inhalt einfuegen
4. "Create" klicken → Container startet

### 2. Oberon konfigurieren
In `~/.oberon/oberon.env`:

```properties
OBERON_DB_BROKER_ENABLED=true
OBERON_DB_BROKER_JDBC_URL=jdbc:postgresql://NAS-IP:5432/postgres
OBERON_DB_BROKER_ADMIN_USER=oberon
OBERON_DB_BROKER_ADMIN_PASSWORD=Oberon!Pg2026
OBERON_DB_BROKER_DB_PREFIX=oberon_
```

### 3. Testen
```bash
curl -X POST http://localhost:17900/api/v2/database/provision \
  -H "Authorization: Bearer oberon-dev-token" \
  -H "Content-Type: application/json" \
  -d '{"appName": "dictopic"}'
```

### 4. pgAdmin (optional)
Web-UI: `http://NAS-IP:5050` (admin@oberon.local / admin)

## Backup
```bash
# Alle DBs sichern
docker exec oberon-postgres pg_dumpall -U oberon > backup.sql

# Wiederherstellen (auf neuem Server)
docker-compose up -d
docker exec -i oberon-postgres psql -U oberon < backup.sql
```

## Umzug
docker-compose.yml + backup.sql auf neuen Server kopieren → fertig.
