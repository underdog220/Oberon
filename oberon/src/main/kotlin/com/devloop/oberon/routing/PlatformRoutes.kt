package com.devloop.oberon.routing

import com.devloop.core.domain.enums.VirtualInstanceStatus
import com.devloop.oberon.service.OberonPlatformServices
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

fun Route.platformRoutes(services: OberonPlatformServices) {
    get("/api/v2/platform/status") {
        val instances = services.repos.instances.getAll()
        val clients = services.repos.gatewayClients.getAll()
        val syncState = services.dataSyncService.state.value
        call.respondText(
            JSONObject()
                .put("status", "ok")
                .put("server", "Oberon")
                .put("apiVersion", 2)
                .put("domains", JSONArray(services.config.domains))
                .put("virtualInstances", instances.size)
                .put("activeInstances", instances.count { it.status == VirtualInstanceStatus.ACTIVE })
                .put("registeredClients", clients.size)
                .put("syncMode", syncState.mode.name)
                .put("syncPending", syncState.pendingCount)
                .put("llmConfigured", services.llmService.isConfigured)
                .put("llmModel", services.llmService.activeModel)
                .put("timestamp", System.currentTimeMillis())
                .toString(),
            ContentType.Application.Json
        )
    }

    get("/api/v2/domains") {
        val arr = JSONArray()
        for (domain in services.config.domains) {
            val instances = services.repos.instances.getAll().count { it.domain == domain }
            arr.put(JSONObject().put("name", domain).put("instanceCount", instances))
        }
        call.respondText(JSONObject().put("domains", arr).toString(), ContentType.Application.Json)
    }
}
