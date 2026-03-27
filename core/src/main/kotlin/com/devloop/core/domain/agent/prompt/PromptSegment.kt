package com.devloop.core.domain.agent.prompt

/**
 * Ein sichtbarer Block der Prompt-Zusammensetzung (nur UI / Datenmodell — nicht im Sendetext).
 */
data class PromptSegment(
    val id: String,
    val title: String,
    val sourceKind: PromptSourceKind,
    /** Menschenlesbare Herkunft, z. B. `CodingAgentSendPayloadResolver → chatInput`. */
    val sourceDetail: String,
    val text: String,
    val includedInFinalPrompt: Boolean = true,
)
