package com.devloop.oberon.database

import com.devloop.oberon.OberonConfig
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Database-Broker: Verwaltet SQL-Server-Datenbanken fuer alle Apps zentral.
 *
 * Apps registrieren sich bei Oberon und bekommen automatisch:
 * - Eine eigene Datenbank (isoliert)
 * - Einen eigenen DB-User mit eingeschraenkten Rechten
 * - JDBC-Zugangsdaten
 *
 * Oberon ist der einzige mit Admin-Zugang zum SQL-Server.
 * Apps bekommen nur Zugang zu ihrer eigenen Datenbank.
 */
class DatabaseBroker(private val config: OberonConfig) {

    private val log = LoggerFactory.getLogger("DatabaseBroker")

    /** Registry: appName → ProvisionedDatabase */
    private val registry = ConcurrentHashMap<String, ProvisionedDatabase>()

    /** Pfad zur persistierten Registry (JSON-Datei). */
    private val registryFile = config.dataDir.resolve("db-broker-registry.json").toFile()

    init {
        loadRegistry()
    }

    /**
     * Provisioniert eine Datenbank fuer eine App.
     * Wenn die App schon registriert ist, werden die bestehenden Credentials zurueckgegeben.
     */
    fun provision(appName: String, appVersion: String = ""): ProvisionResult {
        val normalized = appName.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
        if (normalized.isBlank()) {
            return ProvisionResult.Error("Ungueltiger App-Name")
        }

        // Schon registriert?
        val existing = registry[normalized]
        if (existing != null) {
            log.info("DB-Broker: App '$normalized' bereits registriert (DB: ${existing.database})")
            return ProvisionResult.Success(existing)
        }

        if (!config.dbBrokerEnabled || config.dbBrokerJdbcUrl.isBlank()) {
            return ProvisionResult.Error("Database-Broker nicht konfiguriert (OBERON_DB_BROKER_JDBC_URL fehlt)")
        }

        val dbName = "${config.dbBrokerDatabasePrefix}$normalized"
        val dbUser = "${normalized}_svc"
        val dbPassword = generatePassword(24)

        log.info("DB-Broker: Provisioniere DB '$dbName' fuer App '$normalized'...")

        try {
            getAdminConnection().use { conn ->
                // 1. Datenbank erstellen
                conn.createStatement().execute("IF DB_ID('$dbName') IS NULL CREATE DATABASE [$dbName]")
                log.info("DB-Broker: Datenbank '$dbName' erstellt (oder existiert bereits)")

                // 2. Login erstellen
                try {
                    conn.createStatement().execute(
                        "IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = '$dbUser') " +
                            "CREATE LOGIN [$dbUser] WITH PASSWORD = '$dbPassword', DEFAULT_DATABASE = [$dbName]"
                    )
                } catch (e: Exception) {
                    // Login existiert evtl. schon — Passwort aendern
                    conn.createStatement().execute("ALTER LOGIN [$dbUser] WITH PASSWORD = '$dbPassword'")
                }

                // 3. User in der Datenbank erstellen + Rechte
                conn.createStatement().execute("USE [$dbName]")
                conn.createStatement().execute(
                    "IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = '$dbUser') " +
                        "CREATE USER [$dbUser] FOR LOGIN [$dbUser]"
                )
                conn.createStatement().execute("ALTER ROLE db_datareader ADD MEMBER [$dbUser]")
                conn.createStatement().execute("ALTER ROLE db_datawriter ADD MEMBER [$dbUser]")
                conn.createStatement().execute("GRANT CREATE TABLE TO [$dbUser]")
                conn.createStatement().execute("GRANT ALTER ON SCHEMA::dbo TO [$dbUser]")

                log.info("DB-Broker: User '$dbUser' mit Lese-/Schreib-/DDL-Rechten erstellt")
            }
        } catch (e: Throwable) {
            log.error("DB-Broker: Provisionierung fehlgeschlagen: ${e.message}")
            return ProvisionResult.Error("SQL-Fehler: ${e.message}")
        }

        // JDBC-URL fuer die App zusammenbauen
        val appJdbcUrl = buildAppJdbcUrl(dbName)

        val provisioned = ProvisionedDatabase(
            appName = normalized,
            appVersion = appVersion,
            database = dbName,
            username = dbUser,
            password = dbPassword,
            jdbcUrl = appJdbcUrl,
            provisionedAtMs = System.currentTimeMillis(),
        )

        registry[normalized] = provisioned
        saveRegistry()

        log.info("DB-Broker: App '$normalized' provisioniert → $dbName")
        return ProvisionResult.Success(provisioned)
    }

    /**
     * Rotiert die Credentials einer App (neues Passwort).
     */
    fun rotateCredentials(appName: String): ProvisionResult {
        val normalized = appName.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val existing = registry[normalized] ?: return ProvisionResult.Error("App '$normalized' nicht registriert")

        val newPassword = generatePassword(24)

        try {
            getAdminConnection().use { conn ->
                conn.createStatement().execute("ALTER LOGIN [${existing.username}] WITH PASSWORD = '$newPassword'")
            }
        } catch (e: Throwable) {
            return ProvisionResult.Error("Rotation fehlgeschlagen: ${e.message}")
        }

        val updated = existing.copy(password = newPassword, provisionedAtMs = System.currentTimeMillis())
        registry[normalized] = updated
        saveRegistry()

        log.info("DB-Broker: Credentials fuer '$normalized' rotiert")
        return ProvisionResult.Success(updated)
    }

    /**
     * Gibt den Status aller provisionierten Datenbanken zurueck.
     */
    fun status(): BrokerStatus {
        val serverReachable = if (config.dbBrokerEnabled && config.dbBrokerJdbcUrl.isNotBlank()) {
            try {
                getAdminConnection().use { it.isValid(5) }
            } catch (_: Throwable) { false }
        } else false

        return BrokerStatus(
            enabled = config.dbBrokerEnabled,
            serverUrl = config.dbBrokerJdbcUrl,
            serverReachable = serverReachable,
            databases = registry.values.toList(),
        )
    }

    /**
     * Gibt die Credentials fuer eine registrierte App zurueck.
     */
    fun getCredentials(appName: String): ProvisionedDatabase? {
        val normalized = appName.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
        return registry[normalized]
    }

    // ── Intern ──────────────────────────────────────────

    private fun getAdminConnection(): Connection {
        return DriverManager.getConnection(
            config.dbBrokerJdbcUrl,
            config.dbBrokerAdminUser,
            config.dbBrokerAdminPassword,
        )
    }

    private fun buildAppJdbcUrl(dbName: String): String {
        // Basis-URL nehmen und Datenbank anhaengen
        val base = config.dbBrokerJdbcUrl.trimEnd(';')
        return if (base.contains("database=", ignoreCase = true)) {
            base.replace(Regex("database=[^;]*", RegexOption.IGNORE_CASE), "database=$dbName")
        } else {
            "$base;database=$dbName"
        }
    }

    private fun generatePassword(length: Int): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#\$%"
        val random = SecureRandom()
        return (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    private fun loadRegistry() {
        if (!registryFile.exists()) return
        try {
            val json = JSONObject(registryFile.readText())
            val apps = json.optJSONArray("apps") ?: return
            for (i in 0 until apps.length()) {
                val app = apps.getJSONObject(i)
                val name = app.getString("appName")
                registry[name] = ProvisionedDatabase(
                    appName = name,
                    appVersion = app.optString("appVersion", ""),
                    database = app.getString("database"),
                    username = app.getString("username"),
                    password = app.getString("password"),
                    jdbcUrl = app.getString("jdbcUrl"),
                    provisionedAtMs = app.optLong("provisionedAtMs", 0),
                )
            }
            log.info("DB-Broker: ${registry.size} App(s) aus Registry geladen")
        } catch (e: Throwable) {
            log.warn("DB-Broker: Registry laden fehlgeschlagen: ${e.message}")
        }
    }

    private fun saveRegistry() {
        try {
            val apps = JSONArray()
            registry.values.forEach { db ->
                apps.put(JSONObject().apply {
                    put("appName", db.appName)
                    put("appVersion", db.appVersion)
                    put("database", db.database)
                    put("username", db.username)
                    put("password", db.password)
                    put("jdbcUrl", db.jdbcUrl)
                    put("provisionedAtMs", db.provisionedAtMs)
                })
            }
            registryFile.writeText(JSONObject().put("apps", apps).toString(2))
        } catch (e: Throwable) {
            log.warn("DB-Broker: Registry speichern fehlgeschlagen: ${e.message}")
        }
    }
}

data class ProvisionedDatabase(
    val appName: String,
    val appVersion: String,
    val database: String,
    val username: String,
    val password: String,
    val jdbcUrl: String,
    val provisionedAtMs: Long,
)

sealed class ProvisionResult {
    data class Success(val db: ProvisionedDatabase) : ProvisionResult()
    data class Error(val message: String) : ProvisionResult()
}

data class BrokerStatus(
    val enabled: Boolean,
    val serverUrl: String,
    val serverReachable: Boolean,
    val databases: List<ProvisionedDatabase>,
)
