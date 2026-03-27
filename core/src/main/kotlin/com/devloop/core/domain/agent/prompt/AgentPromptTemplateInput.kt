package com.devloop.core.domain.agent.prompt

/**
 * Kontext für die Aufbereitung des an einen Coding-Agenten gesendeten Prompts (nicht der Roh-Intent).
 *
 * [rawUserIntent] ist die eigentliche Nutzer-/Supervisor-Aufgabe; weitere Felder kommen aus DevLoop,
 * ohne erfundene Details — fehlende strukturierte Daten werden im Template explizit benannt.
 */
data class AgentPromptTemplateInput(
    val rawUserIntent: String,
    val projectName: String? = null,
    /** Local-CLI `repoRoot`, falls bekannt. */
    val localRepoRoot: String? = null,
    val integrationTargetLabel: String? = null,
    /** z. B. `read-only` / `workspace-write` für Codex-Sandbox. */
    val codexSandboxModeLabel: String? = null,
    /** When set and [com.devloop.core.domain.agent.trace.AgentTraceLogger] is enabled, Codex template steps are logged. */
    val traceRunId: String? = null,
    /**
     * Quelle des aufgelösten Nutzertextes (Resolver / Supervisor-Fallback), nur für Provenance-[PromptSegment.sourceDetail].
     */
    val payloadResolutionSource: String = "unknown",
)
