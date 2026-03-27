package com.devloop.oberon.plananalyse

/**
 * Flaechenberechnung nach DIN 277:2021 und WoFlV.
 * Regelbasiert — kein KI-Einfluss.
 */
object FlaechenRechner {

    // -- DIN 277:2021 ----------------------------------------------------------

    data class Din277Eingabe(
        val raeume: List<RaumFlaeche>,
    )

    data class RaumFlaeche(
        val bezeichnung: String,
        val flaeche: Double,        // m²
        val typ: Din277RaumTyp,
        val geschoss: String = "",  // EG, OG, KG, DG
    )

    enum class Din277RaumTyp {
        NUF_WOHNEN,        // Nutzungsflaeche Wohnen
        NUF_BUERO,         // Nutzungsflaeche Buero
        NUF_GEWERBE,       // Nutzungsflaeche Gewerbe
        NUF_SONSTIGE,      // Nutzungsflaeche Sonstige
        VF,                // Verkehrsflaeche (Flur, Treppenhaus)
        TF,                // Technische Funktionsflaeche (Heizung, Technik)
        KGF,               // Konstruktions-Grundflaeche (Waende, Stuetzen)
    }

    data class Din277Ergebnis(
        val bgf: Double,    // Brutto-Grundflaeche
        val nrf: Double,    // Netto-Raumflaeche = NUF + TF + VF
        val nuf: Double,    // Nutzungsflaeche (alle NUF-Typen)
        val tf: Double,     // Technische Funktionsflaeche
        val vf: Double,     // Verkehrsflaeche
        val kgf: Double,    // Konstruktions-Grundflaeche
        val einzelpositionen: List<String>,  // Nachvollziehbare Auflistung
    )

    fun berechneDin277(eingabe: Din277Eingabe): Din277Ergebnis {
        var nuf = 0.0; var tf = 0.0; var vf = 0.0; var kgf = 0.0
        val positionen = mutableListOf<String>()

        for (raum in eingabe.raeume) {
            when (raum.typ) {
                Din277RaumTyp.NUF_WOHNEN, Din277RaumTyp.NUF_BUERO,
                Din277RaumTyp.NUF_GEWERBE, Din277RaumTyp.NUF_SONSTIGE -> {
                    nuf += raum.flaeche
                    positionen.add("NUF: ${raum.bezeichnung} (${raum.geschoss}) = ${"%.2f".format(raum.flaeche)} m\u00B2")
                }
                Din277RaumTyp.VF -> {
                    vf += raum.flaeche
                    positionen.add("VF: ${raum.bezeichnung} (${raum.geschoss}) = ${"%.2f".format(raum.flaeche)} m\u00B2")
                }
                Din277RaumTyp.TF -> {
                    tf += raum.flaeche
                    positionen.add("TF: ${raum.bezeichnung} (${raum.geschoss}) = ${"%.2f".format(raum.flaeche)} m\u00B2")
                }
                Din277RaumTyp.KGF -> {
                    kgf += raum.flaeche
                    positionen.add("KGF: ${raum.bezeichnung} (${raum.geschoss}) = ${"%.2f".format(raum.flaeche)} m\u00B2")
                }
            }
        }

        val nrf = nuf + tf + vf
        val bgf = nrf + kgf

        positionen.add("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500")
        positionen.add("NUF gesamt: ${"%.2f".format(nuf)} m\u00B2")
        positionen.add("TF gesamt:  ${"%.2f".format(tf)} m\u00B2")
        positionen.add("VF gesamt:  ${"%.2f".format(vf)} m\u00B2")
        positionen.add("NRF (NUF+TF+VF): ${"%.2f".format(nrf)} m\u00B2")
        positionen.add("KGF gesamt: ${"%.2f".format(kgf)} m\u00B2")
        positionen.add("BGF (NRF+KGF): ${"%.2f".format(bgf)} m\u00B2")

        return Din277Ergebnis(bgf, nrf, nuf, tf, vf, kgf, positionen)
    }

    // -- WoFlV (Wohnflaechenverordnung) ----------------------------------------

    data class WoflvEingabe(
        val raeume: List<WoflvRaum>,
    )

    data class WoflvRaum(
        val bezeichnung: String,
        val grundflaeche: Double,       // m² (volle Flaeche)
        val typ: WoflvRaumTyp,
        val dachschraegeUnter1m: Double = 0.0,   // m² unter 1m Hoehe
        val dachschraege1bis2m: Double = 0.0,     // m² zwischen 1-2m Hoehe
        val geschoss: String = "",
    )

    enum class WoflvRaumTyp {
        VOLLWERTIG,         // Wohnraeume (100%)
        BALKON_TERRASSE,    // 25% (einfach) oder 50% (hochwertig)
        WINTERGARTEN,       // 50% (unbeheizt) oder 100% (beheizt)
        KELLER,             // 0% (nicht zur Wohnflaeche)
        NEBENRAUM,          // Je nach Nutzung 0-100%
        GARAGE,             // 0% (nicht zur Wohnflaeche)
    }

    data class WoflvErgebnis(
        val wohnflaeche: Double,
        val einzelpositionen: List<String>,
    )

    fun berechneWoflv(eingabe: WoflvEingabe, balkonFaktor: Double = 0.25): WoflvErgebnis {
        var gesamt = 0.0
        val positionen = mutableListOf<String>()

        for (raum in eingabe.raeume) {
            val anrechenbar: Double
            val details: String

            when (raum.typ) {
                WoflvRaumTyp.VOLLWERTIG -> {
                    // Dachschraegen beruecksichtigen
                    val vollFlaeche = raum.grundflaeche - raum.dachschraegeUnter1m - raum.dachschraege1bis2m
                    val halb = raum.dachschraege1bis2m * 0.5
                    // Unter 1m = 0%
                    anrechenbar = vollFlaeche + halb
                    details = if (raum.dachschraege1bis2m > 0 || raum.dachschraegeUnter1m > 0) {
                        "${"%.2f".format(vollFlaeche)} m\u00B2 (100%) + ${"%.2f".format(raum.dachschraege1bis2m)} m\u00B2 (50%) + ${"%.2f".format(raum.dachschraegeUnter1m)} m\u00B2 (0%)"
                    } else {
                        "${"%.2f".format(raum.grundflaeche)} m\u00B2 (100%)"
                    }
                }
                WoflvRaumTyp.BALKON_TERRASSE -> {
                    anrechenbar = raum.grundflaeche * balkonFaktor
                    details = "${"%.2f".format(raum.grundflaeche)} m\u00B2 x ${(balkonFaktor * 100).toInt()}%"
                }
                WoflvRaumTyp.WINTERGARTEN -> {
                    anrechenbar = raum.grundflaeche * 0.5
                    details = "${"%.2f".format(raum.grundflaeche)} m\u00B2 x 50% (unbeheizt)"
                }
                WoflvRaumTyp.KELLER, WoflvRaumTyp.GARAGE -> {
                    anrechenbar = 0.0
                    details = "${"%.2f".format(raum.grundflaeche)} m\u00B2 (nicht anrechenbar)"
                }
                WoflvRaumTyp.NEBENRAUM -> {
                    anrechenbar = 0.0
                    details = "${"%.2f".format(raum.grundflaeche)} m\u00B2 (Nebenraum, nicht anrechenbar)"
                }
            }

            gesamt += anrechenbar
            positionen.add("${raum.bezeichnung} (${raum.geschoss}): $details = ${"%.2f".format(anrechenbar)} m\u00B2")
        }

        positionen.add("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500")
        positionen.add("Wohnflaeche nach WoFlV: ${"%.2f".format(gesamt)} m\u00B2")

        return WoflvErgebnis(gesamt, positionen)
    }
}
