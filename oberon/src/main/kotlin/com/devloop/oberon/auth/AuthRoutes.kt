package com.devloop.oberon.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.json.JSONObject

/**
 * Auth-Endpoints (OHNE Auth-Interceptor — muessen vor dem Auth-Check erreichbar sein).
 *
 * POST /api/v2/auth/register     — App registrieren (Shared Secret oder Invite-Code)
 * POST /api/v2/auth/login        — User-Login (Username + Passwort)
 * POST /api/v2/auth/refresh      — App-Token erneuern
 * POST /api/v2/auth/invite       — Invite-Code generieren (nur Admin)
 * POST /api/v2/auth/user/create  — User anlegen (nur Admin)
 * POST /api/v2/auth/user/password — Passwort aendern
 * GET  /api/v2/auth/status       — Auth-Status (nur Admin)
 * GET  /api/v2/auth/verify       — Token pruefen
 */
fun Route.authRoutes(authService: OberonAuthService) {

    route("/api/v2/auth") {

        // App-Registrierung (kein Auth noetig — Shared Secret/Invite-Code ist die Auth)
        post("/register") {
            val body = JSONObject(call.receiveText())
            val appName = body.optString("appName", "")
            val secret = body.optString("registrationSecret", "").takeIf { it.isNotBlank() }
            val invite = body.optString("inviteCode", "").takeIf { it.isNotBlank() }
            val perms = body.optJSONArray("permissions")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: listOf("database", "llm")

            when (val result = authService.registerApp(appName, secret, invite, perms)) {
                is AuthResult.Success -> call.respondText(
                    JSONObject().apply {
                        put("status", "ok")
                        put("token", result.token)
                        put("subject", result.subject)
                        put("type", result.type)
                    }.toString(), ContentType.Application.Json,
                )
                is AuthResult.Error -> call.respondText(
                    JSONObject().put("error", result.message).toString(),
                    ContentType.Application.Json, HttpStatusCode.Unauthorized,
                )
            }
        }

        // User-Login (kein Auth noetig — Username/Passwort ist die Auth)
        post("/login") {
            val body = JSONObject(call.receiveText())
            val username = body.optString("username", "")
            val password = body.optString("password", "")

            when (val result = authService.loginUser(username, password)) {
                is AuthResult.Success -> call.respondText(
                    JSONObject().apply {
                        put("status", "ok")
                        put("token", result.token)
                        put("subject", result.subject)
                        put("type", result.type)
                    }.toString(), ContentType.Application.Json,
                )
                is AuthResult.Error -> call.respondText(
                    JSONObject().put("error", result.message).toString(),
                    ContentType.Application.Json, HttpStatusCode.Unauthorized,
                )
            }
        }

        // Token erneuern (braucht gueltiges Token)
        post("/refresh") {
            val body = JSONObject(call.receiveText())
            val appName = body.optString("appName", "")

            when (val result = authService.refreshAppToken(appName)) {
                is AuthResult.Success -> call.respondText(
                    JSONObject().apply {
                        put("status", "ok")
                        put("token", result.token)
                    }.toString(), ContentType.Application.Json,
                )
                is AuthResult.Error -> call.respondText(
                    JSONObject().put("error", result.message).toString(),
                    ContentType.Application.Json, HttpStatusCode.BadRequest,
                )
            }
        }

        // Token verifizieren
        get("/verify") {
            val token = extractToken(call)
            if (token == null) {
                call.respondText(
                    JSONObject().put("valid", false).toString(),
                    ContentType.Application.Json, HttpStatusCode.Unauthorized,
                )
                return@get
            }
            val claims = authService.verifyToken(token)
            if (claims != null) {
                call.respondText(
                    JSONObject().apply {
                        put("valid", true)
                        put("subject", claims.subject)
                        put("type", claims.type)
                        put("permissions", claims.permissions)
                    }.toString(), ContentType.Application.Json,
                )
            } else {
                call.respondText(
                    JSONObject().put("valid", false).toString(),
                    ContentType.Application.Json, HttpStatusCode.Unauthorized,
                )
            }
        }

        // Invite-Code generieren (braucht Admin-Rechte)
        post("/invite") {
            val token = extractToken(call)
            val claims = token?.let { authService.verifyToken(it) }
            if (claims == null || "admin" !in claims.permissions) {
                call.respondText(
                    JSONObject().put("error", "Admin-Rechte erforderlich").toString(),
                    ContentType.Application.Json, HttpStatusCode.Forbidden,
                )
                return@post
            }
            val body = JSONObject(call.receiveText())
            val minutes = body.optInt("validForMinutes", 10)
            val code = authService.generateInviteCode(minutes)
            call.respondText(
                JSONObject().put("code", code).put("validForMinutes", minutes).toString(),
                ContentType.Application.Json,
            )
        }

        // User anlegen (braucht Admin-Rechte)
        post("/user/create") {
            val token = extractToken(call)
            val claims = token?.let { authService.verifyToken(it) }
            if (claims == null || "admin" !in claims.permissions) {
                call.respondText(
                    JSONObject().put("error", "Admin-Rechte erforderlich").toString(),
                    ContentType.Application.Json, HttpStatusCode.Forbidden,
                )
                return@post
            }
            val body = JSONObject(call.receiveText())
            val username = body.optString("username", "")
            val password = body.optString("password", "")
            val perms = body.optJSONArray("permissions")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: listOf("database", "llm")

            if (authService.createUser(username, password, perms)) {
                call.respondText(
                    JSONObject().put("status", "created").put("username", username).toString(),
                    ContentType.Application.Json,
                )
            } else {
                call.respondText(
                    JSONObject().put("error", "User existiert bereits").toString(),
                    ContentType.Application.Json, HttpStatusCode.Conflict,
                )
            }
        }

        // Passwort aendern
        post("/user/password") {
            val token = extractToken(call)
            val claims = token?.let { authService.verifyToken(it) }
            if (claims == null) {
                call.respondText(
                    JSONObject().put("error", "Nicht authentifiziert").toString(),
                    ContentType.Application.Json, HttpStatusCode.Unauthorized,
                )
                return@post
            }
            val body = JSONObject(call.receiveText())
            val username = if ("admin" in claims.permissions) {
                body.optString("username", claims.subject)
            } else claims.subject
            val newPassword = body.optString("newPassword", "")

            if (authService.changePassword(username, newPassword)) {
                call.respondText(
                    JSONObject().put("status", "changed").toString(), ContentType.Application.Json,
                )
            } else {
                call.respondText(
                    JSONObject().put("error", "User nicht gefunden").toString(),
                    ContentType.Application.Json, HttpStatusCode.NotFound,
                )
            }
        }

        // Auth-Status (nur Admin)
        get("/status") {
            val token = extractToken(call)
            val claims = token?.let { authService.verifyToken(it) }
            if (claims == null || "admin" !in claims.permissions) {
                call.respondText(
                    JSONObject().put("error", "Admin-Rechte erforderlich").toString(),
                    ContentType.Application.Json, HttpStatusCode.Forbidden,
                )
                return@get
            }
            val status = authService.status()
            call.respondText(
                JSONObject().apply {
                    put("apps", status.registeredApps.map { app ->
                        JSONObject().put("appName", app.appName).put("permissions", app.permissions)
                    })
                    put("users", status.registeredUsers)
                    put("activeInviteCodes", status.activeInviteCodes)
                }.toString(), ContentType.Application.Json,
            )
        }
    }
}

private fun extractToken(call: ApplicationCall): String? {
    val auth = call.request.header(HttpHeaders.Authorization)
    if (auth != null && auth.startsWith("Bearer ", ignoreCase = true)) {
        return auth.substringAfter("Bearer ").trim()
    }
    return call.request.header("X-DevLoop-Token")?.trim()
}
