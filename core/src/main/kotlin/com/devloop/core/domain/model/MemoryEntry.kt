package com.devloop.core.domain.model

import com.devloop.core.domain.enums.MemoryKind

/**
 * Strukturierter Gedaechtniseintrag (Context Booster).
 *
 * Kann an eine virtuelle Instanz, ein Projekt, oder global gebunden sein.
 * [memoryKind] unterscheidet zwischen stabilem Langzeitwissen und dynamischem Kontext.
 */
data class MemoryEntry(
    val id: MemoryEntryId,
    /** NULL = globales Wissen. */
    val virtualInstanceId: VirtualInstanceId? = null,
    /** NULL = projektubergreifend. */
    val projectId: ProjectId? = null,
    val memoryKind: MemoryKind,
    val category: String = "",
    val title: String,
    val content: String,
    val relevanceScore: Double = 1.0,
    val validFromEpochMillis: Long? = null,
    /** NULL = kein Ablauf. */
    val validUntilEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val domain: String = "SYSTEM",
)
