package com.devloop.oberon.routing

import com.devloop.oberon.dsgvo.*
import com.devloop.oberon.llm.LlmMessage
import com.devloop.oberon.service.OberonPlatformServices
import com.devloop.oberon.util.errorJson
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/**
 * DSGVO-Endpoints: Anonymisierung, De-Anonymisierung, Proxy-Modus und Reporting.
 *
 * Drei Betriebsmodi:
 * 1. Proxy — Oberon anonymisiert, ruft LLM auf, de-anonymisiert (alles serverseitig)
 * 2a. Sanitize — Client erhaelt anonymisierten Text und ruft LLM selbst auf
 * 2b. Deanonymize — Client sendet LLM-Antwort zurueck zur De-Anonymisierung
 */
fun Route.dsgvoRoutes(services: OberonPlatformServices) {

    // ══════════════════════════════════════════════════════════════════════
    // Modus 1 — Proxy: Oberon anonymisiert, ruft LLM auf, de-anonymisiert
    // ══════════════════════════════════════════════════════════════════════

    post("/api/v2/dsgvo/proxy") {
        val startTime = System.currentTimeMillis()
        val body = JSONObject(call.receiveText())

        val clientId = body.optString("clientId", "")
        val domain = body.optString("domain", "")
        val prompt = body.optString("prompt", "")
        val systemPrompt = body.optString("systemPrompt", "")
        val model = body.optString("model", "")
        val maxTokens = body.optInt("maxTokens", 2000)
        val workspaceId = body.optString("workspaceId", "").takeIf { it.isNotBlank() }

        // Pflichtfelder pruefen
        if (clientId.isBlank()) {
            call.respondText(errorJson("clientId erforderlich").toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@post
        }
        if (prompt.isBlank()) {
            call.respondText(errorJson("prompt erforderlich").toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@post
        }
        if (!services.llmService.isConfigured) {
            call.respondText(
                errorJson("LLM nicht konfiguriert — API-Key setzen per PUT /api/v2/admin/llm-config oder ENV OBERON_OPENAI_API_KEY").toString(),
                ContentType.Application.Json, HttpStatusCode.ServiceUnavailable,
            )
            return@post
        }

        val sessionId = workspaceId ?: UUID.randomUUID().toString()
        val auditId = UUID.randomUUID().toString()

        try {
            // 1. Prompt scannen und anonymisieren
            val promptResult = services.dsgvoService.processText(prompt, clientId, domain, sessionId)
            val piiFound = promptResult.piiFound
            val piiTypes = promptResult.piiTypes

            // 2. System-Prompt ebenfalls anonymisieren (falls vorhanden)
            val anonymizedSystemPrompt = if (systemPrompt.isNotBlank() && piiFound) {
                services.dsgvoService.processText(systemPrompt, clientId, domain, sessionId).anonymizedText
            } else {
                systemPrompt
            }

            // 3. LLM-Messages zusammenbauen (anonymisiert oder original)
            val llmMessages = mutableListOf<LlmMessage>()
            if (anonymizedSystemPrompt.isNotBlank()) {
                llmMessages += LlmMessage("system", anonymizedSystemPrompt)
            }
            val promptFuerLlm = if (piiFound) promptResult.anonymizedText else prompt
            llmMessages += LlmMessage("user", promptFuerLlm)

            // 4. LLM aufrufen
            val llmResult = services.llmService.chatCompletion(llmMessages)

            llmResult.fold(
                onSuccess = { llmAntwort ->
                    // 5. Antwort de-anonymisieren (falls PII gefunden war)
                    val endAntwort = if (piiFound) {
                        services.dsgvoService.deanonymize(llmAntwort, sessionId)
                    } else {
                        llmAntwort
                    }

                    val durationMs = System.currentTimeMillis() - startTime

                    // 6. Audit-Event loggen
                    services.dsgvoService.auditLogger.log(
                        DsgvoAuditEvent(
                            id = auditId,
                            clientId = clientId,
                            domain = domain,
                            piiFound = piiFound,
                            piiTypes = piiTypes,
                            anonymized = piiFound,
                            routingDecision = RoutingDecision.PROXY,
                            processingDurationMs = durationMs,
                            resultStatus = "ok",
                            mappingId = sessionId,
                        )
                    )

                    // 7. Antwort zurueckgeben
                    val response = JSONObject()
                        .put("status", "ok")
                        .put("response", endAntwort)
                        .put("piiFound", piiFound)
                        .put("piiTypes", JSONArray(piiTypes.map { it.name }))
                        .put("anonymized", piiFound)
                        .put("routingDecision", "PROXY")
                        .put("auditId", auditId)
                        .put("durationMs", durationMs)

                    call.respondText(response.toString(), ContentType.Application.Json)
                },
                onFailure = { error ->
                    call.respondText(
                        JSONObject()
                            .put("status", "error")
                            .put("error", error.message ?: "LLM-Fehler im Proxy-Modus")
                            .put("auditId", auditId)
                            .toString(),
                        ContentType.Application.Json, HttpStatusCode.BadGateway,
                    )
                },
            )
        } catch (e: Throwable) {
            call.respondText(
                errorJson("DSGVO-Proxy-Fehler: ${e.message}").toString(),
                ContentType.Application.Json, HttpStatusCode.InternalServerError,
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Modus 2a — Sanitize: Client erhaelt anonymisierten Text
    // ══════════════════════════════════════════════════════════════════════

    post("/api/v2/dsgvo/sanitize") {
        val body = JSONObject(call.receiveText())

        val clientId = body.optString("clientId", "")
        val domain = body.optString("domain", "")
        val prompt = body.optString("prompt", "")
        val systemPrompt = body.optString("systemPrompt", "")

        if (clientId.isBlank()) {
            call.respondText(errorJson("clientId erforderlich").toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@post
        }
        if (prompt.isBlank()) {
            call.respondText(errorJson("prompt erforderlich").toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@post
        }

        try {
            val sessionId = UUID.randomUUID().toString()
            val auditId = UUID.randomUUID().toString()

            // Prompt anonymisieren
            val promptResult = services.dsgvoService.processText(prompt, clientId, domain, sessionId)

            // System-Prompt anonymisieren (falls vorhanden)
            val sanitizedSystemPrompt = if (systemPrompt.isNotBlank()) {
                services.dsgvoService.processText(systemPrompt, clientId, domain, sessionId).anonymizedText
            } else {
                ""
            }

            // Audit-Event loggen
            services.dsgvoService.auditLogger.log(
                DsgvoAuditEvent(
                    id = auditId,
                    clientId = clientId,
                    domain = domain,
                    piiFound = promptResult.piiFound,
                    piiTypes = promptResult.piiTypes,
                    anonymized = promptResult.piiFound,
                    routingDecision = RoutingDecision.CORRECTOR,
                    processingDurationMs = 0,
                    resultStatus = "ok",
                    mappingId = sessionId,
                )
            )

            val response = JSONObject()
                .put("status", "ok")
                .put("sanitizedPrompt", promptResult.anonymizedText)
                .put("sanitizedSystemPrompt", sanitizedSystemPrompt)
                .put("mappingId", sessionId)
                .put("piiFound", promptResult.piiFound)
                .put("piiTypes", JSONArray(promptResult.piiTypes))
                .put("auditId", auditId)

            call.respondText(response.toString(), ContentType.Application.Json)
        } catch (e: Throwable) {
            call.respondText(
                errorJson("DSGVO-Sanitize-Fehler: ${e.message}").toString(),
                ContentType.Application.Json, HttpStatusCode.InternalServerError,
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Modus 2b — De-Anonymisierung: Platzhalter zurueck in Originale
    // ══════════════════════════════════════════════════════════════════════

    post("/api/v2/dsgvo/deanonymize") {
        val body = JSONObject(call.receiveText())

        val mappingId = body.optString("mappingId", "")
        val responseText = body.optString("response", "")

        if (mappingId.isBlank()) {
            call.respondText(errorJson("mappingId erforderlich").toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@post
        }
        if (responseText.isBlank()) {
            call.respondText(errorJson("response erforderlich").toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@post
        }

        try {
            val auditId = UUID.randomUUID().toString()

            // De-Anonymisierung durchfuehren
            val deanonymized = services.dsgvoService.deanonymize(responseText, mappingId)

            // Audit-Event loggen
            services.dsgvoService.auditLogger.log(
                DsgvoAuditEvent(
                    id = auditId,
                    clientId = "",
                    domain = "",
                    piiFound = true,
                    piiTypes = emptyList(),
                    anonymized = false,
                    routingDecision = RoutingDecision.CORRECTOR,
                    processingDurationMs = 0,
                    resultStatus = "ok",
                    mappingId = mappingId,
                )
            )

            val response = JSONObject()
                .put("status", "ok")
                .put("deanonymizedResponse", deanonymized)
                .put("auditId", auditId)

            call.respondText(response.toString(), ContentType.Application.Json)
        } catch (e: Throwable) {
            call.respondText(
                errorJson("DSGVO-De-Anonymisierung-Fehler: ${e.message}").toString(),
                ContentType.Application.Json, HttpStatusCode.InternalServerError,
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Tagesbericht: Zusammenfassung der DSGVO-Aktivitaeten
    // ══════════════════════════════════════════════════════════════════════

    get("/api/v2/dsgvo/report") {
        try {
            val dateParam = call.request.queryParameters["date"]
            val date = if (dateParam != null) {
                LocalDate.parse(dateParam)
            } else {
                LocalDate.now()
            }

            val bericht = services.dsgvoService.dailyReport(date)

            // DsgvoTagesbericht als JSON serialisieren
            val response = JSONObject()
                .put("status", "ok")
                .put("date", date.toString())
                .put("report", bericht.toJson())

            call.respondText(response.toString(), ContentType.Application.Json)
        } catch (e: java.time.format.DateTimeParseException) {
            call.respondText(
                errorJson("Ungueltiges Datumsformat — erwartet: YYYY-MM-DD").toString(),
                ContentType.Application.Json, HttpStatusCode.BadRequest,
            )
        } catch (e: Throwable) {
            call.respondText(
                errorJson("DSGVO-Report-Fehler: ${e.message}").toString(),
                ContentType.Application.Json, HttpStatusCode.InternalServerError,
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Status: Aktueller DSGVO-System-Status
    // ══════════════════════════════════════════════════════════════════════

    get("/api/v2/dsgvo/status") {
        try {
            val heute = LocalDate.now()
            val bericht = services.dsgvoService.dailyReport(heute)

            val response = JSONObject()
                .put("dsgvoEnabled", true)
                .put("activeSessions", bericht.activeSessions)
                .put("todayRequests", bericht.totalRequests)
                .put("todayPiiFound", bericht.piiFoundCount)
                .put("todayAnonymized", bericht.anonymizedCount)

            call.respondText(response.toString(), ContentType.Application.Json)
        } catch (e: Throwable) {
            call.respondText(
                errorJson("DSGVO-Status-Fehler: ${e.message}").toString(),
                ContentType.Application.Json, HttpStatusCode.InternalServerError,
            )
        }
    }
}

