package com.devloop.core.orchestration

import com.devloop.core.domain.enums.TransferMode
import com.devloop.core.domain.enums.WorkspaceType
import com.devloop.core.domain.model.InputDraft
import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.model.Workspace
import com.devloop.core.domain.model.WorkspaceId
import com.devloop.core.domain.repository.InputDraftRepository
import com.devloop.core.domain.repository.WorkspaceRepository
import java.util.UUID

/**
 * Ensures the project has the MVP-required structure:
 * - Workspaces: CHATGPT + CURSOR
 * - InputDrafts: one per workspace (empty text if missing)
 */
class EnsureProjectStructureUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val inputDraftRepository: InputDraftRepository
) {
    suspend fun ensureChatAndCursorEnabled(projectId: ProjectId) {
        ensureWorkspaceAndDraft(projectId, WorkspaceType.CHATGPT)
        ensureWorkspaceAndDraft(projectId, WorkspaceType.CURSOR)
    }

    private suspend fun ensureWorkspaceAndDraft(
        projectId: ProjectId,
        workspaceType: WorkspaceType
    ) {
        val existingWorkspace = workspaceRepository.getWorkspaceByType(projectId, workspaceType)
        val workspaceId = existingWorkspace?.id ?: workspaceIdFor(projectId, workspaceType)

        if (existingWorkspace == null) {
            val now = System.currentTimeMillis()
            val workspace = Workspace(
                id = workspaceId,
                projectId = projectId,
                type = workspaceType,
                displayName = workspaceType.name,
                createdAtEpochMillis = now
            )
            workspaceRepository.upsertWorkspace(workspace)
        }

        val draft = inputDraftRepository.getDraft(projectId, workspaceId)
        if (draft == null) {
            val now = System.currentTimeMillis()
            val draftId = inputDraftIdFor(projectId, workspaceType)
            val emptyDraft = InputDraft(
                id = draftId,
                projectId = projectId,
                workspaceId = workspaceId,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                text = "",
                inputChannel = null
            )
            inputDraftRepository.upsertDraft(emptyDraft)
        }
    }

    private fun workspaceIdFor(projectId: ProjectId, workspaceType: WorkspaceType): WorkspaceId =
        "${projectId}-${workspaceType.name}"

    private fun inputDraftIdFor(projectId: ProjectId, workspaceType: WorkspaceType): String =
        "${projectId}-${workspaceType.name}-draft"
}

