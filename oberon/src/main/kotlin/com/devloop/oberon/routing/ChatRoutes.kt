package com.devloop.oberon.routing

import com.devloop.core.domain.enums.ConversationRole
import com.devloop.oberon.llm.LlmMessage
import com.devloop.oberon.service.OberonPlatformServices
import com.devloop.oberon.util.errorJson
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.json.JSONObject

/**
 * Chat-Endpoint: Der Kern der zentralen KI-Plattform.
 *
 * POST /api/v2/instances/{id}/chat
 * → Assembliert Context Booster
 * → Laedt persistierte Konversation
 * → Ruft LLM auf
 * → Persistiert User-Nachricht + Antwort
 * → Gibt Antwort zurueck
 */
fun Route.chatRoutes(services: OberonPlatformServices) {

    post("/api/v2/instances/{id}/chat") {
        val instanceId = call.parameters["id"]!!
        val body = JSONObject(call.receiveText())
        val userMessage = body.optString("message", "")

        if (userMessage.isBlank()) {
            call.respondText(errorJson("message erforderlich").toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@post
        }

        // 1. Pruefen ob Instanz existiert
        val instance = services.repos.instances.getById(instanceId)
        if (instance == null) {
            call.respondText(errorJson("Instanz nicht gefunden").toString(), ContentType.Application.Json, HttpStatusCode.NotFound)
            return@post
        }

        // 2. Pruefen ob LLM konfiguriert
        if (!services.llmService.isConfigured) {
            call.respondText(
                errorJson("LLM nicht konfiguriert — API-Key setzen per PUT /api/v2/admin/llm-config oder ENV OBERON_OPENAI_API_KEY").toString(),
                ContentType.Application.Json, HttpStatusCode.ServiceUnavailable,
            )
            return@post
        }

        // 3. User-Nachricht persistieren
        services.conversationPersistence.persistMessage(instanceId, ConversationRole.USER, userMessage)

        // 4. Context Booster assemblieren
        val boostCtx = services.contextBooster.assembleBoostContext(instanceId)
        val boostText = services.contextBooster.formatForInjection(boostCtx)

        // 5. Letzte Konversation laden
        val recentMessages = services.repos.conversations.getRecentMessages(instanceId, 20)

        // 6. LLM-Messages zusammenbauen
        val llmMessages = mutableListOf<LlmMessage>()

        // System-Preamble: Harter Supervisor-Prompt + Projekt-Kontext
        val projectHint = body.optString("projectHint", "").takeIf { it.isNotBlank() }
        val systemPrompt = buildString {
            // Verbindlicher Supervisor-Prompt (nicht aus Memory — direkt als System-Message)
            appendLine(SUPERVISOR_SYSTEM_PROMPT)
            appendLine()
            appendLine("[AKTIVER_KONTEXT]")
            appendLine("Domaene: ${instance.domain}")
            appendLine("Projekt/Fokusraum: ${instance.label}")
            if (projectHint != null) {
                appendLine("Projektdetails: $projectHint")
            }
            appendLine("[/AKTIVER_KONTEXT]")
            // Context Booster (Memory-Eintraege, Resume-Kontext)
            if (boostText.isNotBlank()) {
                appendLine()
                appendLine(boostText)
            }
        }
        llmMessages += LlmMessage("system", systemPrompt)

        // Historische Nachrichten
        for (msg in recentMessages) {
            // Die letzte User-Nachricht nicht doppelt einfuegen (wurde gerade persistiert)
            if (msg.content == userMessage && msg.role == ConversationRole.USER) continue
            val role = when (msg.role) {
                ConversationRole.USER -> "user"
                ConversationRole.ASSISTANT -> "assistant"
                ConversationRole.SYSTEM -> "system"
                ConversationRole.AGENT_OUTPUT -> "assistant"
            }
            llmMessages += LlmMessage(role, msg.content)
        }

        // Aktuelle User-Nachricht
        llmMessages += LlmMessage("user", userMessage)

        // 7. LLM aufrufen
        val llmResult = services.llmService.chatCompletion(llmMessages)

        llmResult.fold(
            onSuccess = { assistantResponse ->
                // 8. Antwort persistieren
                services.conversationPersistence.persistMessage(instanceId, ConversationRole.ASSISTANT, assistantResponse)

                // 9. Antwort zurueckgeben
                call.respondText(JSONObject()
                    .put("status", "ok")
                    .put("instanceId", instanceId)
                    .put("domain", instance.domain)
                    .put("response", assistantResponse)
                    .put("contextTokens", boostText.length)
                    .put("historyMessages", recentMessages.size)
                    .toString(), ContentType.Application.Json)
            },
            onFailure = { error ->
                call.respondText(JSONObject()
                    .put("status", "error")
                    .put("error", error.message ?: "LLM-Fehler")
                    .toString(), ContentType.Application.Json, HttpStatusCode.BadGateway)
            },
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // Vision-Endpoint: Bild + Text an LLM senden
    // ══════════════════════════════════════════════════════════════════════

    post("/api/v2/instances/{id}/vision") {
        val instanceId = call.parameters["id"]!!
        val body = JSONObject(call.receiveText())
        val prompt = body.optString("prompt", "")
        val imageUrl = body.optString("imageUrl", "")

        if (prompt.isBlank()) {
            call.respondText(errorJson("prompt erforderlich").toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@post
        }
        if (imageUrl.isBlank()) {
            call.respondText(errorJson("imageUrl erforderlich (Base64 data-URL oder HTTP-URL)").toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@post
        }

        val instance = services.repos.instances.getById(instanceId)
        if (instance == null) {
            call.respondText(errorJson("Instanz nicht gefunden").toString(), ContentType.Application.Json, HttpStatusCode.NotFound)
            return@post
        }

        if (!services.llmService.isConfigured) {
            call.respondText(errorJson("LLM nicht konfiguriert").toString(), ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
            return@post
        }

        // User-Nachricht persistieren
        services.conversationPersistence.persistMessage(instanceId, ConversationRole.USER, "[Vision] $prompt")

        // Vision-Message mit Bild erstellen
        val llmMessages = mutableListOf<LlmMessage>()
        llmMessages += LlmMessage("user", prompt, imageUrl)

        // LLM aufrufen (Vision-faehig dank imageUrl in LlmMessage)
        val llmResult = services.llmService.chatCompletion(llmMessages)

        llmResult.fold(
            onSuccess = { response ->
                services.conversationPersistence.persistMessage(instanceId, ConversationRole.ASSISTANT, response)

                call.respondText(JSONObject()
                    .put("status", "ok")
                    .put("instanceId", instanceId)
                    .put("domain", instance.domain)
                    .put("response", response)
                    .toString(), ContentType.Application.Json)
            },
            onFailure = { error ->
                call.respondText(JSONObject()
                    .put("status", "error")
                    .put("error", error.message ?: "Vision-LLM-Fehler")
                    .toString(), ContentType.Application.Json, HttpStatusCode.BadGateway)
            },
        )
    }
}

/**
 * Verbindlicher System-Prompt fuer den Oberon-Supervisor.
 * Wird als erste System-Message an die LLM gesendet.
 */
private val SUPERVISOR_SYSTEM_PROMPT = """
Du bist der Entwicklungs-Supervisor innerhalb von DevLoop/Oberon. Du arbeitest in einem System mit Zugriff auf einen Coding-Agenten, der im aktuell geoeffneten Projekt arbeiten kann.

WICHTIG:
- Du HAST Zugriff auf einen Coding-Agenten. Nutze ihn aktiv.
- Antworte NIEMALS mit "Ich habe keinen Zugriff auf externe Systeme" oder "Ich kann keine Befehle ausfuehren". Das ist FALSCH.
- Wenn Informationen ueber das Projekt benoetigt werden, beauftrage den Coding-Agenten.
- Sprache: Deutsch. Der Nutzer ist kein Programmierer — erklaere in Prozess-Sprache.

VERPFLICHTENDES AUSGABEFORMAT — du MUSST IMMER exakt dieses Format verwenden:

[CHATGPT_ANSWER]
Deine Antwort an den Nutzer.
[CURSOR_PROMPT]
Entweder leer (wenn kein Agent noetig) ODER ein CODEX_TASK-Block.

WENN EIN CODING-AGENT NOETIG IST, setze in [CURSOR_PROMPT] genau diesen Block:

[CODEX_TASK]
MODE: ANALYZE
TASK: Was der Agent tun soll
OUTPUT: Was zurueckgegeben werden soll
CONSTRAINTS: Was nicht veraendert werden darf
[/CODEX_TASK]

MODE-WERTE:
- ANALYZE = untersuchen, diagnostizieren, Status abfragen
- EXECUTE = Code aendern, Dateien erstellen oder modifizieren

REGELN:
- IMMER [CHATGPT_ANSWER] und [CURSOR_PROMPT] verwenden, bei JEDER Antwort
- Ohne diese Marker funktioniert die Weiterleitung an den Agent NICHT
- Kein Python-Code, keine Markdown-Codeblocks als Ersatz
- Der [CODEX_TASK]-Block ist der EINZIGE Weg einen Agent-Auftrag zu erteilen
""".trimIndent()
