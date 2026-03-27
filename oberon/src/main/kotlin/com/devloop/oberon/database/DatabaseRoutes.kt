package com.devloop.oberon.database

import com.devloop.oberon.service.OberonPlatformServices
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.json.JSONObject

/**
 * REST-Endpoints fuer den Database-Broker.
 *
 * POST /api/v2/database/provision   — DB fuer App erstellen/abrufen
 * GET  /api/v2/database/status      — Status aller Datenbanken
 * POST /api/v2/database/rotate      — Credentials rotieren
 * GET  /api/v2/database/credentials — Credentials einer App abrufen
 */
fun Route.databaseRoutes(broker: DatabaseBroker?) {
    route("/api/v2/database") {

        // DB fuer App provisionieren (oder bestehende Credentials zurueckgeben)
        post("/provision") {
            if (broker == null) {
                call.respondText(
                    JSONObject().put("error", "Database-Broker nicht aktiviert").toString(),
                    ContentType.Application.Json, HttpStatusCode.ServiceUnavailable,
                )
                return@post
            }

            val body = JSONObject(call.receiveText())
            val appName = body.optString("appName", "").trim()
            val appVersion = body.optString("appVersion", "")

            if (appName.isBlank()) {
                call.respondText(
                    JSONObject().put("error", "appName fehlt").toString(),
                    ContentType.Application.Json, HttpStatusCode.BadRequest,
                )
                return@post
            }

            when (val result = broker.provision(appName, appVersion)) {
                is ProvisionResult.Success -> {
                    val db = result.db
                    call.respondText(
                        JSONObject().apply {
                            put("status", "ok")
                            put("appName", db.appName)
                            put("database", db.database)
                            put("username", db.username)
                            put("password", db.password)
                            put("jdbcUrl", db.jdbcUrl)
                            put("provisionedAt", db.provisionedAtMs)
                        }.toString(),
                        ContentType.Application.Json,
                    )
                }
                is ProvisionResult.Error -> {
                    call.respondText(
                        JSONObject().put("error", result.message).toString(),
                        ContentType.Application.Json, HttpStatusCode.InternalServerError,
                    )
                }
            }
        }

        // Status aller Datenbanken
        get("/status") {
            if (broker == null) {
                call.respondText(
                    JSONObject().put("enabled", false).toString(),
                    ContentType.Application.Json,
                )
                return@get
            }

            val status = broker.status()
            call.respondText(
                JSONObject().apply {
                    put("enabled", status.enabled)
                    put("serverUrl", status.serverUrl)
                    put("serverReachable", status.serverReachable)
                    put("databaseCount", status.databases.size)
                    put("databases", status.databases.map { db ->
                        JSONObject().apply {
                            put("appName", db.appName)
                            put("database", db.database)
                            put("username", db.username)
                            // Passwort NICHT im Status anzeigen
                            put("provisionedAt", db.provisionedAtMs)
                        }
                    })
                }.toString(),
                ContentType.Application.Json,
            )
        }

        // Credentials rotieren
        post("/rotate") {
            if (broker == null) {
                call.respondText(
                    JSONObject().put("error", "Database-Broker nicht aktiviert").toString(),
                    ContentType.Application.Json, HttpStatusCode.ServiceUnavailable,
                )
                return@post
            }

            val body = JSONObject(call.receiveText())
            val appName = body.optString("appName", "").trim()

            when (val result = broker.rotateCredentials(appName)) {
                is ProvisionResult.Success -> {
                    val db = result.db
                    call.respondText(
                        JSONObject().apply {
                            put("status", "rotated")
                            put("appName", db.appName)
                            put("username", db.username)
                            put("password", db.password)
                        }.toString(),
                        ContentType.Application.Json,
                    )
                }
                is ProvisionResult.Error -> {
                    call.respondText(
                        JSONObject().put("error", result.message).toString(),
                        ContentType.Application.Json, HttpStatusCode.BadRequest,
                    )
                }
            }
        }

        // Credentials einer App abrufen
        get("/credentials/{appName}") {
            if (broker == null) {
                call.respondText(
                    JSONObject().put("error", "Database-Broker nicht aktiviert").toString(),
                    ContentType.Application.Json, HttpStatusCode.ServiceUnavailable,
                )
                return@get
            }

            val appName = call.parameters["appName"] ?: ""
            val db = broker.getCredentials(appName)

            if (db == null) {
                call.respondText(
                    JSONObject().put("error", "App '$appName' nicht registriert").toString(),
                    ContentType.Application.Json, HttpStatusCode.NotFound,
                )
                return@get
            }

            call.respondText(
                JSONObject().apply {
                    put("appName", db.appName)
                    put("database", db.database)
                    put("username", db.username)
                    put("password", db.password)
                    put("jdbcUrl", db.jdbcUrl)
                }.toString(),
                ContentType.Application.Json,
            )
        }
    }
}
