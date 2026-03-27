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
 * Database-Broker: Verwaltet PostgreSQL-Datenbanken fuer alle Apps zentral.
 *
 * Apps registrieren sich bei Oberon und bekommen automatisch:
 * - Eine eigene Datenbank (isoliert)
 * - Einen eigenen DB-User mit eingeschraenkten Rechten
 * - JDBC-Zugangsdaten
 *
 * Oberon ist der einzige mit Admin-Zugang zum PostgreSQL-Server.
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

        // Schon registriert? → aktuelle URL zurueckgeben (dynamisch)
        val existing = registry[normalized]
        if (existing != null) {
            log.info("DB-Broker: App '$normalized' bereits registriert (DB: ${existing.database})")
            return ProvisionResult.Success(existing.copy(jdbcUrl = buildAppJdbcUrl(existing.database)))
        }

        if (!config.dbBrokerEnabled || config.dbBrokerJdbcUrl.isBlank()) {
            return ProvisionResult.Error("Database-Broker nicht konfiguriert (OBERON_DB_BROKER_JDBC_URL fehlt)")
        }

        val dbName = "${config.dbBrokerDatabasePrefix}$normalized"
        val dbUser = "${normalized}_svc"
        val dbPassword = generatePassword(24)

        log.info("DB-Broker: Provisioniere DB '$dbName' fuer App '$normalized'...")

        try {
            // PostgreSQL: Datenbank und User erstellen
            // (Muss auf der 'postgres' Default-DB ausgefuehrt werden)
            getAdminConnection().use { conn ->
                conn.autoCommit = true

                // 1. User erstellen (falls nicht vorhanden)
                val userExists = conn.prepareStatement(
                    "SELECT 1 FROM pg_roles WHERE rolname = ?"
                ).use { ps ->
                    ps.setString(1, dbUser)
                    ps.executeQuery().next()
                }
                if (!userExists) {
                    conn.createStatement().execute(
                        "CREATE ROLE \"$dbUser\" WITH LOGIN PASSWORD '$dbPassword'"
                    )
                    log.info("DB-Broker: User '$dbUser' erstellt")
                } else {
                    conn.createStatement().execute(
                        "ALTER ROLE \"$dbUser\" WITH PASSWORD '$dbPassword'"
                    )
                    log.info("DB-Broker: User '$dbUser' existiert, Passwort aktualisiert")
                }

                // 2. Datenbank erstellen (falls nicht vorhanden)
                val dbExists = conn.prepareStatement(
                    "SELECT 1 FROM pg_database WHERE datname = ?"
                ).use { ps ->
                    ps.setString(1, dbName)
                    ps.executeQuery().next()
                }
                if (!dbExists) {
                    conn.createStatement().execute(
                        "CREATE DATABASE \"$dbName\" OWNER \"$dbUser\""
                    )
                    log.info("DB-Broker: Datenbank '$dbName' erstellt")
                } else {
                    log.info("DB-Broker: Datenbank '$dbName' existiert bereits")
                }

                // 3. Rechte setzen
                conn.createStatement().execute(
                    "GRANT ALL PRIVILEGES ON DATABASE \"$dbName\" TO \"$dbUser\""
                )
            }

            // 4. Schema-Rechte in der neuen DB setzen
            getConnection(dbName).use { conn ->
                conn.autoCommit = true
                conn.createStatement().execute(
                    "GRANT ALL ON SCHEMA public TO \"$dbUser\""
                )
                conn.createStatement().execute(
                    "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO \"$dbUser\""
                )
                conn.createStatement().execute(
                    "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO \"$dbUser\""
                )
            }

        } catch (e: Throwable) {
            log.error("DB-Broker: Provisionierung fehlgeschlagen: ${e.message}")
            return ProvisionResult.Error("PostgreSQL-Fehler: ${e.message}")
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

        log.info("DB-Broker: App '$normalized' provisioniert → $dbName ($appJdbcUrl)")
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
                conn.autoCommit = true
                conn.createStatement().execute(
                    "ALTER ROLE \"${existing.username}\" WITH PASSWORD '$newPassword'"
                )
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
     * Die JDBC-URL wird IMMER aus der aktuellen Config berechnet —
     * wenn die DB umzieht, reicht es Oberon umzukonfigurieren.
     */
    fun getCredentials(appName: String): ProvisionedDatabase? {
        val normalized = appName.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val entry = registry[normalized] ?: return null
        // JDBC-URL dynamisch — nicht die gespeicherte, sondern die aktuelle
        return entry.copy(jdbcUrl = buildAppJdbcUrl(entry.database))
    }

    // ── Intern ──────────────────────────────────────────

    /** Verbindung zur Admin-DB (postgres). */
    private fun getAdminConnection(): Connection {
        return DriverManager.getConnection(
            config.dbBrokerJdbcUrl,
            config.dbBrokerAdminUser,
            config.dbBrokerAdminPassword,
        )
    }

    /** Verbindung zu einer spezifischen DB. */
    private fun getConnection(dbName: String): Connection {
        val baseUrl = config.dbBrokerJdbcUrl.replace(Regex("/[^/]*$"), "/$dbName")
        return DriverManager.getConnection(
            baseUrl,
            config.dbBrokerAdminUser,
            config.dbBrokerAdminPassword,
        )
    }

    private fun buildAppJdbcUrl(dbName: String): String {
        return config.dbBrokerJdbcUrl.replace(Regex("/[^/]*$"), "/$dbName")
    }

    private fun generatePassword(length: Int): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
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
