package com.devloop.oberon.dsgvo

/**
 * Regex-basierter PII-Scanner fuer deutsche Dokumente.
 * Performance-Ziel: <50ms fuer typische Gutachtentexte.
 *
 * Erkennt: IBAN, Telefon, E-Mail, Adressen, Flurnummern,
 * Grundbuchblaetter, Katasterdaten, Gemarkungen, Personennamen
 * und Datumsangaben mit Personenbezug.
 */
class FastPiiScanner {

    // --- Regex-Patterns (vorkompiliert fuer Performance) ---

    private val ibanPattern = Regex(
        """DE\d{2}\s?\d{4}\s?\d{4}\s?\d{4}\s?\d{4}\s?\d{2}"""
    )

    private val telefonPattern = Regex(
        """(?:\+49|0049|0)\s?[\d\s/\-]{6,15}"""
    )

    private val emailPattern = Regex(
        """[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}"""
    )

    private val adressePattern = Regex(
        """[A-Z\u00C4\u00D6\u00DC][a-z\u00E4\u00F6\u00FC\u00DF]+(?:stra\u00DFe|str\.|weg|gasse|platz|allee|ring)\s+\d+[a-z]?"""
    )

    private val flurnummerPattern = Regex(
        """(?:Flurst[\u00FC|u]ck|Flur|Flst\.?)\s*(?:Nr\.?\s*)?\d+(?:/\d+)?""",
        RegexOption.IGNORE_CASE
    )

    private val grundbuchblattPattern = Regex(
        """(?:Grundbuch)?[Bb]latt\s*(?:Nr\.?\s*)?\d+"""
    )

    private val katasterPattern = Regex(
        """(?:Kataster|Liegenschafts)(?:amt|bezirk|nummer)?\s*[:\s]\s*\S+""",
        RegexOption.IGNORE_CASE
    )

    private val gemarkungPattern = Regex(
        """(?:Gemarkung|Gmkg\.?)\s+[A-Z\u00C4\u00D6\u00DC][a-z\u00E4\u00F6\u00FC\u00DF]+(?:\s[A-Z\u00C4\u00D6\u00DC][a-z\u00E4\u00F6\u00FC\u00DF]+)?"""
    )

    private val personPattern = Regex(
        """(?:Herr|Frau|Hr\.|Fr\.)\s+[A-Z\u00C4\u00D6\u00DC][a-z\u00E4\u00F6\u00FC\u00DF]+(?:\s+[A-Z\u00C4\u00D6\u00DC][a-z\u00E4\u00F6\u00FC\u00DF]+)?"""
    )

    private val personGebPattern = Regex(
        """(?:geb\.|geboren)\s+[A-Z\u00C4\u00D6\u00DC][a-z\u00E4\u00F6\u00FC\u00DF]+"""
    )

    /** Datumsmuster: DD.MM.YYYY oder DD.MM.YY */
    private val datumPattern = Regex(
        """\d{1,2}\.\d{1,2}\.\d{2,4}"""
    )

    /** Einfaches Namensmuster fuer Naehe-Erkennung bei Datum */
    private val namePattern = Regex(
        """[A-Z\u00C4\u00D6\u00DC][a-z\u00E4\u00F6\u00FC\u00DF]{2,}(?:\s+[A-Z\u00C4\u00D6\u00DC][a-z\u00E4\u00F6\u00FC\u00DF]{2,})?"""
    )

    /**
     * Scannt den uebergebenen Text auf personenbezogene Daten.
     * Gibt eine nach Startposition sortierte Liste von Treffern zurueck.
     */
    fun scan(text: String): List<PiiMatch> {
        val matches = mutableListOf<PiiMatch>()

        // Feste Muster mit hoher Konfidenz
        collectMatches(text, ibanPattern, PiiCategory.IBAN, 0.99, matches)
        collectMatches(text, telefonPattern, PiiCategory.TELEFON, 0.85, matches)
        collectMatches(text, emailPattern, PiiCategory.EMAIL, 0.99, matches)
        collectMatches(text, adressePattern, PiiCategory.ADRESSE, 0.90, matches)
        collectMatches(text, flurnummerPattern, PiiCategory.FLURNUMMER, 0.95, matches)
        collectMatches(text, grundbuchblattPattern, PiiCategory.GRUNDBUCHBLATT, 0.95, matches)
        collectMatches(text, katasterPattern, PiiCategory.KATASTER, 0.90, matches)
        collectMatches(text, gemarkungPattern, PiiCategory.GEMARKUNG, 0.90, matches)

        // Personennamen (etwas geringere Konfidenz wg. Heuristik)
        collectMatches(text, personPattern, PiiCategory.PERSON, 0.80, matches)
        collectMatches(text, personGebPattern, PiiCategory.PERSON, 0.85, matches)

        // Datum mit Name in der Naehe (innerhalb von 50 Zeichen)
        scanDatumMitName(text, matches)

        // Sortierung nach Startposition, dann nach Laenge (laengster Treffer zuerst)
        matches.sortWith(compareBy<PiiMatch> { it.startIndex }.thenByDescending { it.endIndex - it.startIndex })

        // Ueberlappende Treffer entfernen (laengster/konfidentester gewinnt)
        return removeOverlaps(matches)
    }

    /**
     * Sucht nach Datumsangaben, die innerhalb von 50 Zeichen einen Namen haben.
     */
    private fun scanDatumMitName(text: String, matches: MutableList<PiiMatch>) {
        for (datumMatch in datumPattern.findAll(text)) {
            val suchStart = maxOf(0, datumMatch.range.first - 50)
            val suchEnde = minOf(text.length, datumMatch.range.last + 51)
            val umgebung = text.substring(suchStart, suchEnde)

            // Pruefen ob ein Name in der Naehe ist
            val hatName = namePattern.findAll(umgebung).any { nameMatch ->
                // Name darf nicht das Datum selbst sein
                val absolutStart = suchStart + nameMatch.range.first
                absolutStart != datumMatch.range.first
            }

            if (hatName) {
                matches.add(
                    PiiMatch(
                        category = PiiCategory.DATUM_MIT_NAME,
                        originalText = datumMatch.value,
                        startIndex = datumMatch.range.first,
                        endIndex = datumMatch.range.last + 1,
                        confidence = 0.70,
                    )
                )
            }
        }
    }

    /**
     * Sammelt alle Regex-Treffer fuer ein Pattern und fuegt sie zur Liste hinzu.
     */
    private fun collectMatches(
        text: String,
        pattern: Regex,
        category: PiiCategory,
        confidence: Double,
        matches: MutableList<PiiMatch>,
    ) {
        for (match in pattern.findAll(text)) {
            matches.add(
                PiiMatch(
                    category = category,
                    originalText = match.value,
                    startIndex = match.range.first,
                    endIndex = match.range.last + 1,
                    confidence = confidence,
                )
            )
        }
    }

    /**
     * Entfernt ueberlappende Treffer. Bei Ueberlappung gewinnt der Treffer
     * mit hoeherer Konfidenz, bei Gleichstand der laengere.
     */
    private fun removeOverlaps(sorted: List<PiiMatch>): List<PiiMatch> {
        if (sorted.isEmpty()) return sorted

        val result = mutableListOf<PiiMatch>()
        var lastEnd = -1

        for (match in sorted) {
            if (match.startIndex >= lastEnd) {
                // Keine Ueberlappung
                result.add(match)
                lastEnd = match.endIndex
            } else {
                // Ueberlappung: nur ersetzen wenn bessere Konfidenz
                val vorheriger = result.last()
                if (match.confidence > vorheriger.confidence) {
                    result[result.lastIndex] = match
                    lastEnd = match.endIndex
                }
            }
        }
        return result
    }
}
