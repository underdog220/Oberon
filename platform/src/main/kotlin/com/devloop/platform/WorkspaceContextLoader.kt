package com.devloop.platform

import com.devloop.core.domain.model.ConversationMessage
import com.devloop.core.domain.model.ResumeContext
import com.devloop.core.domain.model.VirtualInstance
import com.devloop.core.domain.model.VirtualInstanceId
import com.devloop.core.domain.repository.ConversationRepository
import com.devloop.core.domain.repository.ResumeContextRepository
import com.devloop.core.domain.repository.VirtualInstanceRepository
import java.util.logging.Logger

/**
 * Laedt den vollstaendigen Kontext eines Fokusraums bei Startup/Resume.
 *
 * Wird aufgerufen wenn ein Projekt geoeffnet oder ein Fokusraum gewechselt wird.
 * Rekonstruiert den Arbeitszustand aus persistierten Daten.
 */
class WorkspaceContextLoader(
    private val instances: VirtualInstanceRepository,
    private val conversations: ConversationRepository,
    private val resumeContexts: ResumeContextRepository,
    private val contextBooster: ContextBoosterService,
) {
    private val log = Logger.getLogger("WorkspaceContextLoader")

    /**
     * Laedt den vollstaendigen Kontext fuer einen Fokusraum.
     */
    suspend fun loadForResume(virtualInstanceId: VirtualInstanceId): WorkspaceLoadResult {
        val instance = instances.getById(virtualInstanceId)
            ?: return WorkspaceLoadResult(found = false)

        val resume = resumeContexts.getForInstance(virtualInstanceId)
        val recentMessages = conversations.getRecentMessages(virtualInstanceId, 50)
        val boostContext = contextBooster.assembleBoostContext(virtualInstanceId)

        log.info("Workspace geladen: ${instance.label} (${recentMessages.size} msgs, resume=${resume != null})")

        return WorkspaceLoadResult(
            found = true,
            instance = instance,
            resumeContext = resume,
            recentMessages = recentMessages,
            boostContext = boostContext,
        )
    }
}

data class WorkspaceLoadResult(
    val found: Boolean,
    val instance: VirtualInstance? = null,
    val resumeContext: ResumeContext? = null,
    val recentMessages: List<ConversationMessage> = emptyList(),
    val boostContext: BoostContext = BoostContext.EMPTY,
)
