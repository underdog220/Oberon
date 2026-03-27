package com.devloop.core.domain.model

import com.devloop.core.domain.enums.ContentType
import com.devloop.core.domain.enums.ConversationRole

/**
 * Persistente Konversationsnachricht innerhalb eines Fokusraums.
 */
data class ConversationMessage(
    val id: ConversationMessageId,
    val virtualInstanceId: VirtualInstanceId,
    val role: ConversationRole,
    val content: String,
    val contentType: ContentType = ContentType.TEXT,
    /** JSON: sourceAgentId, parseMode, etc. */
    val metadata: String = "{}",
    val createdAtEpochMillis: Long,
    val domain: String = "SYSTEM",
)
