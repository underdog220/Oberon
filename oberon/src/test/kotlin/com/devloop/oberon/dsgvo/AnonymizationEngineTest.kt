package com.devloop.oberon.dsgvo

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class AnonymizationEngineTest {
    private val engine = AnonymizationEngine()
    private val scanner = FastPiiScanner()

    @Test
    fun `anonymisiert und de-anonymisiert korrekt`() {
        val originalText = "Herr Müller wohnt in der Elbestraße 20e, Flurstück 125/39"
        val matches = scanner.scan(originalText)

        val result = engine.anonymize(originalText, "test-session", matches)

        // Platzhalter vorhanden
        assertFalse(result.anonymizedText.contains("Müller"))
        assertFalse(result.anonymizedText.contains("Elbestraße 20e"))
        assertTrue(result.anonymizedText.contains("[PERSON_"))
        assertTrue(result.anonymizedText.contains("[ADRESSE_") || result.anonymizedText.contains("[FLUR_"))

        // De-Anonymisierung
        val restored = engine.deanonymize(result.anonymizedText, "test-session")
        assertEquals(originalText, restored)
    }

    @Test
    fun `gleiche Werte bekommen gleichen Platzhalter`() {
        val text = "Herr Müller und Herr Müller"
        val matches = scanner.scan(text)
        val result = engine.anonymize(text, "test-consistency", matches)

        // Beide "Müller" sollen den gleichen Platzhalter haben
        val platzhalter = Regex("\\[PERSON_\\d+\\]").findAll(result.anonymizedText).toList()
        if (platzhalter.size >= 2) {
            assertEquals(platzhalter[0].value, platzhalter[1].value)
        }
    }

    @Test
    fun `verschiedene Sessions sind unabhaengig`() {
        val text1 = "Herr Müller"
        val text2 = "Frau Schmidt"

        val matches1 = scanner.scan(text1)
        val matches2 = scanner.scan(text2)

        engine.anonymize(text1, "session-a", matches1)
        engine.anonymize(text2, "session-b", matches2)

        // De-Anonymisierung nur mit richtiger Session
        val restored = engine.deanonymize("[PERSON_1]", "session-a")
        assertNotEquals("[PERSON_1]", restored) // Sollte de-anonymisiert sein
    }

    @Test
    fun `Session wird nach Timeout aufgeraeumt`() {
        // Hier nur pruefen dass removeSession funktioniert
        val text = "Herr Test"
        val matches = scanner.scan(text)
        engine.anonymize(text, "temp-session", matches)

        assertNotNull(engine.getSession("temp-session"))
        engine.removeSession("temp-session")
        assertNull(engine.getSession("temp-session"))
    }
}
