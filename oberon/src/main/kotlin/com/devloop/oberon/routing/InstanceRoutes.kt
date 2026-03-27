package com.devloop.oberon.routing

import com.devloop.core.domain.enums.ActivityState
import com.devloop.core.domain.enums.ConversationRole
import com.devloop.oberon.service.OberonPlatformServices
import com.devloop.oberon.util.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.json.JSONArray
import org.json.JSONObject

fun Route.instanceRoutes(services: OberonPlatformServices) {

    route("/api/v2/instances") {

        get {
            val domain = call.request.queryParameters["domain"]
            val all = services.repos.instances.getAll()
            val filtered = if (domain != null) all.filter { it.domain == domain } else all
            val arr = JSONArray(); filtered.forEach { arr.put(instanceToJson(it)) }
            call.respondText(JSONObject().put("instances", arr).put("count", filtered.size).toString(), ContentType.Application.Json)
        }

        post {
            val body = JSONObject(call.receiveText())
            val projectId = body.optString("projectId", "")
            val label = body.optString("label", "")
            val type = body.optString("type", "TOPIC_FOCUS")
            val domain = body.optString("domain", "SYSTEM")
            if (projectId.isBlank() || label.isBlank()) {
                call.respondText(errorJson("projectId und label erforderlich").toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            }
            val id = when (type) {
                "PROJECT_FOCUS" -> services.instanceManager.ensureProjectFocus(projectId, label)
                "CROSS_PROJECT" -> services.instanceManager.createCrossProjectFocus(projectId, label)
                else -> services.instanceManager.createTopicFocus(projectId, label, body.optString("scopeJson", "{}"))
            }
            call.respondText(JSONObject().put("id", id).put("label", label).put("domain", domain).toString(), ContentType.Application.Json, HttpStatusCode.Created)
        }

        get("/{id}") {
            val id = call.parameters["id"]!!
            val instance = services.repos.instances.getById(id)
            if (instance == null) {
                call.respondText(errorJson("Instanz nicht gefunden").toString(), ContentType.Application.Json, HttpStatusCode.NotFound)
                return@get
            }
            call.respondText(instanceToJson(instance).toString(), ContentType.Application.Json)
        }

        get("/{id}/messages") {
            val id = call.parameters["id"]!!
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val msgs = services.repos.conversations.getRecentMessages(id, limit)
            val arr = JSONArray(); msgs.forEach { arr.put(messageToJson(it)) }
            call.respondText(JSONObject().put("messages", arr).put("count", msgs.size).toString(), ContentType.Application.Json)
        }

        post("/{id}/messages") {
            val id = call.parameters["id"]!!
            val body = JSONObject(call.receiveText())
            val content = body.optString("content", "")
            val role = body.optString("role", "USER")
            if (content.isBlank()) {
                call.respondText(errorJson("content erforderlich").toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            }
            services.conversationPersistence.persistMessage(id, ConversationRole.valueOf(role), content)
            call.respondText(JSONObject().put("status", "persisted").toString(), ContentType.Application.Json, HttpStatusCode.Created)
        }

        get("/{id}/context") {
            val id = call.parameters["id"]!!
            val ctx = services.contextBooster.assembleBoostContext(id)
            val formatted = services.contextBooster.formatForInjection(ctx)
            call.respondText(JSONObject()
                .put("virtualInstanceId", id)
                .put("stableKnowledgeCount", ctx.stableKnowledge.size)
                .put("decisionsCount", ctx.decisions.size)
                .put("recentMessagesCount", ctx.recentMessages.size)
                .put("hasResumeContext", ctx.resumeContext != null)
                .put("formattedContext", formatted)
                .toString(), ContentType.Application.Json)
        }

        get("/{id}/resume") {
            val id = call.parameters["id"]!!
            val resume = services.repos.resumeContexts.getForInstance(id)
            if (resume == null) {
                call.respondText(JSONObject().put("found", false).toString(), ContentType.Application.Json)
                return@get
            }
            call.respondText(resumeToJson(resume).toString(), ContentType.Application.Json)
        }

        post("/{id}/resume") {
            val id = call.parameters["id"]!!
            val body = JSONObject(call.receiveText())
            services.conversationPersistence.snapshotResumeState(
                virtualInstanceId = id,
                activityState = try { ActivityState.valueOf(body.optString("activityState", "IDLE")) } catch (_: Exception) { ActivityState.IDLE },
                resumeHints = body.optString("resumeHints", ""),
                contextJson = body.optString("contextJson", "{}"),
            )
            call.respondText(JSONObject().put("status", "updated").toString(), ContentType.Application.Json)
        }
    }
}
