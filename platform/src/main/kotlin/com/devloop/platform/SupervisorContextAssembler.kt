package com.devloop.platform

import com.devloop.core.domain.enums.ConversationRole
import com.devloop.core.domain.model.ConversationMessage
import com.devloop.core.domain.model.VirtualInstanceId
import com.devloop.core.domain.repository.ConversationRepository
import com.devloop.core.domain.repository.ResumeContextRepository
import java.util.logging.Logger

/**
 * Baut den OpenAI-Message-Thread aus persistierten Daten + Boost-Kontext.
 *
 * Ersetzt den bisherigen rein in-memory-basierten Ansatz.
 * Produziert eine Liste von Messages die an die OpenAI API gesendet werden kann.
 */
class SupervisorContextAssembler(
    private val conversations: ConversationRepository,
    private val resumeContexts: ResumeContextRepository,
    private val contextBooster: ContextBoosterService,
) {
    private val log = Logger.getLogger("SupervisorContextAssembler")

    /**
     * Assembliert den vollstaendigen Thread fuer eine virtuelle Instanz.
     *
     * @param virtualInstanceId Fokusraum
     * @param newUserMessage Neue Nachricht (falls vorhanden)
     * @param maxMessages Max. Anzahl historischer Messages
     * @return Liste von (role, content) Paaren fuer die OpenAI API
     */
    suspend fun assembleThread(
        virtualInstanceId: VirtualInstanceId,
        newUserMessage: String? = null,
        maxMessages: Int = 30,
    ): List<AssembledMessage> {
        val result = mutableListOf<AssembledMessage>()

        // 1. System-Preamble mit Boost-Kontext
        val boostCtx = contextBooster.assembleBoostContext(virtualInstanceId)
        val boostText = contextBooster.formatForInjection(boostCtx)
        if (boostText.isNotBlank()) {
            result += AssembledMessage(role = "system", content = boostText, source = "context_booster")
        }

        // 2. Historische Konversation (persistiert)
        val history = conversations.getRecentMessages(virtualInstanceId, maxMessages)
        for (msg in history) {
            val role = when (msg.role) {
                ConversationRole.USER -> "user"
                ConversationRole.ASSISTANT -> "assistant"
                ConversationRole.SYSTEM -> "system"
                ConversationRole.AGENT_OUTPUT -> "assistant"
            }
            result += AssembledMessage(role = role, content = msg.content, source = "history")
        }

        // 3. Neue User-Nachricht
        if (newUserMessage != null) {
            result += AssembledMessage(role = "user", content = newUserMessage, source = "new_input")
        }

        log.fine("Thread assembliert: ${result.size} Messages (${boostText.length} chars boost)")
        return result
    }
}

/**
 * Eine assemblierte Message fuer den OpenAI-Thread.
 */
data class AssembledMessage(
    val role: String,
    val content: String,
    /** Herkunft: "context_booster", "history", "new_input", "briefing" */
    val source: String,
)
