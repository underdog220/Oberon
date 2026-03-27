package com.devloop.core.domain.model

import com.devloop.core.domain.enums.WorkspaceType

data class Workspace(
    val id: WorkspaceId,
    val projectId: ProjectId,
    val type: WorkspaceType,
    val displayName: String,
    val createdAtEpochMillis: Long
)

