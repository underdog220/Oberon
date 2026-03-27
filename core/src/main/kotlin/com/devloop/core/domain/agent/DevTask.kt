package com.devloop.core.domain.agent

/**
 * One-shot task for a [CodingAgent].
 *
 * Prompt-centric with optional file attachments and tool permissions.
 */
data class DevTask(
    val id: String,
    /** Natural-language instruction for the agent. */
    val prompt: String,
    /** Owning project for logging / future context. */
    val projectId: String,
    /**
     * Optional override for Codex CLI sandbox mode (orchestrator / future automation).
     * When null, [AgentRunContext.codexSandbox] and target defaults apply.
     */
    val codexSandboxOverride: CodexSandboxMode? = null,
    /**
     * File paths relevant to this task. Agents may pass these as context
     * (e.g. `--file` flags for CLI agents, file content for API agents).
     * Paths are relative to the workspace root or absolute.
     */
    val files: List<String> = emptyList(),
    /**
     * Tool names the agent is allowed to use (e.g. "Read", "Edit", "Bash").
     * Empty = agent defaults. Agents that don't support tool restrictions ignore this.
     */
    val allowedTools: List<String> = emptyList(),
)
