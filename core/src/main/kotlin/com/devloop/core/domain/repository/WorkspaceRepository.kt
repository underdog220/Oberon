package com.devloop.core.domain.repository

import com.devloop.core.domain.enums.WorkspaceType
import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.model.Workspace
import com.devloop.core.domain.model.WorkspaceId
import kotlinx.coroutines.flow.Flow

interface WorkspaceRepository {
    fun observeWorkspaces(projectId: ProjectId): Flow<List<Workspace>>
    suspend fun getWorkspaceById(id: WorkspaceId): Workspace?
    suspend fun getWorkspaceByType(projectId: ProjectId, type: WorkspaceType): Workspace?
    suspend fun upsertWorkspace(workspace: Workspace)
    suspend fun upsertWorkspaces(workspaces: List<Workspace>)
}

