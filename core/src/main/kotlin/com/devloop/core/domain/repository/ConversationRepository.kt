package com.devloop.core.domain.repository

import com.devloop.core.domain.model.ConversationMessage
import com.devloop.core.domain.model.ConversationMessageId
import com.devloop.core.domain.model.VirtualInstanceId
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun observeMessages(virtualInstanceId: VirtualInstanceId): Flow<List<ConversationMessage>>
    suspend fun getRecentMessages(virtualInstanceId: VirtualInstanceId, limit: Int): List<ConversationMessage>
    suspend fun insertMessage(message: ConversationMessage)
    suspend fun getMessageById(id: ConversationMessageId): ConversationMessage?
    suspend fun getMessageCount(virtualInstanceId: VirtualInstanceId): Int
}
