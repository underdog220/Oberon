package com.devloop.core.domain.repository

import com.devloop.core.domain.model.ResumeContext
import com.devloop.core.domain.model.VirtualInstanceId

interface ResumeContextRepository {
    suspend fun getForInstance(virtualInstanceId: VirtualInstanceId): ResumeContext?
    suspend fun upsert(resumeContext: ResumeContext)
}
