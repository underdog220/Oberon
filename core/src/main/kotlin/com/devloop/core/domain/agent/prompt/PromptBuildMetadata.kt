package com.devloop.core.domain.agent.prompt

/**
 * Kompakte Build-Metadaten für die Prompt-Provenance-UI.
 */
data class PromptBuildMetadata(
    val payloadResolutionSource: String,
    /** z. B. `CodexCliPromptStrategy`, `InternalCodingPromptComposer`, `ManualPassthrough`. */
    val strategyLabel: String,
    val normalizedAgentId: String,
    val integrationTargetLabel: String? = null,
    val codexSandboxLabel: String? = null,
    val portfolioHintsIncluded: Boolean = false,
    val agentHandoffIncluded: Boolean = false,
    val manualDirectAgentInput: Boolean = false,
)
