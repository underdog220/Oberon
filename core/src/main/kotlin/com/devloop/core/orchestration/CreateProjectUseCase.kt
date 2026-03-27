package com.devloop.core.orchestration

import com.devloop.core.domain.enums.ProjectStatus
import com.devloop.core.domain.model.Project
import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.repository.ProjectRepository
import java.util.UUID

class CreateProjectUseCase(
    private val projectRepository: ProjectRepository
) {
    suspend fun createProject(name: String): ProjectId {
        val now = System.currentTimeMillis()
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = name,
            status = ProjectStatus.ACTIVE,
            createdAtEpochMillis = now
        )
        projectRepository.upsertProject(project)
        return project.id
    }
}

