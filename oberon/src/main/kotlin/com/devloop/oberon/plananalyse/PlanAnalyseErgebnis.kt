package com.devloop.oberon.plananalyse

/**
 * Ergebnis einer Plan-Analyse (Vision + Flaechenberechnung).
 */
data class PlanAnalyseErgebnis(
    val id: String = java.util.UUID.randomUUID().toString(),
    val planType: PlanType,
    val massstab: String?,
    val geschoss: String?,
    val erkannteRaeume: List<ErkannterRaum>,
    val grundstuecksflaeche: Double?,
    val ueberbauteFlaeche: Double?,
    val din277: FlaechenRechner.Din277Ergebnis?,
    val woflv: FlaechenRechner.WoflvErgebnis?,
    val hinweise: List<String>,
    val rohdatenKi: String?,  // KI-Rohantwort fuer Audit
)

/**
 * Ein einzelner erkannter Raum aus der Plan-Analyse.
 */
data class ErkannterRaum(
    val bezeichnung: String,
    val flaeche: Double?,       // m² wenn erkannt
    val geschoss: String?,
    val istDachschraege: Boolean = false,
)
