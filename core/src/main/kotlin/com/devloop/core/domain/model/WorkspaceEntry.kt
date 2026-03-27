package com.devloop.core.domain.model

import com.devloop.core.domain.enums.WorkspaceEntryType

data class WorkspaceEntry(
    val id: WorkspaceEntryId,
    val projectId: ProjectId,
    val workspaceId: WorkspaceId,
    val type: WorkspaceEntryType,
    val createdAtEpochMillis: Long,
    val payload: String
)

