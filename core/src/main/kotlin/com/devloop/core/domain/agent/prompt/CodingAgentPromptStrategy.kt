package com.devloop.core.domain.agent.prompt

/**
 * Agentenspezifische Aufbereitung des Coding-Prompts. Neue Agenten: eigene Implementierung registrieren in
 * [CodingAgentPromptStrategyRegistry]; der Desktop-[com.devloop.desktop.util.CodingAgentPromptAssembler] nutzt das Register.
 */
interface CodingAgentPromptStrategy {
    fun supports(normalizedAgentId: String): Boolean
    fun build(input: AgentPromptTemplateInput): BuiltCodingAgentPrompt
}
