package com.devloop.core.domain.agent

/**
 * Pluggable backend for “run this prompt and give me text back”.
 *
 * Higher-level orchestration can wrap [runTask] for queues, streaming, or review steps later.
 */
interface CodingAgent {
    val id: String
    val displayName: String

    suspend fun runTask(task: DevTask, context: AgentRunContext): Result<AgentResponse>
}
