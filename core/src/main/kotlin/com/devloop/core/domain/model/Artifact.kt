package com.devloop.core.domain.model

import com.devloop.core.domain.enums.ArtifactType

data class Artifact(
    val id: ArtifactId,
    val projectId: ProjectId,
    val type: ArtifactType,
    val createdAtEpochMillis: Long,
    val title: String? = null,
    val content: String
)

