package com.devloop.oberon

import com.devloop.oberon.auth.OberonAuthService
import com.devloop.oberon.auth.authRoutes
import com.devloop.oberon.database.DatabaseBroker
import com.devloop.oberon.database.databaseRoutes
import com.devloop.oberon.discovery.OberonBeacon
import com.devloop.oberon.plananalyse.planRoutes
import com.devloop.oberon.routing.*
import com.devloop.oberon.service.OberonPlatformServices
import com.devloop.oberon.tls.OberonTlsSetup
import com.devloop.oberon.util.errorJson
import com.devloop.platform.auth.AuthResult
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("OberonServer")

/**
 * Oberon — Zentrale KI-Plattform als eigenstaendiger Headless-Server.
 *
 * Starten: java -jar oberon.jar
 * Konfiguration: ENV-Variablen (OBERON_PORT, OBERON_TOKEN, OBERON_DATA_DIR, ...)
 */
fun main() {
    val config = OberonConfig.load()
    log.info("=== Oberon Server ===")
    log.info("HTTP:  ${config.host}:${config.port}")
    log.info("HTTPS: ${if (config.tlsEnabled) "${config.host}:${config.httpsPort}" else "deaktiviert"}")
    log.info("Datenbank: ${config.dbPath}")
    log.info("Domaenen: ${config.domains}")
    log.info("SQL Server Sync: ${if (config.mssqlJdbcUrl != null) "aktiv" else "deaktiviert"}")

    val services = OberonPlatformServices.create(config)

    // Auth-Service (JWT-Token, User-Login, App-Registrierung)
    val authService = OberonAuthService(config)
    log.info("Auth-Service: aktiv (JWT, App-Registration, User-Login)")

    // Database-Broker (zentrale DB-Verwaltung)
    val dbBroker = if (config.dbBrokerEnabled && config.dbBrokerJdbcUrl.isNotBlank()) {
        log.info("Database-Broker: AKTIV (${config.dbBrokerJdbcUrl})")
        DatabaseBroker(config)
    } else {
        log.info("Database-Broker: deaktiviert (OBERON_DB_BROKER_ENABLED=false oder keine JDBC-URL)")
        null
    }

    // TLS-Zertifikat vorbereiten (self-signed, automatisch generiert)
    val tlsConfig = if (config.tlsEnabled) {
        OberonTlsSetup.ensureKeyStore(config.dataDir.toFile())
    } else null

    // HTTP-Server (immer aktiv — Fallback)
    val httpServer = embeddedServer(Netty, port = config.port, host = config.host) {
        configurePlugins()
        configureRouting(services, dbBroker, authService)
    }

    // HTTPS-Server (parallel auf separatem Port, wenn TLS verfuegbar)
    if (tlsConfig != null && config.httpsPort > 0) {
        try {
            val ks = java.security.KeyStore.getInstance("JKS").apply {
                load(tlsConfig.keystoreFile.inputStream(), tlsConfig.keystorePassword.toCharArray())
            }
            val kmf = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, tlsConfig.keyPassword.toCharArray())
            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
            sslContext.init(kmf.keyManagers, null, null)

            // Separater HTTPS-Server auf eigenem Port
            // (Gleiche Konfiguration wie HTTP — Clients koennen waehlen)
            val appModule: Application.() -> Unit = {
                configurePlugins()
                configureRouting(services, dbBroker, authService)
            }
            val httpsServer = embeddedServer(Netty, port = config.httpsPort, host = config.host, module = appModule)
            httpsServer.start(wait = false)
            log.info("HTTPS aktiviert auf Port ${config.httpsPort} (Self-Signed)")
            log.info("Hinweis: Browser zeigt Warnung bei Self-Signed-Zertifikaten — das ist normal.")
        } catch (e: Throwable) {
            log.warn("HTTPS-Setup fehlgeschlagen: ${e.message}")
            log.info("Nur HTTP auf Port ${config.port} verfuegbar")
        }
    } else {
        log.info("HTTPS deaktiviert — nur HTTP auf Port ${config.port}")
    }

    // Discovery-Beacon starten (UDP-Broadcast, damit Clients den Server finden)
    val beacon = OberonBeacon(
        serverPort = config.port,
        httpsPort = if (config.tlsEnabled) config.httpsPort else 0,
        token = config.token,
        domains = config.domains,
    )
    beacon.start()
    log.info("Discovery-Beacon aktiv auf UDP-Port ${OberonBeacon.DEFAULT_BEACON_PORT}")

    httpServer.start(wait = true)
}

private fun Application.configurePlugins() {
    install(DefaultHeaders) {
        header("X-Oberon-Version", "1.0")
        header("X-Powered-By", "Oberon/DevLoop")
    }
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            this@configurePlugins.log.error("Unbehandelte Ausnahme: ${cause.message}", cause)
            call.respondText(
                errorJson("Internal Server Error: ${cause.message}").toString(),
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError,
            )
        }
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-DevLoop-Token")
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Put)
    }
}

private fun Application.configureRouting(
    services: OberonPlatformServices,
    dbBroker: DatabaseBroker? = null,
    authService: OberonAuthService? = null,
) {
    // Auth-Interceptor fuer /api/v2/* Pfade (global)
    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.path()
        if (!path.startsWith("/api/v2")) return@intercept // Health etc. ohne Auth
        // Auth-Endpoints brauchen keinen Token (dort wird der Token erst erstellt)
        if (path.startsWith("/api/v2/auth/register") ||
            path.startsWith("/api/v2/auth/login")
        ) return@intercept

        val auth = call.request.header(HttpHeaders.Authorization)
        val token = call.request.header("X-DevLoop-Token")
        val bearer = when {
            auth != null && auth.startsWith("Bearer ", ignoreCase = true) -> auth.substringAfter("Bearer ").trim()
            token != null -> token.trim()
            else -> {
                call.respondText(errorJson("Kein Auth-Token").toString(), ContentType.Application.Json, HttpStatusCode.Unauthorized)
                finish()
                return@intercept
            }
        }

        // JWT-Verifizierung (mit Legacy-Token-Fallback)
        if (authService != null) {
            val claims = authService.verifyToken(bearer)
            if (claims == null) {
                call.respondText(errorJson("Ungueltiges oder abgelaufenes Token").toString(), ContentType.Application.Json, HttpStatusCode.Unauthorized)
                finish()
                return@intercept
            }
            // Claims fuer nachfolgende Handler bereitstellen
            call.attributes.put(TokenClaimsKey, claims)
        } else {
            // Fallback: alter statischer Token-Check
            val result = services.tokenAuth.authenticate(bearer)
            if (result is com.devloop.platform.auth.AuthResult.Denied) {
                call.respondText(errorJson(result.reason).toString(), ContentType.Application.Json, HttpStatusCode.Unauthorized)
                finish()
                return@intercept
            }
            val client = (result as com.devloop.platform.auth.AuthResult.Authenticated).client
            if (client != null) {
                call.attributes.put(ClientAttributeKey, client)
            }
        }
    }

    routing {
        get("/api/health") { call.respondText("ok") }

        if (authService != null) authRoutes(authService)

        platformRoutes(services)
        instanceRoutes(services)
        chatRoutes(services)
        memoryRoutes(services)
        clientRoutes(services)
        auditRoutes(services)
        adminRoutes(services)
        dsgvoRoutes(services)
        planRoutes(services)
        databaseRoutes(dbBroker)

        // Admin-UI: statische Dateien aus resources/admin/
        get("/admin/{path...}") {
            val path = call.parameters.getAll("path")?.joinToString("/") ?: "index.html"
            val resource = this::class.java.classLoader.getResource("admin/$path")
            if (resource != null) {
                val contentType = when {
                    path.endsWith(".html") -> ContentType.Text.Html
                    path.endsWith(".js") -> ContentType.Application.JavaScript
                    path.endsWith(".css") -> ContentType.Text.CSS
                    path.endsWith(".json") -> ContentType.Application.Json
                    else -> ContentType.Application.OctetStream
                }
                call.respondText(resource.readText(), contentType)
            } else {
                // Fallback auf index.html (SPA-Routing)
                val index = this::class.java.classLoader.getResource("admin/index.html")
                if (index != null) call.respondText(index.readText(), ContentType.Text.Html)
                else call.respondText("Not Found", ContentType.Text.Plain, HttpStatusCode.NotFound)
            }
        }
        get("/admin") { call.respondRedirect("/admin/index.html") }
    }
}

/** Ktor AttributeKey fuer den authentifizierten Client (Legacy). */
val ClientAttributeKey = io.ktor.util.AttributeKey<com.devloop.core.domain.model.GatewayClient>("oberonClient")

/** Ktor AttributeKey fuer JWT-Token-Claims. */
val TokenClaimsKey = io.ktor.util.AttributeKey<com.devloop.oberon.auth.TokenClaims>("tokenClaims")
