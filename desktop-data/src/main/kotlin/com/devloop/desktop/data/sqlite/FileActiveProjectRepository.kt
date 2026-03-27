package com.devloop.desktop.data.sqlite

import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.repository.ActiveProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class FileActiveProjectRepository(
    private val baseDir: Path
) : ActiveProjectRepository {

    private val file: Path get() = baseDir.resolve("active_project_id.txt")

    override suspend fun getLastActiveProjectId(): ProjectId? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        file.readText().trim().takeIf { it.isNotEmpty() }
    }

    override suspend fun setLastActiveProjectId(projectId: ProjectId) = withContext(Dispatchers.IO) {
        Files.createDirectories(baseDir)
        file.writeText(projectId)
    }
}
