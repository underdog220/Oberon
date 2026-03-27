package com.devloop.core.domain.repository

import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.model.Transfer
import com.devloop.core.domain.model.WorkspaceId
import kotlinx.coroutines.flow.Flow

interface TransferRepository {
    suspend fun insertTransfer(transfer: Transfer)
    fun observeTransfers(projectId: ProjectId): Flow<List<Transfer>>
    fun observeTransfersFromWorkspace(
        projectId: ProjectId,
        fromWorkspaceId: WorkspaceId
    ): Flow<List<Transfer>>
}

