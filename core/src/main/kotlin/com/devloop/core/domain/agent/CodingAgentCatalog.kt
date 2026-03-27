package com.devloop.core.domain.agent

/**
 * Registrierte Coding-Agent-Implementierungen für UI und Einstellungen.
 * Reihenfolge = Darstellung im Ziel-Agent-Dropdown.
 */
data class RegisteredCodingAgent(
    val id: String,
    /** Kurzbezeichnung im UI (entspricht den [CodingAgent.displayName]-Namen). */
    val displayName: String,
)

object CodingAgentCatalog {
    val registered: List<RegisteredCodingAgent> = listOf(
        RegisteredCodingAgent(CodingAgentIds.OPENAI_CODING, "OpenAI Coding-Agent"),
        RegisteredCodingAgent(CodingAgentIds.CODEX_CLI, "Codex CLI"),
        RegisteredCodingAgent(CodingAgentIds.CLAUDE_CODE_CLI, "Claude Code CLI"),
        RegisteredCodingAgent(CodingAgentIds.CURSOR_CLI, "Cursor CLI (experimentell)"),
    )

    fun displayNameForId(id: String): String {
        val n = CodingAgentIds.normalizeStoredPrimaryId(id)
        return registered.firstOrNull { it.id == n }?.displayName
            ?: registered.first { it.id == CodingAgentIds.OPENAI_CODING }.displayName
    }
}
