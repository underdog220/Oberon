package com.devloop.core.domain.agent

import com.devloop.core.domain.model.IntegrationTarget

/**
 * Runtime context supplied by the orchestrator (UI/desktop). Optional fields are agent-specific.
 */
data class AgentRunContext(
    val projectId: String,
    /** Used by [CodingAgentIds.CURSOR_CLI] / [CodingAgentIds.CODEX_CLI]; may be null if the user chose API-only flow. */
    val integrationTarget: IntegrationTarget? = null,
    /** Active CLI workspace config JSON when a local CLI target is selected (Cursor / Codex adapters). */
    val cliTargetConfigJson: String? = null,
    /**
     * Effective Codex sandbox for this run when using [CodingAgentIds.CODEX_CLI].
     * Null lets the Codex adapter use the persisted Local-CLI target default (read-only vs workspace-write).
     */
    val codexSandbox: CodexSandboxMode? = null,
    /** Correlates CLI/API agent runs with [com.devloop.core.domain.agent.trace.AgentTraceLogger] blocks when set. */
    val agentTraceRunId: String? = null,
    /**
     * True when the prompt came from the UI field „Prompt an Coding-Agent“ (resolver `manualDirectAgentInput`):
     * [com.devloop.bridge.agent.OpenAiCodingAgent] uses a single user message (no fixed system prompt).
     */
    val manualDirectAgentInput: Boolean = false,
)
