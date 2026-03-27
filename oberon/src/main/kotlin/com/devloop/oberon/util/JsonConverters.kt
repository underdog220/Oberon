package com.devloop.oberon.util

import com.devloop.core.domain.model.*
import org.json.JSONObject

fun instanceToJson(i: VirtualInstance) = JSONObject()
    .put("id", i.id).put("projectId", i.projectId).put("label", i.label)
    .put("instanceType", i.instanceType.name).put("status", i.status.name)
    .put("domain", i.domain).put("scopeJson", i.scopeJson)
    .put("createdAtEpochMillis", i.createdAtEpochMillis)
    .put("lastActiveAtEpochMillis", i.lastActiveAtEpochMillis)

fun messageToJson(m: ConversationMessage) = JSONObject()
    .put("id", m.id).put("role", m.role.name).put("content", m.content)
    .put("contentType", m.contentType.name).put("metadata", m.metadata)
    .put("domain", m.domain).put("createdAtEpochMillis", m.createdAtEpochMillis)

fun memoryToJson(e: MemoryEntry) = JSONObject()
    .put("id", e.id).put("memoryKind", e.memoryKind.name)
    .put("category", e.category).put("title", e.title).put("content", e.content)
    .put("relevanceScore", e.relevanceScore).put("domain", e.domain)
    .put("virtualInstanceId", e.virtualInstanceId ?: JSONObject.NULL)
    .put("projectId", e.projectId ?: JSONObject.NULL)
    .put("updatedAtEpochMillis", e.updatedAtEpochMillis)

fun resumeToJson(r: ResumeContext) = JSONObject()
    .put("found", true).put("virtualInstanceId", r.virtualInstanceId)
    .put("activityState", r.activityState.name).put("resumeHints", r.resumeHints)
    .put("updatedAtEpochMillis", r.updatedAtEpochMillis)

fun errorJson(msg: String) = JSONObject().put("error", msg)
