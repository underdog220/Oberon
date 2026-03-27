package com.devloop.core.domain.repository

import com.devloop.core.domain.enums.MemoryKind
import com.devloop.core.domain.model.MemoryEntry
import com.devloop.core.domain.model.MemoryEntryId
import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.model.VirtualInstanceId

interface MemoryRepository {
    suspend fun getForInstance(virtualInstanceId: VirtualInstanceId, kind: MemoryKind? = null): List<MemoryEntry>
    suspend fun getForProject(projectId: ProjectId, kind: MemoryKind? = null): List<MemoryEntry>
    suspend fun getGlobal(kind: MemoryKind? = null): List<MemoryEntry>
    suspend fun upsert(entry: MemoryEntry)
    suspend fun delete(id: MemoryEntryId)
    /** Volltextsuche ueber Titel und Inhalt. */
    suspend fun search(query: String, limit: Int = 20): List<MemoryEntry>
}
