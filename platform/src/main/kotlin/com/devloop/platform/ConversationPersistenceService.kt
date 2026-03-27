package com.devloop.platform

import com.devloop.core.domain.enums.ActivityState
import com.devloop.core.domain.enums.ConversationRole
import com.devloop.core.domain.enums.ContentType
import com.devloop.core.domain.model.ConversationMessage
import com.devloop.core.domain.model.ResumeContext
import com.devloop.core.domain.model.VirtualInstanceId
import com.devloop.core.domain.repository.ConversationRepository
import com.devloop.core.domain.repository.ResumeContextRepository
import java.util.UUID
import java.util.logging.Logger

/**
 * Persistiert Konversationsnachrichten und aktualisiert den Resume-Kontext.
 *
 * Wird von SupervisorChatManager nach jedem Roundtrip aufgerufen.
 */
class ConversationPersistenceService(
    val conversations: ConversationRepository,
    private val resumeContexts: ResumeContextRepository,
) {
    private val log = Logger.getLogger("ConversationPersistenceService")

    /**
     * Persistiert eine Nachricht fuer den gegebenen Fokusraum.
     */
    suspend fun persistMessage(
        virtualInstanceId: VirtualInstanceId,
        role: ConversationRole,
        content: String,
        metadata: String = "{}",
    ) {
        val msg = ConversationMessage(
            id = UUID.randomUUID().toString(),
            virtualInstanceId = virtualInstanceId,
            role = role,
            content = content,
            contentType = ContentType.TEXT,
            metadata = metadata,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        conversations.insertMessage(msg)
    }

    /**
     * Aktualisiert den Resume-Kontext nach einem wichtigen Ereignis
     * (z. B. Ende einer Session, Agent-Lauf abgeschlossen).
     */
    suspend fun snapshotResumeState(
        virtualInstanceId: VirtualInstanceId,
        activityState: ActivityState,
        resumeHints: String,
        threadSnapshotJson: String = "[]",
        contextJson: String = "{}",
    ) {
        val recent = conversations.getRecentMessages(virtualInstanceId, 1)
        val lastMsgId = recent.lastOrNull()?.id

        resumeContexts.upsert(
            ResumeContext(
                virtualInstanceId = virtualInstanceId,
                lastConversationMessageId = lastMsgId,
                openAiThreadSnapshot = threadSnapshotJson,
                activityState = activityState,
                resumeHints = resumeHints,
                lastContextJson = contextJson,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        )
        log.info("Resume-Kontext aktualisiert fuer $virtualInstanceId: $activityState")
    }
}
