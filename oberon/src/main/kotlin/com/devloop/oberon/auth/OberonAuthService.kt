package com.devloop.oberon.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.devloop.oberon.OberonConfig
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Oberon Auth-Service: JWT-Token-Ausstellung und Verifizierung.
 *
 * Verwaltet drei Typen von Identitaeten:
 * - **Apps** (Machine-to-Machine): Registrierung per Shared Secret oder Invite-Code
 * - **User** (Browser-Login): Username + Passwort → JWT Session-Token
 * - **Legacy**: Statischer Token (Abwaertskompatibilitaet)
 *
 * JWT-Claims:
 * - `sub`: App-Name oder Username
 * - `type`: "app" oder "user"
 * - `permissions`: Liste von Rechten (database, llm, admin, ...)
 * - `exp`: Ablaufzeitpunkt
 */
class OberonAuthService(private val config: OberonConfig) {

    private val log = LoggerFactory.getLogger("OberonAuth")
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)
    private val verifier = JWT.require(algorithm).withIssuer("oberon").build()

    /** Registrierte Apps. */
    private val apps = ConcurrentHashMap<String, RegisteredApp>()

    /** Registrierte User (fuer Browser-Login). */
    private val users = ConcurrentHashMap<String, RegisteredUser>()

    /** Aktive Invite-Codes. */
    private val inviteCodes = ConcurrentHashMap<String, InviteCode>()

    private val registryFile = config.dataDir.resolve("auth-registry.json").toFile()

    init {
        loadRegistry()
        // Default-Admin-User anlegen falls keiner existiert
        if (users.isEmpty()) {
            val defaultHash = hashPassword("admin")
            users["admin"] = RegisteredUser(
                username = "admin",
                passwordHash = defaultHash,
                permissions = listOf("admin", "database", "llm", "instances", "audit"),
                createdAtMs = System.currentTimeMillis(),
            )
            saveRegistry()
            log.info("Auth: Default-Admin-User angelegt (admin/admin — bitte aendern!)")
        }
    }

    // ══════════ App-Registrierung ══════════

    /**
     * Registriert eine App per Shared Secret oder Invite-Code.
     * Gibt ein JWT-Token zurueck.
     */
    fun registerApp(
        appName: String,
        registrationSecret: String? = null,
        inviteCode: String? = null,
        permissions: List<String> = listOf("database", "llm"),
    ): AuthResult {
        val normalized = appName.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        if (normalized.isBlank()) return AuthResult.Error("Ungueltiger App-Name")

        // Authentifizierung: Shared Secret ODER Invite-Code
        val authorized = when {
            registrationSecret != null ->
                registrationSecret == config.registrationSecret
            inviteCode != null -> {
                val code = inviteCodes.remove(inviteCode)
                code != null && code.expiresAtMs > System.currentTimeMillis()
            }
            else -> false
        }

        if (!authorized) return AuthResult.Error("Ungueltige Credentials")

        // App registrieren (oder bestehende aktualisieren)
        val app = RegisteredApp(
            appName = normalized,
            permissions = permissions,
            createdAtMs = System.currentTimeMillis(),
        )
        apps[normalized] = app
        saveRegistry()

        val token = issueAppToken(normalized, permissions)
        log.info("Auth: App '$normalized' registriert (Permissions: $permissions)")
        return AuthResult.Success(token, normalized, "app")
    }

    /**
     * Erneuert das Token einer registrierten App.
     */
    fun refreshAppToken(appName: String): AuthResult {
        val app = apps[appName] ?: return AuthResult.Error("App '$appName' nicht registriert")
        val token = issueAppToken(app.appName, app.permissions)
        return AuthResult.Success(token, app.appName, "app")
    }

    // ══════════ User-Login (Browser) ══════════

    /**
     * Login per Username + Passwort. Gibt JWT Session-Token zurueck.
     */
    fun loginUser(username: String, password: String): AuthResult {
        val user = users[username] ?: return AuthResult.Error("Ungueltige Credentials")
        if (user.passwordHash != hashPassword(password)) {
            return AuthResult.Error("Ungueltige Credentials")
        }
        val token = issueUserToken(username, user.permissions)
        log.info("Auth: User '$username' eingeloggt")
        return AuthResult.Success(token, username, "user")
    }

    /**
     * Neuen User anlegen (nur Admin).
     */
    fun createUser(username: String, password: String, permissions: List<String>): Boolean {
        if (users.containsKey(username)) return false
        users[username] = RegisteredUser(
            username = username,
            passwordHash = hashPassword(password),
            permissions = permissions,
            createdAtMs = System.currentTimeMillis(),
        )
        saveRegistry()
        log.info("Auth: User '$username' angelegt (Permissions: $permissions)")
        return true
    }

    /**
     * Passwort aendern.
     */
    fun changePassword(username: String, newPassword: String): Boolean {
        val user = users[username] ?: return false
        users[username] = user.copy(passwordHash = hashPassword(newPassword))
        saveRegistry()
        return true
    }

    // ══════════ Invite-Codes ══════════

    /**
     * Generiert einen Einmal-Code fuer App-Registrierung.
     */
    fun generateInviteCode(validForMinutes: Int = 10): String {
        val code = buildString {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val random = SecureRandom()
            repeat(4) { append(chars[random.nextInt(chars.length)]) }
            append("-")
            repeat(4) { append(chars[random.nextInt(chars.length)]) }
        }
        inviteCodes[code] = InviteCode(
            code = code,
            expiresAtMs = System.currentTimeMillis() + validForMinutes * 60_000L,
        )
        log.info("Auth: Invite-Code generiert: $code (gueltig $validForMinutes Min.)")
        return code
    }

    // ══════════ Token-Verifizierung ══════════

    /**
     * Verifiziert ein JWT-Token und gibt die Claims zurueck.
     * Unterstuetzt auch den Legacy-Static-Token als Fallback.
     */
    fun verifyToken(token: String): TokenClaims? {
        // Legacy: statischer Token (Abwaertskompatibilitaet)
        if (token == config.token) {
            return TokenClaims(
                subject = "legacy",
                type = "legacy",
                permissions = listOf("admin", "database", "llm", "instances", "audit"),
            )
        }

        return try {
            val decoded = verifier.verify(token)
            TokenClaims(
                subject = decoded.subject,
                type = decoded.getClaim("type").asString() ?: "unknown",
                permissions = decoded.getClaim("permissions").asList(String::class.java) ?: emptyList(),
            )
        } catch (_: JWTVerificationException) {
            null
        }
    }

    // ══════════ Status ══════════

    fun status(): AuthStatus = AuthStatus(
        registeredApps = apps.values.toList(),
        registeredUsers = users.keys.toList(),
        activeInviteCodes = inviteCodes.size,
    )

    // ══════════ Intern ══════════

    private fun issueAppToken(appName: String, permissions: List<String>): String {
        val now = Date()
        val expiry = Date(now.time + config.jwtAppExpiryHours * 3_600_000L)
        return JWT.create()
            .withIssuer("oberon")
            .withSubject(appName)
            .withClaim("type", "app")
            .withClaim("permissions", permissions)
            .withIssuedAt(now)
            .withExpiresAt(expiry)
            .sign(algorithm)
    }

    private fun issueUserToken(username: String, permissions: List<String>): String {
        val now = Date()
        val expiry = Date(now.time + config.jwtUserExpiryHours * 3_600_000L)
        return JWT.create()
            .withIssuer("oberon")
            .withSubject(username)
            .withClaim("type", "user")
            .withClaim("permissions", permissions)
            .withIssuedAt(now)
            .withExpiresAt(expiry)
            .sign(algorithm)
    }

    private fun hashPassword(password: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hash = md.digest((password + config.jwtSecret).toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    // ══════════ Persistenz ══════════

    private fun loadRegistry() {
        if (!registryFile.exists()) return
        try {
            val json = JSONObject(registryFile.readText())
            json.optJSONArray("apps")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.getString("appName")
                    apps[name] = RegisteredApp(
                        appName = name,
                        permissions = o.optJSONArray("permissions")?.let { p ->
                            (0 until p.length()).map { p.getString(it) }
                        } ?: emptyList(),
                        createdAtMs = o.optLong("createdAtMs", 0),
                    )
                }
            }
            json.optJSONArray("users")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.getString("username")
                    users[name] = RegisteredUser(
                        username = name,
                        passwordHash = o.getString("passwordHash"),
                        permissions = o.optJSONArray("permissions")?.let { p ->
                            (0 until p.length()).map { p.getString(it) }
                        } ?: emptyList(),
                        createdAtMs = o.optLong("createdAtMs", 0),
                    )
                }
            }
            log.info("Auth: ${apps.size} App(s) + ${users.size} User geladen")
        } catch (e: Throwable) {
            log.warn("Auth: Registry laden fehlgeschlagen: ${e.message}")
        }
    }

    private fun saveRegistry() {
        try {
            val json = JSONObject()
            json.put("apps", JSONArray().apply {
                apps.values.forEach { app ->
                    put(JSONObject().apply {
                        put("appName", app.appName)
                        put("permissions", app.permissions)
                        put("createdAtMs", app.createdAtMs)
                    })
                }
            })
            json.put("users", JSONArray().apply {
                users.values.forEach { user ->
                    put(JSONObject().apply {
                        put("username", user.username)
                        put("passwordHash", user.passwordHash)
                        put("permissions", user.permissions)
                        put("createdAtMs", user.createdAtMs)
                    })
                }
            })
            registryFile.writeText(json.toString(2))
        } catch (e: Throwable) {
            log.warn("Auth: Registry speichern fehlgeschlagen: ${e.message}")
        }
    }
}

// ══════════ Datenmodelle ══════════

data class RegisteredApp(
    val appName: String,
    val permissions: List<String>,
    val createdAtMs: Long,
)

data class RegisteredUser(
    val username: String,
    val passwordHash: String,
    val permissions: List<String>,
    val createdAtMs: Long,
)

data class InviteCode(
    val code: String,
    val expiresAtMs: Long,
)

data class TokenClaims(
    val subject: String,
    val type: String,
    val permissions: List<String>,
)

sealed class AuthResult {
    data class Success(val token: String, val subject: String, val type: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

data class AuthStatus(
    val registeredApps: List<RegisteredApp>,
    val registeredUsers: List<String>,
    val activeInviteCodes: Int,
)
