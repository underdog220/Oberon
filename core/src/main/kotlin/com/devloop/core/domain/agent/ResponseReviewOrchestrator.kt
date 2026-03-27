package com.devloop.core.domain.agent

/**
 * Orchestrates an optional second-pass review of a [CodingAgent] response.
 *
 * After the primary agent completes, the review agent receives the original
 * prompt + the primary response, and produces a [ReviewResponse] with:
 * - `ok = true`: response is acceptable, proceed
 * - `ok = false`: response has issues, text contains feedback
 *
 * Usage is optional — the supervisor decides whether to apply review.
 */
class ResponseReviewOrchestrator(
    private val reviewAgent: CodingAgent,
) {

    /**
     * Reviews a primary agent's response.
     *
     * @param originalPrompt the prompt that was sent to the primary agent
     * @param primaryResponse the response from the primary agent
     * @param context the agent run context (reused for the review agent)
     * @return ReviewResponse with the reviewer's assessment
     */
    suspend fun review(
        originalPrompt: String,
        primaryResponse: AgentResponse,
        context: AgentRunContext,
    ): Result<ReviewResponse> {
        val reviewPrompt = buildReviewPrompt(originalPrompt, primaryResponse.text)
        val reviewTask = DevTask(
            id = "review-${primaryResponse.agentId}-${System.currentTimeMillis()}",
            prompt = reviewPrompt,
            projectId = context.projectId,
        )

        return reviewAgent.runTask(reviewTask, context).map { response ->
            val ok = !response.text.contains("[ISSUE]", ignoreCase = true) &&
                !response.text.contains("[REJECT]", ignoreCase = true)
            ReviewResponse(
                reviewerAgentId = reviewAgent.id,
                text = response.text,
                ok = ok,
            )
        }
    }

    private fun buildReviewPrompt(originalPrompt: String, primaryOutput: String): String = buildString {
        appendLine("Du bist ein Code-Reviewer. Pruefe die folgende Agent-Antwort auf Korrektheit und Qualitaet.")
        appendLine()
        appendLine("=== Originaler Auftrag ===")
        appendLine(originalPrompt.take(3000))
        appendLine()
        appendLine("=== Agent-Antwort ===")
        appendLine(primaryOutput.take(5000))
        appendLine()
        appendLine("=== Deine Aufgabe ===")
        appendLine("1. Pruefe ob die Antwort den Auftrag korrekt erfuellt.")
        appendLine("2. Pruefe auf offensichtliche Fehler, fehlende Teile, oder Sicherheitsprobleme.")
        appendLine("3. Wenn alles in Ordnung ist, antworte mit: [OK] gefolgt von einer kurzen Bestaetigung.")
        appendLine("4. Wenn es Probleme gibt, antworte mit: [ISSUE] gefolgt von einer Beschreibung der Probleme.")
    }
}
