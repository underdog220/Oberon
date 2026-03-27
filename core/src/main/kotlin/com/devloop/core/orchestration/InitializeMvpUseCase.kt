package com.devloop.core.orchestration

import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.first

/**
 * Initializes a minimal MVP state:
 * - create a default project if the database is empty
 * - ensure CHATGPT + CURSOR workspaces and drafts exist
 */
class InitializeMvpUseCase(
    private val projectRepository: ProjectRepository,
    private val ensureProjectStructureUseCase: EnsureProjectStructureUseCase,
    private val createProjectUseCase: CreateProjectUseCase
) {
    suspend fun ensureAtLeastOneProjectExists(defaultProjectName: String = "Demo"): ProjectId {
        val projects = projectRepository.observeProjects().first()
        if (projects.isNotEmpty()) {
            // Pick the newest project.
            return projects.first().id
        }

        val createdId = createProjectUseCase.createProject(defaultProjectName)
        ensureProjectStructureUseCase.ensureChatAndCursorEnabled(createdId)
        return createdId
    }
}

