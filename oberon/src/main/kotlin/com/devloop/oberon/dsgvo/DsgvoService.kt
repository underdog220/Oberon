package com.devloop.oberon.dsgvo

import com.devloop.oberon.OberonConfig
import java.time.LocalDate
import java.util.UUID

/**
 * DSGVO-Service — zentrale Fassade fuer PII-Scanning, Anonymisierung und Audit.
 *
 * Orchestriert:
 * - FastPiiScanner: Erkennung personenbezogener Daten per Regex
 * - AnonymizationEngine: Platzhalter-basierte Anonymisierung mit Session-Management
 * - DsgvoAuditLogger: Revisionssichere Protokollierung aller Verarbeitungsschritte
 */
class DsgvoService(
    private val config: OberonConfig,
    val auditLogger: DsgvoAuditLogger,
) {
    val scanner = FastPiiScanner()
    val anonymizer = AnonymizationEngine(
        sessionTtlMinutes = config.dsgvoSessionTtlMinutes.toLong()
    )

    /**
     * Hauptfunktion: Text scannen und (bei Fund) anonymisieren.
     * Erstellt automatisch ein Audit-Event.
     */
    fun processText(
        text: String,
        clientId: String,
        domain: String,
        sessionId: String? = null,
    ): AnonymizationResult {
        val startTime = System.currentTimeMillis()
        val effektiveSessionId = sessionId ?: UUID.randomUUID().toString()

        // Schritt 1: PII scannen
        val matches = scanner.scan(text)

        // Schritt 2: Anonymisieren (wenn DSGVO aktiv und PII gefunden oder immer-anonymisieren gesetzt)
        val result = if (config.dsgvoEnabled && (matches.isNotEmpty() || config.dsgvoAlwaysAnonymize)) {
            anonymizer.anonymize(text, effektiveSessionId, matches)
        } else {
            AnonymizationResult(
                anonymizedText = text,
                sessionId = effektiveSessionId,
                piiFound = matches.isNotEmpty(),
                piiTypes = matches.map { it.category }.distinct(),
                mappingCount = 0,
                scanDurationMs = System.currentTimeMillis() - startTime,
            )
        }

        // Schritt 3: Routing-Entscheidung
        val routing = routingDecision(result.piiFound, null)

        // Schritt 4: Audit-Event protokollieren
        val dauer = System.currentTimeMillis() - startTime
        auditLogger.log(
            DsgvoAuditEvent(
                clientId = clientId,
                domain = domain,
                piiFound = result.piiFound,
                piiTypes = result.piiTypes,
                anonymized = result.piiFound && config.dsgvoEnabled,
                routingDecision = routing,
                processingDurationMs = dauer,
                resultStatus = "ok",
                mappingId = if (result.piiFound) effektiveSessionId else null,
            )
        )

        return result
    }

    /**
     * De-anonymisiert einen Text anhand der Session-Mappings.
     */
    fun deanonymize(text: String, sessionId: String): String {
        return anonymizer.deanonymize(text, sessionId)
    }

    /**
     * Bestimmt das Routing basierend auf PII-Fund und Dokumenttyp.
     *
     * Logik:
     * - PII gefunden -> LOCAL (lokales LLM) oder PROXY (anonymisiert extern)
     * - Kein PII -> DIRECT (externe API erlaubt)
     * - Gutachten-Dokumente -> immer LOCAL bevorzugt
     */
    fun routingDecision(piiFound: Boolean, documentType: String?): RoutingDecision {
        // Gutachten-relevante Dokumente immer lokal verarbeiten
        val gutachtenTypen = setOf("gutachten", "bewertung", "verkehrswert", "wertermittlung")
        val istGutachten = documentType?.lowercase() in gutachtenTypen

        return when {
            // Gutachten immer lokal
            istGutachten -> RoutingDecision.LOCAL
            // PII gefunden: lokal bevorzugt, sonst anonymisiert via Proxy
            piiFound && config.dsgvoAlwaysAnonymize -> RoutingDecision.LOCAL
            piiFound -> RoutingDecision.PROXY
            // Kein PII: direkt extern erlaubt
            else -> RoutingDecision.DIRECT
        }
    }

    /**
     * Erstellt einen DSGVO-Tagesbericht fuer das angegebene Datum.
     */
    fun dailyReport(date: LocalDate): DsgvoTagesbericht {
        return auditLogger.generateDailyReport(date)
    }
}
