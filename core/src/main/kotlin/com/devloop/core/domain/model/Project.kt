package com.devloop.core.domain.model

import com.devloop.core.domain.enums.ProjectStatus

data class Project(
    val id: ProjectId,
    val name: String,
    val status: ProjectStatus,
    val createdAtEpochMillis: Long,
    /**
     * Desktop: bevorzugter Coding-Agent für dieses Projekt ([com.devloop.core.domain.agent.CodingAgentIds]).
     * `null` → Fallback aus globalen Bridge-Einstellungen.
     */
    val desktopPrimaryCodingAgentId: String? = null,
    /** Desktop: [com.devloop.core.domain.agent.CodexSandboxMode] name, z. B. READ_ONLY; `null` → aus Local-CLI-Target-JSON. */
    val desktopCodexSandbox: String? = null,
    /** Desktop: bevorzugtes [IntegrationTarget.id] für dieses Projekt; `null` → automatische Wahl. */
    val desktopPreferredIntegrationTargetId: String? = null,
)

