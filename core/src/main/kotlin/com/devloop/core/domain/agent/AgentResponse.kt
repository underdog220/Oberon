package com.devloop.core.domain.agent

/**
 * Successful completion of [CodingAgent.runTask]. Failures use [Result.failure] instead.
 */
data class AgentResponse(
    val text: String,
    val agentId: String,
    val agentDisplayName: String,
    val durationMs: Long,
    /** Character length of [text] (trimmed payload). */
    val outputLength: Int,
    /** Set for Cursor CLI etc.; null for API-only agents or if not collected. */
    val executionDetails: CodingAgentExecutionDetails? = null,
)
