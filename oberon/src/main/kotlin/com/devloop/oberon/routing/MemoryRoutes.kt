package com.devloop.oberon.routing

import com.devloop.core.domain.enums.MemoryKind
import com.devloop.core.domain.model.MemoryEntry
import com.devloop.oberon.service.OberonPlatformServices
import com.devloop.oberon.util.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun Route.memoryRoutes(services: OberonPlatformServices) {

    route("/api/v2/memory") {

        get {
            val q = call.request.queryParameters
            val instanceId = q["instanceId"]
            val projectId = q["projectId"]
            val kind = q["kind"]?.let { try { MemoryKind.valueOf(it) } catch (_: Exception) { null } }
            val search = q["q"]

            val entries = when {
                search != null -> services.repos.memory.search(search)
                instanceId != null -> services.repos.memory.getForInstance(instanceId, kind)
                projectId != null -> services.repos.memory.getForProject(projectId, kind)
                else -> services.repos.memory.getGlobal(kind)
            }
            val arr = JSONArray(); entries.forEach { arr.put(memoryToJson(it)) }
            call.respondText(JSONObject().put("entries", arr).put("count", entries.size).toString(), ContentType.Application.Json)
        }

        post {
            val body = JSONObject(call.receiveText())
            val now = System.currentTimeMillis()
            val entry = MemoryEntry(
                id = body.optString("id", UUID.randomUUID().toString()),
                virtualInstanceId = body.optString("virtualInstanceId", "").takeIf { it.isNotBlank() },
                projectId = body.optString("projectId", "").takeIf { it.isNotBlank() },
                memoryKind = try { MemoryKind.valueOf(body.optString("memoryKind", "STABLE_KNOWLEDGE")) } catch (_: Exception) { MemoryKind.STABLE_KNOWLEDGE },
                category = body.optString("category", ""),
                title = body.optString("title", ""),
                content = body.optString("content", ""),
                relevanceScore = body.optDouble("relevanceScore", 1.0),
                createdAtEpochMillis = body.optLong("createdAtEpochMillis", now),
                updatedAtEpochMillis = now,
                domain = body.optString("domain", "SYSTEM"),
            )
            services.repos.memory.upsert(entry)
            call.respondText(JSONObject().put("id", entry.id).put("status", "saved").toString(), ContentType.Application.Json, HttpStatusCode.Created)
        }

        delete("/{id}") {
            val id = call.parameters["id"]!!
            services.repos.memory.delete(id)
            call.respondText(JSONObject().put("deleted", id).toString(), ContentType.Application.Json)
        }
    }
}
