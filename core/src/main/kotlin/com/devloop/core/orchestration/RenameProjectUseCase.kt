package com.devloop.core.orchestration

import com.devloop.core.domain.model.Project
import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.repository.ProjectRepository

class RenameProjectUseCase(
    private val projectRepository: ProjectRepository
) {
    suspend fun renameProject(projectId: ProjectId, newName: String): Project {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "Project name must not be empty" }

        val existing = projectRepository.getProjectById(projectId)
            ?: throw IllegalStateException("Project not found: id=$projectId")

        val updated = existing.copy(name = trimmed)
        projectRepository.upsertProject(updated)
        return updated
    }
}

