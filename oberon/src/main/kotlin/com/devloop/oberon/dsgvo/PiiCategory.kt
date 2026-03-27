package com.devloop.oberon.dsgvo

/**
 * Kategorien personenbezogener Daten (PII) fuer die DSGVO-konforme Verarbeitung.
 * Jede Kategorie definiert einen Platzhalter-Prefix und eine menschenlesbare Beschreibung.
 */
enum class PiiCategory(val platzhalterPrefix: String, val beschreibung: String) {
    PERSON("PERSON", "Vor-/Nachname"),
    IBAN("IBAN", "Bankkontonummer"),
    ADRESSE("ADRESSE", "Strasse + Hausnummer"),
    TELEFON("TELEFON", "Telefon-/Mobilnummer"),
    EMAIL("EMAIL", "E-Mail-Adresse"),
    DATUM_MIT_NAME("DATUM_NAME", "Datumsangabe mit Personenbezug"),
    FLURNUMMER("FLUR", "Flustueck-/Flurnummer"),
    GRUNDBUCHBLATT("GRUNDBUCH", "Grundbuchblatt-Nummer"),
    KATASTER("KATASTER", "Katasterdaten"),
    GEMARKUNG("GEMARKUNG", "Gemarkungsbezeichnung"),
}
