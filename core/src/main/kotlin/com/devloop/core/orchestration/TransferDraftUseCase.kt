package com.devloop.core.orchestration

import com.devloop.core.domain.enums.TransferMode
import com.devloop.core.domain.enums.WorkspaceType
import com.devloop.core.domain.model.Transfer
import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.repository.InputDraftRepository
import com.devloop.core.domain.repository.TransferRepository
import com.devloop.core.domain.repository.WorkspaceRepository
import java.util.UUID

class TransferDraftUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val inputDraftRepository: InputDraftRepository,
    private val transferRepository: TransferRepository,
    private val ensureProjectStructureUseCase: EnsureProjectStructureUseCase
) {
    suspend fun transferText(
        projectId: ProjectId,
        fromWorkspaceType: WorkspaceType,
        toWorkspaceType: WorkspaceType,
        mode: TransferMode
    ): Transfer {
        ensureProjectStructureUseCase.ensureChatAndCursorEnabled(projectId)

        val fromWorkspace = workspaceRepository.getWorkspaceByType(projectId, fromWorkspaceType)
            ?: error("From workspace not found after ensure")
        val toWorkspace = workspaceRepository.getWorkspaceByType(projectId, toWorkspaceType)
            ?: error("To workspace not found after ensure")

        val sourceDraft = inputDraftRepository.getDraft(projectId, fromWorkspace.id)
            ?: error("Source draft not found after ensure")
        val targetDraft = inputDraftRepository.getDraft(projectId, toWorkspace.id)
            ?: error("Target draft not found after ensure")

        // Always snapshot source at transfer time.
        val snapshot = sourceDraft.text

        val newTargetText = when (mode) {
            TransferMode.COPY,
            TransferMode.OVERWRITE -> snapshot
            TransferMode.APPEND -> targetDraft.text + snapshot
        }

        val now = System.currentTimeMillis()
        val updatedTargetDraft = targetDraft.copy(
            text = newTargetText,
            updatedAtEpochMillis = now
        )
        inputDraftRepository.upsertDraft(updatedTargetDraft)

        val transfer = Transfer(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            fromWorkspaceId = fromWorkspace.id,
            toWorkspaceId = toWorkspace.id,
            mode = mode,
            createdAtEpochMillis = now,
            sourceDraftId = sourceDraft.id,
            transferredText = snapshot
        )

        transferRepository.insertTransfer(transfer)
        return transfer
    }
}

