package com.devloop.core.domain.model

import com.devloop.core.domain.enums.GatewayClientKind

/**
 * Registrierter Client der SupervisorGateway-API.
 *
 * Jeder Client hat eine Identitaet, erlaubte Scopes und Rechte.
 * Der API-Key wird nur als Hash gespeichert.
 */
data class GatewayClient(
    val id: GatewayClientId,
    val clientName: String,
    val clientKind: GatewayClientKind,
    /** SHA-256 Hash des API-Keys. */
    val apiKeyHash: String,
    /** JSON-Array: erlaubte Projekte, Instanzen, Aktionen. */
    val scopesJson: String = "[]",
    /** JSON-Objekt: Tool-/Ressourcenrechte. */
    val permissionsJson: String = "{}",
    val isActive: Boolean = true,
    val createdAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long? = null,
    /** JSON-Array: erlaubte Domaenen, z. B. ["SYSTEM","GUTACHTEN"] oder ["*"]. */
    val allowedDomains: String = "[\"*\"]",
)
