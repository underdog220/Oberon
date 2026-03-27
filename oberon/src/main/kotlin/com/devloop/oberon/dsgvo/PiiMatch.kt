package com.devloop.oberon.dsgvo

/**
 * Einzelner Treffer des PII-Scanners.
 * Enthaelt Kategorie, Originaltext, Position und Konfidenz.
 */
data class PiiMatch(
    val category: PiiCategory,
    val originalText: String,
    val startIndex: Int,
    val endIndex: Int,
    /** Konfidenz des Treffers (0.0 = unsicher, 1.0 = sicher) */
    val confidence: Double,
)
