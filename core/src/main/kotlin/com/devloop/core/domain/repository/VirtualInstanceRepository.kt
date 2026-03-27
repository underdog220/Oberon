package com.devloop.core.domain.repository

import com.devloop.core.domain.enums.VirtualInstanceStatus
import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.model.VirtualInstance
import com.devloop.core.domain.model.VirtualInstanceId
import kotlinx.coroutines.flow.Flow

interface VirtualInstanceRepository {
    fun observeInstances(projectId: ProjectId): Flow<List<VirtualInstance>>
    suspend fun getAll(): List<VirtualInstance>
    suspend fun getById(id: VirtualInstanceId): VirtualInstance?
    suspend fun getByProject(projectId: ProjectId): List<VirtualInstance>
    suspend fun upsert(instance: VirtualInstance)
    suspend fun updateStatus(id: VirtualInstanceId, status: VirtualInstanceStatus)
}
