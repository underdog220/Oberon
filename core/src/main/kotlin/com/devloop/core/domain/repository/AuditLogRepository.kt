package com.devloop.core.domain.repository

import com.devloop.core.domain.model.AuditLogEntry
import com.devloop.core.domain.model.GatewayClientId

interface AuditLogRepository {
    suspend fun insert(entry: AuditLogEntry)
    suspend fun getRecent(limit: Int = 100, offset: Int = 0): List<AuditLogEntry>
    suspend fun getByClient(clientId: GatewayClientId, limit: Int = 50): List<AuditLogEntry>
}
