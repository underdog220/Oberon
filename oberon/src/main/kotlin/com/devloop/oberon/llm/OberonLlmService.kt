package com.devloop.oberon.llm

import com.devloop.oberon.OberonConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.logging.Logger

/**
 * LLM-Anbindung fuer Oberon.
 *
 * Ruft die OpenAI-kompatible Chat Completions API auf.
 * Funktioniert mit OpenAI, Azure OpenAI, lokalen LLMs (LM Studio, Ollama)
 * und jedem anderen OpenAI-kompatiblen Endpoint.
 */
class OberonLlmService(private val config: OberonConfig) {

    private val log = Logger.getLogger("OberonLlmService")
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    // Dynamische Overrides (zur Laufzeit aenderbar, ohne Neustart)
    @Volatile private var overrideApiKey: String? = null
    @Volatile private var overrideBaseUrl: String? = null
    @Volatile private var overrideModel: String? = null

    /** Aktiver API-Key (Override > Config). */
    val activeApiKey: String get() = overrideApiKey?.takeIf { it.isNotBlank() } ?: config.openAiApiKey
    /** Aktive Base-URL (Override > Config). */
    val activeBaseUrl: String get() = overrideBaseUrl?.takeIf { it.isNotBlank() } ?: config.openAiBaseUrl
    /** Aktives Modell (Override > Config). */
    val activeModel: String get() = overrideModel?.takeIf { it.isNotBlank() } ?: config.openAiModel

    val isConfigured: Boolean get() = activeApiKey.isNotBlank()

    /**
     * Aktualisiert die LLM-Konfiguration zur Laufzeit.
     * Null-Werte lassen den bestehenden Wert unveraendert.
     */
    fun updateConfig(apiKey: String? = null, baseUrl: String? = null, model: String? = null) {
        if (apiKey != null) overrideApiKey = apiKey
        if (baseUrl != null) overrideBaseUrl = baseUrl
        if (model != null) overrideModel = model
        log.info("LLM-Config aktualisiert: model=${activeModel}, baseUrl=${activeBaseUrl}, key=${if (activeApiKey.isNotBlank()) "***gesetzt***" else "FEHLT"}")
    }

    /**
     * Chat Completion: Sendet Messages an die LLM und gibt die Antwort zurueck.
     *
     * @param messages Liste von (role, content) Paaren
     * @return Antwort-Text oder Fehler
     */
    suspend fun chatCompletion(messages: List<LlmMessage>): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(IllegalStateException("OpenAI API-Key nicht konfiguriert (OBERON_OPENAI_API_KEY oder PUT /api/v2/admin/llm-config)"))
        }

        try {
            val messagesJson = JSONArray()
            for (msg in messages) {
                if (msg.imageUrl != null) {
                    // Vision-Message: content als Array mit Text + Bild
                    val contentArray = JSONArray()
                        .put(JSONObject().put("type", "text").put("text", msg.content))
                        .put(JSONObject().put("type", "image_url").put("image_url",
                            JSONObject().put("url", msg.imageUrl)))
                    messagesJson.put(JSONObject().put("role", msg.role).put("content", contentArray))
                } else {
                    messagesJson.put(JSONObject().put("role", msg.role).put("content", msg.content))
                }
            }

            // Vision-Anfragen brauchen ein Vision-faehiges Modell
            val hatBild = messages.any { it.imageUrl != null }
            val modell = if (hatBild) {
                // gpt-4o/gpt-4o-mini unterstuetzen Vision, gpt-4.1-mini auch
                if (activeModel.contains("gpt-4")) activeModel else "gpt-4o-mini"
            } else {
                activeModel
            }

            val requestBody = JSONObject()
                .put("model", modell)
                .put("messages", messagesJson)
                .put("max_tokens", 4096)

            val url = "${activeBaseUrl.trimEnd('/')}/v1/chat/completions"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${activeApiKey}")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build()

            log.fine("LLM request: ${messages.size} messages, model=${activeModel}")

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                val errorBody = response.body()?.take(500) ?: ""
                log.warning("LLM error ${response.statusCode()}: $errorBody")
                return@withContext Result.failure(RuntimeException("LLM HTTP ${response.statusCode()}: $errorBody"))
            }

            val json = JSONObject(response.body())
            val choices = json.getJSONArray("choices")
            if (choices.length() == 0) {
                return@withContext Result.failure(RuntimeException("LLM: keine Antwort (leere choices)"))
            }
            val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
            log.info("LLM response: ${content.length} chars")
            Result.success(content)
        } catch (e: Throwable) {
            log.warning("LLM call failed: ${e.message}")
            Result.failure(e)
        }
    }
}

data class LlmMessage(
    val role: String,
    val content: String,
    /** Base64-Bild-URL fuer Vision-Anfragen (z.B. "data:image/jpeg;base64,...") */
    val imageUrl: String? = null
)
