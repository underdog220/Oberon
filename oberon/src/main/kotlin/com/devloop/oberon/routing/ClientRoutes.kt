package com.devloop.oberon.routing

import com.devloop.core.domain.enums.GatewayClientKind
import com.devloop.core.domain.model.GatewayClient
import com.devloop.oberon.service.OberonPlatformServices
import com.devloop.oberon.util.errorJson
import com.devloop.platform.auth.TokenAuthenticator
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun Route.clientRoutes(services: OberonPlatformServices) {

    route("/api/v2/clients") {

        get {
            val clients = services.repos.gatewayClients.getAll()
            val arr = JSONArray()
            clients.forEach { c ->
                arr.put(JSONObject()
                    .put("id", c.id).put("clientName", c.clientName)
                    .put("clientKind", c.clientKind.name).put("isActive", c.isActive)
                    .put("scopesJson", c.scopesJson).put("allowedDomains", c.allowedDomains)
                    .put("lastSeenAtEpochMillis", c.lastSeenAtEpochMillis ?: JSONObject.NULL))
            }
            call.respondText(JSONObject().put("clients", arr).toString(), ContentType.Application.Json)
        }

        post {
            val body = JSONObject(call.receiveText())
            val rawApiKey = UUID.randomUUID().toString()
            val client = GatewayClient(
                id = UUID.randomUUID().toString(),
                clientName = body.optString("clientName", ""),
                clientKind = try { GatewayClientKind.valueOf(body.optString("clientKind", "EXTERNAL")) } catch (_: Exception) { GatewayClientKind.EXTERNAL },
                apiKeyHash = TokenAuthenticator.sha256(rawApiKey),
                scopesJson = body.optString("scopesJson", "[\"*\"]"),
                permissionsJson = body.optString("permissionsJson", "{}"),
                allowedDomains = body.optString("allowedDomains", "[\"*\"]"),
                createdAtEpochMillis = System.currentTimeMillis(),
            )
            services.repos.gatewayClients.upsert(client)
            call.respondText(JSONObject()
                .put("id", client.id).put("clientName", client.clientName)
                .put("apiKey", rawApiKey)
                .put("note", "API-Key wird nur einmal angezeigt!")
                .toString(), ContentType.Application.Json, HttpStatusCode.Created)
        }
    }
}
