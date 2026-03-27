package com.devloop.core.orchestration

import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.repository.ActiveProjectRepository

class SetLastActiveProjectUseCase(
    private val activeProjectRepository: ActiveProjectRepository
) {
    suspend fun setLastActiveProjectId(projectId: ProjectId) {
        activeProjectRepository.setLastActiveProjectId(projectId)
    }
}

