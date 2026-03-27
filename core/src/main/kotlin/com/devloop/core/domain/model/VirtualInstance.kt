package com.devloop.core.domain.model

import com.devloop.core.domain.enums.VirtualInstanceStatus
import com.devloop.core.domain.enums.VirtualInstanceType

/**
 * Virtueller Fokusraum / Workspace.
 *
 * Repraesentiert eine "virtuelle Instanz" der zentralen Supervisor-KI.
 * Jeder Fokusraum hat eigenen Gespraechsverlauf, eigenes Gedaechtnis,
 * eigenen Kontext und eigene Rechte — obwohl intern nur eine KI-Engine existiert.
 */
data class VirtualInstance(
    val id: VirtualInstanceId,
    val projectId: ProjectId,
    val label: String,
    val instanceType: VirtualInstanceType,
    val status: VirtualInstanceStatus = VirtualInstanceStatus.ACTIVE,
    val scopeJson: String = "{}",
    val createdAtEpochMillis: Long,
    val lastActiveAtEpochMillis: Long,
    /** Domaene: "SYSTEM" oder "GUTACHTEN". */
    val domain: String = "SYSTEM",
)
