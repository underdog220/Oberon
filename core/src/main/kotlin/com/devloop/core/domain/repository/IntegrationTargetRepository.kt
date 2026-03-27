package com.devloop.core.domain.repository

import com.devloop.core.domain.model.IntegrationTarget
import com.devloop.core.domain.model.IntegrationTargetId
import com.devloop.core.domain.model.ProjectId
import kotlinx.coroutines.flow.Flow

interface IntegrationTargetRepository {
    fun observeTargets(projectId: ProjectId): Flow<List<IntegrationTarget>>
    suspend fun getTarget(id: IntegrationTargetId): IntegrationTarget?
    suspend fun upsertTarget(target: IntegrationTarget)
    suspend fun deleteTarget(id: IntegrationTargetId)
}
