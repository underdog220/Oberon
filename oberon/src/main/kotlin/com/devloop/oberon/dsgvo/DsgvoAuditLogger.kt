package com.devloop.oberon.dsgvo

import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * DSGVO-Audit-Logger.
 * Speichert Events als JSONL-Dateien (eine Zeile pro Event) im Datenverzeichnis.
 * Pfad: {dataDir}/dsgvo/audit/YYYY-MM-DD.jsonl
 */
class DsgvoAuditLogger(private val dataDir: String) {

    private val auditDir: Path = Paths.get(dataDir, "dsgvo", "audit")

    init {
        // Audit-Verzeichnis sicherstellen
        Files.createDirectories(auditDir)
    }

    /**
     * Schreibt ein Audit-Event als JSON-Zeile in die Tagesdatei.
     */
    fun log(event: DsgvoAuditEvent) {
        val datei = auditDatei(LocalDate.now())
        val json = eventToJson(event)
        Files.write(
            datei,
            listOf(json.toString()),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    /**
     * Liest alle Events fuer ein bestimmtes Datum.
     */
    fun getEventsForDate(date: LocalDate): List<DsgvoAuditEvent> {
        val datei = auditDatei(date)
        if (!Files.exists(datei)) return emptyList()

        return Files.readAllLines(datei)
            .filter { it.isNotBlank() }
            .map { jsonToEvent(JSONObject(it)) }
    }

    /**
     * Erstellt einen Tagesbericht mit aggregierten Statistiken.
     */
    fun generateDailyReport(date: LocalDate): DsgvoTagesbericht {
        val events = getEventsForDate(date)

        val piiStatistik = mutableMapOf<PiiCategory, Int>()
        var lokal = 0
        var anonymisiert = 0
        var extern = 0
        var proxy = 0
        var korrektor = 0
        var fallback = 0
        var externesMitPii = false

        for (event in events) {
            // PII-Typen zaehlen
            for (typ in event.piiTypes) {
                piiStatistik[typ] = (piiStatistik[typ] ?: 0) + 1
            }

            // Routing zaehlen
            when (event.routingDecision) {
                RoutingDecision.LOCAL -> lokal++
                RoutingDecision.PROXY -> proxy++
                RoutingDecision.CORRECTOR -> korrektor++
                RoutingDecision.DIRECT -> extern++
                RoutingDecision.ENGINE -> { /* regelbasiert, kein LLM */ }
            }

            if (event.anonymized) anonymisiert++
            if (event.fallbackAktiv) fallback++

            // Pruefen ob PII-Daten extern uebertragen wurden (sollte nie passieren)
            if (event.piiFound && !event.anonymized &&
                event.routingDecision in listOf(RoutingDecision.DIRECT, RoutingDecision.PROXY)
            ) {
                externesMitPii = true
            }
        }

        return DsgvoTagesbericht(
            datum = date,
            gesamtAnfragen = events.size,
            davonLokal = lokal,
            davonAnonymisiert = anonymisiert,
            davonExtern = extern,
            davonProxy = proxy,
            davonKorrektor = korrektor,
            davonFallback = fallback,
            piiTypenStatistik = piiStatistik,
            keineExterneUebertragungMitPii = !externesMitPii,
        )
    }

    // --- Hilfsfunktionen ---

    private fun auditDatei(date: LocalDate): Path {
        val dateiname = date.format(DateTimeFormatter.ISO_LOCAL_DATE) + ".jsonl"
        return auditDir.resolve(dateiname)
    }

    private fun eventToJson(event: DsgvoAuditEvent): JSONObject {
        return JSONObject().apply {
            put("id", event.id)
            put("timestamp", event.timestamp.toString())
            put("clientId", event.clientId)
            put("domain", event.domain)
            put("workspaceId", event.workspaceId ?: JSONObject.NULL)
            put("documentType", event.documentType ?: JSONObject.NULL)
            put("piiFound", event.piiFound)
            put("piiTypes", JSONArray(event.piiTypes.map { it.name }))
            put("anonymized", event.anonymized)
            put("routingDecision", event.routingDecision.name)
            put("processingDurationMs", event.processingDurationMs)
            put("resultStatus", event.resultStatus)
            put("fallbackAktiv", event.fallbackAktiv)
            put("fallbackGrund", event.fallbackGrund ?: JSONObject.NULL)
            put("mappingId", event.mappingId ?: JSONObject.NULL)
        }
    }

    private fun jsonToEvent(json: JSONObject): DsgvoAuditEvent {
        return DsgvoAuditEvent(
            id = json.getString("id"),
            timestamp = Instant.parse(json.getString("timestamp")),
            clientId = json.getString("clientId"),
            domain = json.getString("domain"),
            workspaceId = json.optString("workspaceId", null),
            documentType = json.optString("documentType", null),
            piiFound = json.getBoolean("piiFound"),
            piiTypes = json.getJSONArray("piiTypes").let { arr ->
                (0 until arr.length()).map { PiiCategory.valueOf(arr.getString(it)) }
            },
            anonymized = json.getBoolean("anonymized"),
            routingDecision = RoutingDecision.valueOf(json.getString("routingDecision")),
            processingDurationMs = json.getLong("processingDurationMs"),
            resultStatus = json.getString("resultStatus"),
            fallbackAktiv = json.optBoolean("fallbackAktiv", false),
            fallbackGrund = json.optString("fallbackGrund", null),
            mappingId = json.optString("mappingId", null),
        )
    }
}

/**
 * Einzelnes DSGVO-Audit-Event.
 */
data class DsgvoAuditEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Instant = Instant.now(),
    val clientId: String,
    val domain: String,
    val workspaceId: String? = null,
    val documentType: String? = null,
    val piiFound: Boolean,
    val piiTypes: List<PiiCategory> = emptyList(),
    val anonymized: Boolean,
    val routingDecision: RoutingDecision,
    val processingDurationMs: Long,
    val resultStatus: String,
    val fallbackAktiv: Boolean = false,
    val fallbackGrund: String? = null,
    val mappingId: String? = null,
)

/**
 * Routing-Entscheidung: Wohin wird die Anfrage geleitet?
 */
enum class RoutingDecision {
    /** Lokales LLM (z.B. Ollama) */
    LOCAL,
    /** Oberon macht API-Call (anonymisiert) */
    PROXY,
    /** Client macht eigenen Call, Oberon nur Sanitize */
    CORRECTOR,
    /** Regelbasiert, kein LLM noetig */
    ENGINE,
    /** Kein PII gefunden, direkt extern */
    DIRECT,
}

/**
 * Aggregierter Tagesbericht fuer DSGVO-Compliance.
 */
/**
 * Aggregierter Tagesbericht fuer DSGVO-Compliance.
 */
data class DsgvoTagesbericht(
    val datum: LocalDate,
    val gesamtAnfragen: Int,
    val davonLokal: Int,
    val davonAnonymisiert: Int,
    val davonExtern: Int,
    val davonProxy: Int,
    val davonKorrektor: Int,
    val davonFallback: Int,
    val piiTypenStatistik: Map<PiiCategory, Int>,
    /** True wenn keine PII-Daten unanonymisiert extern uebertragen wurden */
    val keineExterneUebertragungMitPii: Boolean,
) {
    // Kompatibilitaets-Aliases fuer DsgvoRoutes
    val activeSessions: Int get() = 0  // Sessions sind fluechtig, nicht im Tagesbericht
    val totalRequests: Int get() = gesamtAnfragen
    val piiFoundCount: Int get() = piiTypenStatistik.values.sum()
    val anonymizedCount: Int get() = davonAnonymisiert

    /** Serialisiert den Bericht als JSON-Objekt. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("datum", datum.toString())
        put("gesamtAnfragen", gesamtAnfragen)
        put("davonLokal", davonLokal)
        put("davonAnonymisiert", davonAnonymisiert)
        put("davonExtern", davonExtern)
        put("davonProxy", davonProxy)
        put("davonKorrektor", davonKorrektor)
        put("davonFallback", davonFallback)
        put("piiTypenStatistik", JSONObject(piiTypenStatistik.mapKeys { it.key.name }))
        put("keineExterneUebertragungMitPii", keineExterneUebertragungMitPii)
    }
}
