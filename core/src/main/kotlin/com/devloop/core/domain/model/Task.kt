package com.devloop.core.domain.model

import com.devloop.core.domain.enums.TaskStatus

data class Task(
    val id: TaskId,
    val projectId: ProjectId,
    val sessionId: SessionId,
    val status: TaskStatus,
    val title: String,
    val createdAtEpochMillis: Long
)

