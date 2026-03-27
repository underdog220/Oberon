package com.devloop.core.domain.model

/**
 * Audit-Log-Eintrag fuer eine Gateway-Anfrage.
 *
 * Jede Anfrage ueber den SupervisorGateway wird protokolliert:
 * wer, in welchem Scope, welche Aktion, welches Ergebnis.
 */
data class AuditLogEntry(
    val id: AuditLogEntryId,
    val clientId: GatewayClientId? = null,
    val virtualInstanceId: VirtualInstanceId? = null,
    val action: String,
    val resourcePath: String,
    val requestSummary: String = "",
    val responseCode: Int,
    val durationMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val domain: String = "SYSTEM",
)
