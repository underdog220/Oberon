package com.devloop.oberon

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

/**
 * Oberon-Server-Konfiguration.
 * Prioritaet: ENV-Variable > ~/.oberon/oberon.env > Default-Wert.
 */
data class OberonConfig(
    val host: String,
    val port: Int,
    val dbPath: Path,
    val mssqlJdbcUrl: String?,
    val token: String,
    val domains: List<String>,
    val syncIntervalMs: Long,
    val dataDir: Path,
    // LLM-Anbindung
    val openAiApiKey: String,
    val openAiBaseUrl: String,
    val openAiModel: String,
    /** HTTPS-Port (0 = HTTPS deaktiviert). */
    val httpsPort: Int,
    /** Ob TLS aktiviert werden soll. */
    val tlsEnabled: Boolean,
    // DSGVO-Einstellungen
    /** DSGVO-Modul aktiv (PII-Scan + Anonymisierung) */
    val dsgvoEnabled: Boolean,
    /** URL des lokalen LLM (z.B. Ollama) fuer DSGVO-konforme Verarbeitung */
    val dsgvoLocalLlmUrl: String,
    /** Immer anonymisieren, auch wenn kein PII erkannt wurde */
    val dsgvoAlwaysAnonymize: Boolean,
    /** Aufbewahrungsdauer fuer Audit-Logs in Tagen */
    val dsgvoAuditRetentionDays: Int,
    /** Session-TTL fuer Anonymisierungs-Mappings in Minuten */
    val dsgvoSessionTtlMinutes: Int,
    // Database-Broker
    /** Database-Broker aktiv (zentrale DB-Verwaltung fuer alle Apps) */
    val dbBrokerEnabled: Boolean,
    /** JDBC-URL des zentralen SQL-Servers (z.B. jdbc:sqlserver://nas.local:1433) */
    val dbBrokerJdbcUrl: String,
    /** Admin-User fuer den SQL-Server (darf Datenbanken/User erstellen) */
    val dbBrokerAdminUser: String,
    /** Admin-Passwort fuer den SQL-Server */
    val dbBrokerAdminPassword: String,
    /** Prefix fuer erstellte Datenbanken (z.B. "oberon_" → "oberon_dictopic") */
    val dbBrokerDatabasePrefix: String,
) {
    /** Pfad zur persistierten Konfigurationsdatei. */
    val envFile: Path get() = dataDir.resolve("oberon.env")

    companion object {
        fun load(): OberonConfig {
            val dataDir = Paths.get(
                System.getenv("OBERON_DATA_DIR")
                    ?: System.getProperty("oberon.dataDir")
                    ?: Paths.get(System.getProperty("user.home"), ".oberon").toString()
            )
            // Sicherstellen, dass dataDir existiert
            Files.createDirectories(dataDir)

            // oberon.env laden (Key=Value Properties-Datei)
            val envFile = dataDir.resolve("oberon.env")
            val fileProps = loadEnvFile(envFile)

            // Hilfsfunktion: ENV > oberon.env > default
            fun resolve(envKey: String, default: String): String =
                System.getenv(envKey)?.takeIf { it.isNotBlank() }
                    ?: fileProps.getProperty(envKey)?.takeIf { it.isNotBlank() }
                    ?: default

            return OberonConfig(
                host = resolve("OBERON_HOST", "0.0.0.0"),
                port = resolve("OBERON_PORT", "17900").toIntOrNull() ?: 17900,
                dbPath = dataDir.resolve("oberon.db"),
                mssqlJdbcUrl = resolve("OBERON_MSSQL_JDBC_URL", "").takeIf { it.isNotBlank() },
                token = resolve("OBERON_TOKEN", "oberon-dev-token"),
                domains = resolve("OBERON_DOMAINS", "SYSTEM,GUTACHTEN")
                    .split(",").map { it.trim() }.filter { it.isNotBlank() },
                syncIntervalMs = resolve("OBERON_SYNC_INTERVAL_MS", "30000").toLongOrNull() ?: 30_000,
                dataDir = dataDir,
                openAiApiKey = resolve("OBERON_OPENAI_API_KEY", ""),
                openAiBaseUrl = resolve("OBERON_OPENAI_BASE_URL", "https://api.openai.com"),
                openAiModel = resolve("OBERON_OPENAI_MODEL", "gpt-4o-mini"),
                httpsPort = resolve("OBERON_HTTPS_PORT", "17943").toIntOrNull() ?: 17943,
                tlsEnabled = resolve("OBERON_TLS_ENABLED", "true") != "false",
                dsgvoEnabled = resolve("OBERON_DSGVO_ENABLED", "true").toBoolean(),
                dsgvoLocalLlmUrl = resolve("OBERON_DSGVO_LOCAL_LLM_URL", "http://localhost:11434"),
                dsgvoAlwaysAnonymize = resolve("OBERON_DSGVO_ALWAYS_ANONYMIZE", "false").toBoolean(),
                dsgvoAuditRetentionDays = resolve("OBERON_DSGVO_AUDIT_RETENTION_DAYS", "90").toIntOrNull() ?: 90,
                dsgvoSessionTtlMinutes = resolve("OBERON_DSGVO_SESSION_TTL_MINUTES", "60").toIntOrNull() ?: 60,
                dbBrokerEnabled = resolve("OBERON_DB_BROKER_ENABLED", "false").toBoolean(),
                dbBrokerJdbcUrl = resolve("OBERON_DB_BROKER_JDBC_URL", ""),
                dbBrokerAdminUser = resolve("OBERON_DB_BROKER_ADMIN_USER", "sa"),
                dbBrokerAdminPassword = resolve("OBERON_DB_BROKER_ADMIN_PASSWORD", ""),
                dbBrokerDatabasePrefix = resolve("OBERON_DB_BROKER_DB_PREFIX", "oberon_"),
            )
        }

        /** Laedt eine Key=Value-Datei (# Kommentare erlaubt). */
        private fun loadEnvFile(path: Path): Properties {
            val props = Properties()
            if (Files.exists(path)) {
                Files.newBufferedReader(path).use { reader ->
                    props.load(reader)
                }
            }
            return props
        }
    }
}
