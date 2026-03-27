package com.devloop.core.domain.repository

import com.devloop.core.domain.model.Project
import com.devloop.core.domain.model.ProjectId
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>
    suspend fun getProjectById(id: ProjectId): Project?
    suspend fun upsertProject(project: Project)
}

