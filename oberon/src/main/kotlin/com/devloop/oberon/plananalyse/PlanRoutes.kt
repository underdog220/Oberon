package com.devloop.oberon.plananalyse

import com.devloop.oberon.llm.LlmMessage
import com.devloop.oberon.service.OberonPlatformServices
import com.devloop.oberon.util.errorJson
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.logging.Logger

private val log = Logger.getLogger("PlanRoutes")

/**
 * Plan-Analyse-Endpoints: Vision-basierte Bauplan-Erkennung + regelbasierte Flaechenberechnung.
 *
 * POST /api/v2/plan/analyze  — Bild analysieren (Vision + DIN 277 + WoFlV)
 * POST /api/v2/plan/flaeche  — Reine Flaechenberechnung (kein LLM)
 */
fun Route.planRoutes(services: OberonPlatformServices) {

    // ══════════════════════════════════════════════════════════════════════
    // Plan analysieren (Vision + Flaechenberechnung)
    // ══════════════════════════════════════════════════════════════════════

    post("/api/v2/plan/analyze") {
        val body = JSONObject(call.receiveText())
        val prompt = body.optString("prompt", "")
        val imageUrl = body.optString("imageUrl", "")

        if (imageUrl.isBlank()) {
            call.respondText(
                errorJson("imageUrl erforderlich (Base64 data-URL oder HTTP-URL)").toString(),
                ContentType.Application.Json, HttpStatusCode.BadRequest,
            )
            return@post
        }

        if (!services.llmService.isConfigured) {
            call.respondText(
                errorJson("LLM nicht konfiguriert — API-Key setzen per PUT /api/v2/admin/llm-config oder ENV OBERON_OPENAI_API_KEY").toString(),
                ContentType.Application.Json, HttpStatusCode.ServiceUnavailable,
            )
            return@post
        }

        // 1. DSGVO: Text im Prompt auf PII scannen
        val cleanPrompt = if (prompt.isNotBlank()) {
            val dsgvoResult = services.dsgvoService.processText(
                text = prompt,
                clientId = "plan-analyse",
                domain = "plananalyse",
            )
            dsgvoResult.anonymizedText
        } else {
            ""
        }

        // 2. LLM Vision aufrufen mit speziellem Plan-Prompt
        val systemPrompt = buildString {
            appendLine("Analysiere diesen Bauplan. Erkenne:")
            appendLine("1) Plantyp (GRUNDRISS/SCHNITT/ANSICHT/LAGEPLAN/FLURKARTE/BEBAUUNGSPLAN/BAUZEICHNUNG/UNBEKANNT)")
            appendLine("2) Massstab (z.B. 1:100)")
            appendLine("3) Geschoss (z.B. EG, OG, KG, DG)")
            appendLine("4) Alle Raeume mit Bezeichnung und Flaeche in m\u00B2")
            appendLine("5) Grundstuecksflaeche (wenn erkennbar)")
            appendLine("6) Ueberbaute Flaeche (wenn erkennbar)")
            appendLine()
            appendLine("Antworte ausschliesslich als JSON (kein Markdown, kein Text drumherum):")
            appendLine("""{"planType":"...","massstab":"...","geschoss":"...","raeume":[{"bezeichnung":"...","flaeche":0.0,"istDachschraege":false}],"grundstuecksflaeche":null,"ueberbauteFlaeche":null}""")
        }

        val userPrompt = if (cleanPrompt.isNotBlank()) {
            "Zusaetzlicher Kontext: $cleanPrompt"
        } else {
            "Analysiere den beigefuegten Bauplan."
        }

        val llmMessages = listOf(
            LlmMessage("system", systemPrompt),
            LlmMessage("user", userPrompt, imageUrl),
        )

        val llmResult = services.llmService.chatCompletion(llmMessages)

        llmResult.fold(
            onSuccess = { rohantwort ->
                try {
                    // 3. KI-Antwort parsen
                    val parsed = parseKiAntwort(rohantwort)
                    val hinweise = mutableListOf<String>()

                    // 4. FlaechenRechner aufrufen (DIN 277 + WoFlV) wenn Raeume erkannt
                    var din277Ergebnis: FlaechenRechner.Din277Ergebnis? = null
                    var woflvErgebnis: FlaechenRechner.WoflvErgebnis? = null

                    if (parsed.raeume.isNotEmpty()) {
                        // DIN 277 berechnen — alle Raeume als NUF_WOHNEN (Standardannahme)
                        val din277Raeume = parsed.raeume
                            .filter { it.flaeche != null && it.flaeche > 0 }
                            .map { raum ->
                                FlaechenRechner.RaumFlaeche(
                                    bezeichnung = raum.bezeichnung,
                                    flaeche = raum.flaeche!!,
                                    typ = FlaechenRechner.Din277RaumTyp.NUF_WOHNEN,
                                    geschoss = parsed.geschoss ?: "",
                                )
                            }

                        if (din277Raeume.isNotEmpty()) {
                            din277Ergebnis = FlaechenRechner.berechneDin277(
                                FlaechenRechner.Din277Eingabe(din277Raeume),
                            )
                            hinweise.add("DIN 277: Alle Raeume als NUF (Wohnen) eingestuft — Sachverstaendiger muss Raumtypen pruefen.")
                        }

                        // WoFlV berechnen
                        val woflvRaeume = parsed.raeume
                            .filter { it.flaeche != null && it.flaeche > 0 }
                            .map { raum ->
                                FlaechenRechner.WoflvRaum(
                                    bezeichnung = raum.bezeichnung,
                                    grundflaeche = raum.flaeche!!,
                                    typ = FlaechenRechner.WoflvRaumTyp.VOLLWERTIG,
                                    geschoss = parsed.geschoss ?: "",
                                )
                            }

                        if (woflvRaeume.isNotEmpty()) {
                            woflvErgebnis = FlaechenRechner.berechneWoflv(
                                FlaechenRechner.WoflvEingabe(woflvRaeume),
                            )
                            hinweise.add("WoFlV: Alle Raeume als vollwertig eingestuft — Sachverstaendiger muss Dachschraegen/Balkone/Keller pruefen.")
                        }
                    } else {
                        hinweise.add("Keine Raeume mit Flaechenangaben erkannt — manuelle Eingabe erforderlich.")
                    }

                    hinweise.add("[KI-ENTWURF] Raumerkennung und Plantyp durch KI ermittelt — Sachverstaendiger muss Ergebnis pruefen und freigeben.")

                    // 5. PlanAnalyseErgebnis zusammenbauen
                    val ergebnis = PlanAnalyseErgebnis(
                        planType = parsed.planType,
                        massstab = parsed.massstab,
                        geschoss = parsed.geschoss,
                        erkannteRaeume = parsed.raeume,
                        grundstuecksflaeche = parsed.grundstuecksflaeche,
                        ueberbauteFlaeche = parsed.ueberbauteFlaeche,
                        din277 = din277Ergebnis,
                        woflv = woflvErgebnis,
                        hinweise = hinweise,
                        rohdatenKi = rohantwort,
                    )

                    // 6. Als JSON zurueckgeben
                    call.respondText(
                        ergebnisToJson(ergebnis).toString(),
                        ContentType.Application.Json,
                    )
                } catch (e: Throwable) {
                    log.warning("Plan-Analyse Parsing fehlgeschlagen: ${e.message}")
                    // Fallback: Rohantwort mit Fehlerhinweis zurueckgeben
                    call.respondText(
                        JSONObject()
                            .put("status", "teilweise")
                            .put("fehler", "KI-Antwort konnte nicht vollstaendig geparst werden: ${e.message}")
                            .put("rohdatenKi", rohantwort)
                            .put("hinweise", JSONArray().put("[KI-ENTWURF] Automatische Analyse fehlgeschlagen — manuelle Auswertung erforderlich."))
                            .toString(),
                        ContentType.Application.Json,
                    )
                }
            },
            onFailure = { error ->
                call.respondText(
                    JSONObject()
                        .put("status", "error")
                        .put("error", error.message ?: "Vision-LLM-Fehler")
                        .toString(),
                    ContentType.Application.Json, HttpStatusCode.BadGateway,
                )
            },
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // Nur Flaechenberechnung (ohne Vision, Raeume werden uebergeben)
    // ══════════════════════════════════════════════════════════════════════

    post("/api/v2/plan/flaeche") {
        val body = JSONObject(call.receiveText())
        val norm = body.optString("norm", "DIN277").uppercase()
        val raeumeJson = body.optJSONArray("raeume")

        if (raeumeJson == null || raeumeJson.length() == 0) {
            call.respondText(
                errorJson("raeume-Array erforderlich").toString(),
                ContentType.Application.Json, HttpStatusCode.BadRequest,
            )
            return@post
        }

        when (norm) {
            "DIN277" -> {
                val raeume = (0 until raeumeJson.length()).map { i ->
                    val r = raeumeJson.getJSONObject(i)
                    FlaechenRechner.RaumFlaeche(
                        bezeichnung = r.optString("bezeichnung", "Raum ${i + 1}"),
                        flaeche = r.optDouble("flaeche", 0.0),
                        typ = try {
                            FlaechenRechner.Din277RaumTyp.valueOf(r.optString("typ", "NUF_WOHNEN"))
                        } catch (_: Exception) {
                            FlaechenRechner.Din277RaumTyp.NUF_WOHNEN
                        },
                        geschoss = r.optString("geschoss", ""),
                    )
                }
                val ergebnis = FlaechenRechner.berechneDin277(FlaechenRechner.Din277Eingabe(raeume))
                call.respondText(
                    JSONObject()
                        .put("norm", "DIN277")
                        .put("bgf", ergebnis.bgf)
                        .put("nrf", ergebnis.nrf)
                        .put("nuf", ergebnis.nuf)
                        .put("tf", ergebnis.tf)
                        .put("vf", ergebnis.vf)
                        .put("kgf", ergebnis.kgf)
                        .put("einzelpositionen", JSONArray(ergebnis.einzelpositionen))
                        .toString(),
                    ContentType.Application.Json,
                )
            }
            "WOFLV" -> {
                val balkonFaktor = body.optDouble("balkonFaktor", 0.25)
                val raeume = (0 until raeumeJson.length()).map { i ->
                    val r = raeumeJson.getJSONObject(i)
                    FlaechenRechner.WoflvRaum(
                        bezeichnung = r.optString("bezeichnung", "Raum ${i + 1}"),
                        grundflaeche = r.optDouble("grundflaeche", 0.0),
                        typ = try {
                            FlaechenRechner.WoflvRaumTyp.valueOf(r.optString("typ", "VOLLWERTIG"))
                        } catch (_: Exception) {
                            FlaechenRechner.WoflvRaumTyp.VOLLWERTIG
                        },
                        dachschraegeUnter1m = r.optDouble("dachschraegeUnter1m", 0.0),
                        dachschraege1bis2m = r.optDouble("dachschraege1bis2m", 0.0),
                        geschoss = r.optString("geschoss", ""),
                    )
                }
                val ergebnis = FlaechenRechner.berechneWoflv(FlaechenRechner.WoflvEingabe(raeume), balkonFaktor)
                call.respondText(
                    JSONObject()
                        .put("norm", "WOFLV")
                        .put("wohnflaeche", ergebnis.wohnflaeche)
                        .put("einzelpositionen", JSONArray(ergebnis.einzelpositionen))
                        .toString(),
                    ContentType.Application.Json,
                )
            }
            else -> {
                call.respondText(
                    errorJson("Unbekannte Norm '$norm' — erlaubt: DIN277, WOFLV").toString(),
                    ContentType.Application.Json, HttpStatusCode.BadRequest,
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Hilfsfunktionen: KI-Antwort parsen und Ergebnis serialisieren
// ══════════════════════════════════════════════════════════════════════════════

/** Zwischenstruktur fuer die geparste KI-Antwort. */
private data class ParsedKiAntwort(
    val planType: PlanType,
    val massstab: String?,
    val geschoss: String?,
    val raeume: List<ErkannterRaum>,
    val grundstuecksflaeche: Double?,
    val ueberbauteFlaeche: Double?,
)

/**
 * Parst die JSON-Antwort der KI.
 * Tolerant gegenueber fehlenden Feldern und Varianten.
 */
private fun parseKiAntwort(rohantwort: String): ParsedKiAntwort {
    // JSON aus Antwort extrahieren (KI liefert manchmal Markdown-Bloecke)
    val jsonStr = extractJson(rohantwort)
    val json = JSONObject(jsonStr)

    val planTypeStr = json.optString("planType", "UNBEKANNT").uppercase()
    val planType = try {
        PlanType.valueOf(planTypeStr)
    } catch (_: Exception) {
        PlanType.UNBEKANNT
    }

    val raeume = mutableListOf<ErkannterRaum>()
    val raeumeJson = json.optJSONArray("raeume")
    if (raeumeJson != null) {
        for (i in 0 until raeumeJson.length()) {
            val r = raeumeJson.getJSONObject(i)
            raeume.add(
                ErkannterRaum(
                    bezeichnung = r.optString("bezeichnung", "Raum ${i + 1}"),
                    flaeche = if (r.has("flaeche") && !r.isNull("flaeche")) r.optDouble("flaeche") else null,
                    geschoss = json.optString("geschoss", null),
                    istDachschraege = r.optBoolean("istDachschraege", false),
                ),
            )
        }
    }

    return ParsedKiAntwort(
        planType = planType,
        massstab = json.optString("massstab", null).takeIf { !it.isNullOrBlank() },
        geschoss = json.optString("geschoss", null).takeIf { !it.isNullOrBlank() },
        raeume = raeume,
        grundstuecksflaeche = if (json.has("grundstuecksflaeche") && !json.isNull("grundstuecksflaeche")) json.optDouble("grundstuecksflaeche") else null,
        ueberbauteFlaeche = if (json.has("ueberbauteFlaeche") && !json.isNull("ueberbauteFlaeche")) json.optDouble("ueberbauteFlaeche") else null,
    )
}

/**
 * Extrahiert JSON aus einem String, der ggf. in Markdown-Codeblocks eingebettet ist.
 */
private fun extractJson(text: String): String {
    // Versuch 1: JSON in ```json ... ``` Block
    val codeBlockRegex = Regex("```(?:json)?\\s*\\n?(\\{.*?})\\s*```", RegexOption.DOT_MATCHES_ALL)
    val codeBlockMatch = codeBlockRegex.find(text)
    if (codeBlockMatch != null) return codeBlockMatch.groupValues[1].trim()

    // Versuch 2: Erstes { ... } im Text
    val firstBrace = text.indexOf('{')
    val lastBrace = text.lastIndexOf('}')
    if (firstBrace >= 0 && lastBrace > firstBrace) {
        return text.substring(firstBrace, lastBrace + 1)
    }

    // Fallback: Gesamter Text
    return text.trim()
}

/**
 * Serialisiert ein PlanAnalyseErgebnis als JSONObject.
 */
private fun ergebnisToJson(ergebnis: PlanAnalyseErgebnis): JSONObject {
    val json = JSONObject()
    json.put("id", ergebnis.id)
    json.put("planType", ergebnis.planType.name)
    json.put("planTypBeschreibung", ergebnis.planType.beschreibung)
    json.put("massstab", ergebnis.massstab)
    json.put("geschoss", ergebnis.geschoss)

    // Erkannte Raeume
    val raeumeArr = JSONArray()
    for (raum in ergebnis.erkannteRaeume) {
        raeumeArr.put(
            JSONObject()
                .put("bezeichnung", raum.bezeichnung)
                .put("flaeche", raum.flaeche)
                .put("geschoss", raum.geschoss)
                .put("istDachschraege", raum.istDachschraege),
        )
    }
    json.put("erkannteRaeume", raeumeArr)

    json.put("grundstuecksflaeche", ergebnis.grundstuecksflaeche)
    json.put("ueberbauteFlaeche", ergebnis.ueberbauteFlaeche)

    // DIN 277 Ergebnis
    if (ergebnis.din277 != null) {
        json.put("din277", JSONObject()
            .put("bgf", ergebnis.din277.bgf)
            .put("nrf", ergebnis.din277.nrf)
            .put("nuf", ergebnis.din277.nuf)
            .put("tf", ergebnis.din277.tf)
            .put("vf", ergebnis.din277.vf)
            .put("kgf", ergebnis.din277.kgf)
            .put("einzelpositionen", JSONArray(ergebnis.din277.einzelpositionen)),
        )
    }

    // WoFlV Ergebnis
    if (ergebnis.woflv != null) {
        json.put("woflv", JSONObject()
            .put("wohnflaeche", ergebnis.woflv.wohnflaeche)
            .put("einzelpositionen", JSONArray(ergebnis.woflv.einzelpositionen)),
        )
    }

    json.put("hinweise", JSONArray(ergebnis.hinweise))
    json.put("rohdatenKi", ergebnis.rohdatenKi)

    return json
}
