package com.devloop.core.domain.agent.prompt

/**
 * Kategorie eines Prompt-Segments für die Provenance-Ansicht (nicht im gesendeten Text markiert).
 */
enum class PromptSourceKind {
    /** Aufgelöster Nutzertext nach Payload-Auflösung (Chat vs. Cursor-Feld). */
    PAYLOAD_RESOLUTION,

    /** Unveränderter Nutzerauftrag (identisch zu [BuiltCodingAgentPrompt.rawUserIntent] in der Regel). */
    RAW_USER_INTENT,

    /** Agent-spezifisches Template (z. B. Codex-Rahmen, Rolle, Struktur). */
    AGENT_TEMPLATE,

    /** Projektname / Projektzeile im Template. */
    PROJECT_CONTEXT,

    /** Target-Label, Repo-Pfad o. Ä. */
    TARGET_CONTEXT,

    /** Sandbox-Modus o. ä. Laufkontext. */
    SANDBOX_CONTEXT,

    /** Angehängte Portfolio-/Supervisor-Hinweise. */
    PORTFOLIO_HINTS,

    /** Strukturierter Agent-Handoff-Kontext (projektbezogen). */
    AGENT_HANDOFF,

    /** Desktop Legacy-Composer (Rahmen um Chat→Agent). */
    LEGACY_COMPOSER,

    /** 1:1 aus UI-Feld, keine DevLoop-Aufbereitung. */
    PASSTHROUGH,
}
