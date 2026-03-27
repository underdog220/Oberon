package com.devloop.core.domain.model

import com.devloop.core.domain.enums.SessionStatus

data class Session(
    val id: SessionId,
    val projectId: ProjectId,
    val status: SessionStatus,
    val startedAtEpochMillis: Long? = null,
    val endedAtEpochMillis: Long? = null
)

