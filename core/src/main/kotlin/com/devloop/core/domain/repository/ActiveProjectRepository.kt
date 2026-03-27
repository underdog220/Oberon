package com.devloop.core.domain.repository

import com.devloop.core.domain.model.ProjectId

interface ActiveProjectRepository {
    suspend fun getLastActiveProjectId(): ProjectId?
    suspend fun setLastActiveProjectId(projectId: ProjectId)
}

