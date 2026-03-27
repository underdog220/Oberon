package com.devloop.oberon.routing

import com.devloop.oberon.service.OberonPlatformServices
import com.devloop.oberon.util.errorJson
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.json.JSONObject
import java.nio.file.Files
import java.util.Properties

/**
 * Admin-Endpoints fuer Server-Konfiguration zur Laufzeit.
 * Alle Endpoints sind durch den globalen Auth-Interceptor geschuetzt (Master-Token).
 */
fun Route.adminRoutes(services: OberonPlatformServices) {

    /**
     * GET /api/v2/admin/llm-config
     * Zeigt aktuelle LLM-Konfiguration (Key maskiert).
     */
    get("/api/v2/admin/llm-config") {
        val llm = services.llmService
        call.respondText(
            JSONObject()
                .put("configured", llm.isConfigured)
                .put("model", llm.activeModel)
                .put("baseUrl", llm.activeBaseUrl)
                .put("apiKeyHint", maskKey(llm.activeApiKey))
                .toString(),
            ContentType.Application.Json,
        )
    }

    /**
     * PUT /api/v2/admin/llm-config
     * Setzt API-Key, Base-URL und/oder Modell zur Laufzeit.
     * Persistiert die Werte in ~/.oberon/oberon.env.
     *
     * Body (JSON): { "apiKey": "sk-...", "baseUrl": "...", "model": "..." }
     * Alle Felder optional — nur gesetzte Felder werden aktualisiert.
     */
    put("/api/v2/admin/llm-config") {
        val body = JSONObject(call.receiveText())
        val apiKey = body.optString("apiKey", "").takeIf { it.isNotBlank() }
        val baseUrl = body.optString("baseUrl", "").takeIf { it.isNotBlank() }
        val model = body.optString("model", "").takeIf { it.isNotBlank() }

        if (apiKey == null && baseUrl == null && model == null) {
            call.respondText(
                errorJson("Mindestens ein Feld erforderlich: apiKey, baseUrl, model").toString(),
                ContentType.Application.Json, HttpStatusCode.BadRequest,
            )
            return@put
        }

        // 1. Laufzeit-Config aktualisieren
        services.llmService.updateConfig(apiKey = apiKey, baseUrl = baseUrl, model = model)

        // 2. In oberon.env persistieren
        persistToEnvFile(services, apiKey = apiKey, baseUrl = baseUrl, model = model)

        val llm = services.llmService
        call.respondText(
            JSONObject()
                .put("status", "ok")
                .put("configured", llm.isConfigured)
                .put("model", llm.activeModel)
                .put("baseUrl", llm.activeBaseUrl)
                .put("apiKeyHint", maskKey(llm.activeApiKey))
                .put("persisted", true)
                .put("note", "Aenderungen sofort aktiv und in oberon.env gespeichert.")
                .toString(),
            ContentType.Application.Json,
        )
    }

    /**
     * DELETE /api/v2/admin/llm-config
     * Entfernt den API-Key (Laufzeit + Datei).
     */
    delete("/api/v2/admin/llm-config") {
        services.llmService.updateConfig(apiKey = "")
        persistToEnvFile(services, apiKey = "")

        call.respondText(
            JSONObject()
                .put("status", "ok")
                .put("configured", false)
                .put("note", "API-Key entfernt.")
                .toString(),
            ContentType.Application.Json,
        )
    }
}

/**
 * Persistiert LLM-Werte in die oberon.env-Datei.
 * Bestehende Werte bleiben erhalten, nur uebergebene Felder werden aktualisiert.
 */
private fun persistToEnvFile(
    services: OberonPlatformServices,
    apiKey: String? = null,
    baseUrl: String? = null,
    model: String? = null,
) {
    val envFile = services.config.envFile
    val props = Properties()

    // Bestehende Datei laden
    if (Files.exists(envFile)) {
        Files.newBufferedReader(envFile).use { props.load(it) }
    }

    // Nur uebergebene Felder setzen/loeschen
    if (apiKey != null) {
        if (apiKey.isBlank()) props.remove("OBERON_OPENAI_API_KEY")
        else props.setProperty("OBERON_OPENAI_API_KEY", apiKey)
    }
    if (baseUrl != null) {
        if (baseUrl.isBlank()) props.remove("OBERON_OPENAI_BASE_URL")
        else props.setProperty("OBERON_OPENAI_BASE_URL", baseUrl)
    }
    if (model != null) {
        if (model.isBlank()) props.remove("OBERON_OPENAI_MODEL")
        else props.setProperty("OBERON_OPENAI_MODEL", model)
    }

    // Speichern
    Files.newBufferedWriter(envFile).use { writer ->
        props.store(writer, "Oberon-Konfiguration (automatisch generiert)")
    }
}

/** Maskiert einen API-Key fuer die Anzeige: sk-proj-...XXXX */
private fun maskKey(key: String): String = when {
    key.isBlank() -> "(nicht gesetzt)"
    key.length <= 8 -> "****"
    else -> "${key.take(8)}...${key.takeLast(4)}"
}
