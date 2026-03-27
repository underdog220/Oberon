package com.devloop.core.domain.enums

/**
 * Harte Domaenentrennung innerhalb der Oberon-Plattform.
 *
 * Jede Domaene hat eigene Workspaces, Memory, Policies und Audit-Logik.
 * Kein unkontrolliertes Mischen zwischen Domaenen.
 */
enum class OberonDomain {
    /** DevLoop, Coding, Infrastruktur, allgemeine Systementwicklung. */
    SYSTEM,
    /** Gutachten-Erstellung, Bewertung, Dictopic, Valiador. */
    GUTACHTEN,
}
