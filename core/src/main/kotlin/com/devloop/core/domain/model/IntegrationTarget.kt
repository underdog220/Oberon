package com.devloop.core.domain.model

import com.devloop.core.domain.enums.IntegrationTargetKind

/**
 * A logical Cursor integration target (repo + CLI, ACP, or cloud agent), not a desktop window.
 */
data class IntegrationTarget(
    val id: IntegrationTargetId,
    val projectId: ProjectId,
    val kind: IntegrationTargetKind,
    val label: String,
    /** JSON or opaque string — parsed by the matching bridge adapter (CLI paths, ACP session id, …). */
    val configJson: String,
    val createdAtEpochMillis: Long
)
