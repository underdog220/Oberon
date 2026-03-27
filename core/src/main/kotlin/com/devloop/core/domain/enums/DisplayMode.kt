package com.devloop.core.domain.enums

/**
 * Globale Darstellung von Supervisor-Verlauf und Meta-Infos (Desktop + Android Remote).
 */
enum class DisplayMode {
    /** Nur echte Inhalte (User / ChatGPT / Agent), ohne Zeitstempel und Systemzeilen. */
    PRODUCTIVE,

    /** Inhalte + kompakte Aktivitätszeilen + Zeitstempel (sofern nicht per Toggle überschrieben). */
    EXTENDED,

    /** Vollständige technische Infos inkl. Cursor-UI-Bridge und alle Systemmeldungen. */
    DEBUG,
}
