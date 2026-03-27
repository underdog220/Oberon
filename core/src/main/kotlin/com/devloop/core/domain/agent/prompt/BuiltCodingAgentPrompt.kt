package com.devloop.core.domain.agent.prompt

/**
 * Trennung: [rawUserIntent] = ursprüngliche Nutzer-/Supervisor-Aufgabe (Handoff, Transfer, Auditing);
 * [promptForAgent] = tatsächlich an den Prozess/API zu sendender Text.
 *
 * [segments] und [metadata] dienen ausschließlich der UI-Provenance — keine Marker im Sendetext.
 */
data class BuiltCodingAgentPrompt(
    val promptForAgent: String,
    val rawUserIntent: String,
    val assemblyFlags: AgentPromptAssemblyFlags = AgentPromptAssemblyFlags(),
    /** Characters appended from portfolio / supervisor hints (0 if none). */
    val portfolioAppendixCharCount: Int = 0,
    val segments: List<PromptSegment> = emptyList(),
    val metadata: PromptBuildMetadata? = null,
)
