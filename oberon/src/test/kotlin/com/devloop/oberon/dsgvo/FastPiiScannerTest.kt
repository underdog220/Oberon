package com.devloop.oberon.dsgvo

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class FastPiiScannerTest {
    private val scanner = FastPiiScanner()

    @Test
    fun `erkennt deutsche IBAN`() {
        val text = "Bitte überweisen Sie auf DE89370400440532013000"
        val matches = scanner.scan(text)
        assertTrue(matches.any { it.category == PiiCategory.IBAN })
        assertEquals("DE89370400440532013000", matches.first { it.category == PiiCategory.IBAN }.originalText)
    }

    @Test
    fun `erkennt IBAN mit Leerzeichen`() {
        val text = "IBAN: DE89 3704 0044 0532 0130 00"
        val matches = scanner.scan(text)
        assertTrue(matches.any { it.category == PiiCategory.IBAN })
    }

    @Test
    fun `erkennt Telefonnummern`() {
        val text = "Erreichbar unter 0911-1234567 oder +49 911 1234567"
        val matches = scanner.scan(text)
        assertTrue(matches.any { it.category == PiiCategory.TELEFON })
    }

    @Test
    fun `erkennt E-Mail`() {
        val text = "Kontakt: mueller@example.com"
        val matches = scanner.scan(text)
        assertTrue(matches.any { it.category == PiiCategory.EMAIL })
    }

    @Test
    fun `erkennt Straße mit Hausnummer`() {
        val text = "Objekt befindet sich in der Elbestraße 20e"
        val matches = scanner.scan(text)
        assertTrue(matches.any { it.category == PiiCategory.ADRESSE })
    }

    @Test
    fun `erkennt Flurstück`() {
        val text = "Flurstück 125/39 in der Gemarkung Katzwang"
        val matches = scanner.scan(text)
        assertTrue(matches.any { it.category == PiiCategory.FLURNUMMER })
    }

    @Test
    fun `erkennt Flurstück Kurzform`() {
        val text = "Flst. 125/39"
        val matches = scanner.scan(text)
        assertTrue(matches.any { it.category == PiiCategory.FLURNUMMER })
    }

    @Test
    fun `erkennt Grundbuchblatt`() {
        val text = "Grundbuch von Katzwang Blatt 5076"
        val matches = scanner.scan(text)
        assertTrue(matches.any { it.category == PiiCategory.GRUNDBUCHBLATT })
    }

    @Test
    fun `erkennt Gemarkung`() {
        val text = "Gemarkung Katzwang, Nürnberg"
        val matches = scanner.scan(text)
        assertTrue(matches.any { it.category == PiiCategory.GEMARKUNG })
    }

    @Test
    fun `erkennt Person mit Anrede`() {
        val text = "Eigentümer: Herr Müller"
        val matches = scanner.scan(text)
        assertTrue(matches.any { it.category == PiiCategory.PERSON })
    }

    @Test
    fun `erkennt mehrere PII-Typen gleichzeitig`() {
        val text = "Frau Schmidt, Elbestraße 20e, Gemarkung Katzwang, Flurstück 125/39, Blatt 5076"
        val matches = scanner.scan(text)
        assertTrue(matches.size >= 4) // Person, Adresse, Gemarkung, Flur, Grundbuch
    }

    @Test
    fun `kein PII in normalem Text`() {
        val text = "Das Gebäude wurde im Jahr 1990 errichtet. Der Zustand ist befriedigend."
        val matches = scanner.scan(text)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `erkennt Katasterdaten`() {
        val text = "Katasteramt Nürnberg, Bezirk 456"
        val matches = scanner.scan(text)
        assertTrue(matches.any { it.category == PiiCategory.KATASTER })
    }
}
