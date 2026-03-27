package com.devloop.platform

import com.devloop.core.domain.enums.VirtualInstanceStatus
import com.devloop.core.domain.enums.VirtualInstanceType
import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.model.VirtualInstance
import com.devloop.core.domain.model.VirtualInstanceId
import com.devloop.core.domain.repository.VirtualInstanceRepository
import java.util.UUID
import java.util.logging.Logger

/**
 * Verwaltet virtuelle Instanzen (Fokusraeume).
 *
 * Erstellt automatisch einen PROJECT_FOCUS-Fokusraum pro Projekt
 * und ermoeglicht das Anlegen weiterer thematischer oder
 * projektubergreifender Fokusraeume.
 */
class VirtualInstanceManager(
    private val repository: VirtualInstanceRepository,
) {
    private val log = Logger.getLogger("VirtualInstanceManager")

    /**
     * Stellt sicher, dass ein PROJECT_FOCUS-Fokusraum fuer das Projekt existiert.
     * Wird beim Oeffnen eines Projekts aufgerufen.
     * Gibt die ID des (ggf. neu erstellten) Fokusraums zurueck.
     */
    suspend fun ensureProjectFocus(projectId: ProjectId, projectName: String): VirtualInstanceId {
        val existing = repository.getByProject(projectId)
            .find { it.instanceType == VirtualInstanceType.PROJECT_FOCUS && it.status != VirtualInstanceStatus.ARCHIVED }
        if (existing != null) {
            // lastActive aktualisieren
            repository.upsert(existing.copy(lastActiveAtEpochMillis = System.currentTimeMillis()))
            return existing.id
        }

        val now = System.currentTimeMillis()
        val instance = VirtualInstance(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            label = projectName,
            instanceType = VirtualInstanceType.PROJECT_FOCUS,
            status = VirtualInstanceStatus.ACTIVE,
            createdAtEpochMillis = now,
            lastActiveAtEpochMillis = now,
        )
        repository.upsert(instance)
        log.info("Neuer PROJECT_FOCUS Fokusraum erstellt: ${instance.id} fuer Projekt $projectName")
        return instance.id
    }

    /**
     * Erstellt einen thematischen Fokusraum (z. B. "MES Operations").
     */
    suspend fun createTopicFocus(projectId: ProjectId, label: String, scopeJson: String = "{}"): VirtualInstanceId {
        val now = System.currentTimeMillis()
        val instance = VirtualInstance(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            label = label,
            instanceType = VirtualInstanceType.TOPIC_FOCUS,
            status = VirtualInstanceStatus.ACTIVE,
            scopeJson = scopeJson,
            createdAtEpochMillis = now,
            lastActiveAtEpochMillis = now,
        )
        repository.upsert(instance)
        log.info("Neuer TOPIC_FOCUS Fokusraum erstellt: ${instance.id} ($label)")
        return instance.id
    }

    /**
     * Erstellt einen projektubergreifenden Fokusraum (Portfolio).
     */
    suspend fun createCrossProjectFocus(projectId: ProjectId, label: String): VirtualInstanceId {
        val now = System.currentTimeMillis()
        val instance = VirtualInstance(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            label = label,
            instanceType = VirtualInstanceType.CROSS_PROJECT,
            status = VirtualInstanceStatus.ACTIVE,
            createdAtEpochMillis = now,
            lastActiveAtEpochMillis = now,
        )
        repository.upsert(instance)
        return instance.id
    }

    suspend fun archiveInstance(id: VirtualInstanceId) {
        repository.updateStatus(id, VirtualInstanceStatus.ARCHIVED)
    }

    suspend fun getAllActive(): List<VirtualInstance> =
        repository.getAll().filter { it.status == VirtualInstanceStatus.ACTIVE }
}
