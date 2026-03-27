package com.devloop.core.domain.agent

/** Stable ids for [CodingAgent] implementations and persisted primary-agent selection in settings. */
object CodingAgentIds {
    const val OPENAI_CODING = "openai-coding"
    const val CODEX_CLI = "codex-cli"
    const val CURSOR_CLI = "cursor-cli"
    const val CLAUDE_CODE_CLI = "claude-code-cli"

    /**
     * Maps values from [settings.json] to a known id. Unknown values fall back to [OPENAI_CODING]
     * (explicit UI / file must use [CODEX_CLI] or [CURSOR_CLI] for local CLI — no heuristic „silent CLI“).
     */
    fun normalizeStoredPrimaryId(raw: String): String =
        when (raw.trim()) {
            CODEX_CLI,
            "codex_cli",
            -> CODEX_CLI
            CURSOR_CLI,
            "cursor_cli",
            -> CURSOR_CLI
            CLAUDE_CODE_CLI,
            "claude_code_cli",
            "claude-cli",
            -> CLAUDE_CODE_CLI
            OPENAI_CODING -> OPENAI_CODING
            else -> OPENAI_CODING
        }
}
