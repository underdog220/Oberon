package com.devloop.core.orchestration

import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.repository.ActiveProjectRepository

class GetLastActiveProjectUseCase(
    private val activeProjectRepository: ActiveProjectRepository
) {
    suspend fun getLastActiveProjectId(): ProjectId? = activeProjectRepository.getLastActiveProjectId()
}

