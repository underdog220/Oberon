package com.devloop.oberon.dsgvo

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Anonymisierungs-Engine mit Session-Management.
 *
 * Jede Session haelt ein bidirektionales Mapping (Original <-> Platzhalter).
 * Innerhalb einer Session bekommt derselbe Originalwert immer denselben Platzhalter.
 *
 * Thread-safe durch ConcurrentHashMap.
 * Sessions werden nach Ablauf der TTL automatisch aufgeraeumt.
 */
class AnonymizationEngine(
    /** Session-TTL in Minuten (Standard: 60) */
    private val sessionTtlMinutes: Long = 60,
) {
    // Alle aktiven Sessions
    private val sessions = ConcurrentHashMap<String, AnonymizationSession>()

    /**
     * Anonymisiert den Text anhand der erkannten PII-Treffer.
     * Gibt das Ergebnis mit dem anonymisierten Text und Statistiken zurueck.
     */
    fun anonymize(text: String, sessionId: String, matches: List<PiiMatch>): AnonymizationResult {
        val startTime = System.currentTimeMillis()

        // Abgelaufene Sessions aufraumen
        cleanupExpiredSessions()

        val session = sessions.computeIfAbsent(sessionId) {
            AnonymizationSession(
                sessionId = it,
                createdAt = Instant.now(),
                mappings = ConcurrentHashMap(),
                reverseMappings = ConcurrentHashMap(),
            )
        }

        if (matches.isEmpty()) {
            return AnonymizationResult(
                anonymizedText = text,
                sessionId = sessionId,
                piiFound = false,
                piiTypes = emptyList(),
                mappingCount = session.mappings.size,
                scanDurationMs = System.currentTimeMillis() - startTime,
            )
        }

        // Treffer von hinten nach vorne ersetzen (damit Indizes stimmen)
        val sortedMatches = matches.sortedByDescending { it.startIndex }
        val sb = StringBuilder(text)
        val gefundenTypen = mutableSetOf<PiiCategory>()

        for (match in sortedMatches) {
            val platzhalter = session.getOrCreatePlatzhalter(match.category, match.originalText)
            sb.replace(match.startIndex, match.endIndex, platzhalter)
            gefundenTypen.add(match.category)
        }

        return AnonymizationResult(
            anonymizedText = sb.toString(),
            sessionId = sessionId,
            piiFound = true,
            piiTypes = gefundenTypen.toList(),
            mappingCount = session.mappings.size,
            scanDurationMs = System.currentTimeMillis() - startTime,
        )
    }

    /**
     * De-anonymisiert den Text: Ersetzt alle Platzhalter durch die Originaldaten.
     */
    fun deanonymize(text: String, sessionId: String): String {
        val session = sessions[sessionId] ?: return text

        var result = text
        // Laengste Platzhalter zuerst ersetzen (verhindert Teilersetzungen)
        val sortedMappings = session.reverseMappings.entries.sortedByDescending { it.key.length }
        for ((platzhalter, original) in sortedMappings) {
            result = result.replace(platzhalter, original)
        }
        return result
    }

    /** Gibt eine Session zurueck oder null wenn nicht vorhanden. */
    fun getSession(sessionId: String): AnonymizationSession? = sessions[sessionId]

    /** Entfernt eine Session und alle zugehoerigen Mappings. */
    fun removeSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    /** Raeumt abgelaufene Sessions auf (aelter als TTL). */
    private fun cleanupExpiredSessions() {
        val cutoff = Instant.now().minusSeconds(sessionTtlMinutes * 60)
        sessions.entries.removeIf { it.value.createdAt.isBefore(cutoff) }
    }
}

/**
 * Eine Anonymisierungs-Session mit bidirektionalem Mapping.
 */
data class AnonymizationSession(
    val sessionId: String,
    val createdAt: Instant,
    val mappings: ConcurrentHashMap<String, String>,         // original -> platzhalter
    val reverseMappings: ConcurrentHashMap<String, String>,  // platzhalter -> original
) {
    // Zaehler pro Kategorie fuer die Platzhalter-Nummerierung
    private val categoryCounters = ConcurrentHashMap<PiiCategory, Int>()

    /**
     * Gibt den bestehenden Platzhalter fuer einen Originalwert zurueck
     * oder erstellt einen neuen (z.B. [PERSON_1], [PERSON_2], ...).
     */
    fun getOrCreatePlatzhalter(category: PiiCategory, originalText: String): String {
        return mappings.computeIfAbsent(originalText) {
            val counter = categoryCounters.merge(category, 1, Int::plus) ?: 1
            val platzhalter = "[${category.platzhalterPrefix}_$counter]"
            reverseMappings[platzhalter] = originalText
            platzhalter
        }
    }
}

/**
 * Ergebnis einer Anonymisierung.
 */
data class AnonymizationResult(
    val anonymizedText: String,
    val sessionId: String,
    val piiFound: Boolean,
    val piiTypes: List<PiiCategory>,
    val mappingCount: Int,
    val scanDurationMs: Long,
)
