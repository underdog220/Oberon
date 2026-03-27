package com.devloop.oberon.routing

import com.devloop.oberon.service.OberonPlatformServices
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.json.JSONArray
import org.json.JSONObject

fun Route.auditRoutes(services: OberonPlatformServices) {

    get("/api/v2/audit") {
        val q = call.request.queryParameters
        val limit = q["limit"]?.toIntOrNull() ?: 100
        val offset = q["offset"]?.toIntOrNull() ?: 0

        val entries = services.repos.auditLog.getRecent(limit, offset)
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject()
                .put("id", e.id).put("clientId", e.clientId ?: JSONObject.NULL)
                .put("action", e.action).put("resourcePath", e.resourcePath)
                .put("responseCode", e.responseCode).put("domain", e.domain)
                .put("durationMillis", e.durationMillis ?: JSONObject.NULL)
                .put("createdAtEpochMillis", e.createdAtEpochMillis))
        }
        call.respondText(JSONObject().put("entries", arr).put("count", entries.size).toString(), ContentType.Application.Json)
    }
}
