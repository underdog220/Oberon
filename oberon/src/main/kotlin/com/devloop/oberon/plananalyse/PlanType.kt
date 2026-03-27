package com.devloop.oberon.plananalyse

/**
 * Typen von Bauplaenen, die vom Plan-Analyse-Baustein erkannt werden koennen.
 */
enum class PlanType(val beschreibung: String) {
    GRUNDRISS("Grundriss (EG/OG/KG/DG)"),
    SCHNITT("Gebaeudeschnitt"),
    ANSICHT("Gebaeudeansicht"),
    LAGEPLAN("Amtlicher Lageplan"),
    FLURKARTE("Flurkarte/Liegenschaftskarte"),
    BEBAUUNGSPLAN("Bebauungsplan"),
    BAUZEICHNUNG("Allgemeine Bauzeichnung"),
    UNBEKANNT("Nicht identifiziert"),
}
