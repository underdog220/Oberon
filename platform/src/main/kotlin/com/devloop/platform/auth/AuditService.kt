package com.devloop.platform.auth

import com.devloop.core.domain.model.AuditLogEntry
import com.devloop.core.domain.model.GatewayClientId
import com.devloop.core.domain.model.VirtualInstanceId
import com.devloop.core.domain.repository.AuditLogRepository
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.logging.Logger

/**
 * Protokolliert Anfragen im Audit-Log (plattformunabhaengig).
 */
class AuditService(
    private val auditLog: AuditLogRepository,
) {
    private val log = Logger.getLogger("AuditService")

    fun logRequest(
        clientId: GatewayClientId?,
        virtualInstanceId: VirtualInstanceId?,
        action: String,
        resourcePath: String,
        requestSummary: String = "",
        responseCode: Int,
        durationMillis: Long? = null,
        domain: String = "SYSTEM",
    ) {
        val entry = AuditLogEntry(
            id = UUID.randomUUID().toString(),
            clientId = clientId,
            virtualInstanceId = virtualInstanceId,
            action = action,
            resourcePath = resourcePath,
            requestSummary = requestSummary,
            responseCode = responseCode,
            durationMillis = durationMillis,
            createdAtEpochMillis = System.currentTimeMillis(),
            domain = domain,
        )
        try { runBlocking { auditLog.insert(entry) } } catch (e: Throwable) { log.warning("Audit-Log Fehler: ${e.message}") }
    }
}
