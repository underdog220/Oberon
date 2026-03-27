package com.devloop.core.orchestration

import com.devloop.core.domain.enums.InputChannelType
import com.devloop.core.domain.enums.WorkspaceType
import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.model.WorkspaceId
import com.devloop.core.domain.repository.InputDraftRepository
import com.devloop.core.domain.repository.WorkspaceRepository

class UpdateDraftTextUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val inputDraftRepository: InputDraftRepository,
    private val ensureProjectStructureUseCase: EnsureProjectStructureUseCase
) {
    suspend fun updateDraftText(
        projectId: ProjectId,
        workspaceType: WorkspaceType,
        newText: String,
        channel: InputChannelType? = null,
    ) {
        ensureProjectStructureUseCase.ensureChatAndCursorEnabled(projectId)

        val workspace = workspaceRepository.getWorkspaceByType(projectId, workspaceType)
            ?: error("Workspace not found after ensure: projectId=$projectId, type=$workspaceType")

        val draft = inputDraftRepository.getDraft(projectId, workspace.id)
            ?: error("Draft not found after ensure: projectId=$projectId, workspaceId=${workspace.id}")

        val now = System.currentTimeMillis()
        val updatedDraft = draft.copy(
            text = newText,
            updatedAtEpochMillis = now,
            inputChannel = channel?.name ?: draft.inputChannel,
        )

        inputDraftRepository.upsertDraft(updatedDraft)
    }
}

