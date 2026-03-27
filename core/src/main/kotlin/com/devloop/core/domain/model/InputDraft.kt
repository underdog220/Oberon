package com.devloop.core.domain.model

import com.devloop.core.domain.enums.InputChannelType

data class InputDraft(
    val id: InputDraftId,
    val projectId: ProjectId,
    val workspaceId: WorkspaceId,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val text: String,
    /**
     * Persisted as string for DB compatibility. Use [channelType] for typed access.
     */
    val inputChannel: String? = null,
) {
    /** Typed access to input channel. */
    val channelType: InputChannelType get() = InputChannelType.fromString(inputChannel)
}

