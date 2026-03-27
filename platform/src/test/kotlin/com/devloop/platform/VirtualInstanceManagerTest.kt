package com.devloop.platform

import com.devloop.core.domain.enums.VirtualInstanceStatus
import com.devloop.core.domain.enums.VirtualInstanceType
import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.model.VirtualInstance
import com.devloop.core.domain.model.VirtualInstanceId
import com.devloop.core.domain.repository.VirtualInstanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VirtualInstanceManagerTest {

    private val store = mutableListOf<VirtualInstance>()

    private val repo = object : VirtualInstanceRepository {
        override fun observeInstances(projectId: ProjectId) = flowOf(store.filter { it.projectId == projectId })
        override suspend fun getAll() = store.toList()
        override suspend fun getById(id: VirtualInstanceId) = store.find { it.id == id }
        override suspend fun getByProject(projectId: ProjectId) = store.filter { it.projectId == projectId }
        override suspend fun upsert(instance: VirtualInstance) {
            store.removeAll { it.id == instance.id }
            store.add(instance)
        }
        override suspend fun updateStatus(id: VirtualInstanceId, status: VirtualInstanceStatus) {
            val idx = store.indexOfFirst { it.id == id }
            if (idx >= 0) store[idx] = store[idx].copy(status = status)
        }
    }

    private val manager = VirtualInstanceManager(repo)

    @Test
    fun `ensureProjectFocus creates instance on first call`() = runTest {
        val id = manager.ensureProjectFocus("project-1", "TestProject")
        assertNotNull(id)
        assertEquals(1, store.size)
        assertEquals(VirtualInstanceType.PROJECT_FOCUS, store[0].instanceType)
        assertEquals("TestProject", store[0].label)
    }

    @Test
    fun `ensureProjectFocus returns existing on second call`() = runTest {
        val id1 = manager.ensureProjectFocus("project-1", "TestProject")
        val id2 = manager.ensureProjectFocus("project-1", "TestProject")
        assertEquals(id1, id2)
        assertEquals(1, store.size) // keine Duplikate
    }

    @Test
    fun `createTopicFocus creates separate instance`() = runTest {
        manager.ensureProjectFocus("project-1", "TestProject")
        val topicId = manager.createTopicFocus("project-1", "MES Operations")
        assertEquals(2, store.size)
        val topic = store.find { it.id == topicId }!!
        assertEquals(VirtualInstanceType.TOPIC_FOCUS, topic.instanceType)
        assertEquals("MES Operations", topic.label)
    }

    @Test
    fun `archiveInstance sets status to ARCHIVED`() = runTest {
        val id = manager.ensureProjectFocus("project-1", "TestProject")
        manager.archiveInstance(id)
        assertEquals(VirtualInstanceStatus.ARCHIVED, store[0].status)
    }

    @Test
    fun `getAllActive filters archived instances`() = runTest {
        manager.ensureProjectFocus("project-1", "TestProject")
        val topicId = manager.createTopicFocus("project-1", "Topic")
        manager.archiveInstance(topicId)
        val active = manager.getAllActive()
        assertEquals(1, active.size)
    }
}
