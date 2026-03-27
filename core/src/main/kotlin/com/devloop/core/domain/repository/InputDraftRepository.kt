package com.devloop.core.domain.repository

import com.devloop.core.domain.model.InputDraft
import com.devloop.core.domain.model.InputDraftId
import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.model.WorkspaceId
import kotlinx.coroutines.flow.Flow

interface InputDraftRepository {
    fun observeDraft(projectId: ProjectId, workspaceId: WorkspaceId): Flow<InputDraft?>
    suspend fun getDraft(projectId: ProjectId, workspaceId: WorkspaceId): InputDraft?
    suspend fun getDraftById(id: InputDraftId): InputDraft?
    suspend fun upsertDraft(draft: InputDraft)
}

