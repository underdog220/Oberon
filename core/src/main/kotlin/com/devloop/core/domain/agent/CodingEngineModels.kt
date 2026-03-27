package com.devloop.core.domain.agent

enum class CodingEngineId {
    OPENAI_CODEX,
    CLAUDE_CODE,
}

data class CodingEngineDescriptor(
    val id: CodingEngineId,
    val displayName: String,
    val capabilities: EngineCapabilities,
)

data class EngineCapabilities(
    val localWorkspaceRequired: Boolean,
    val supportsWorkspaceWrite: Boolean,
    val supportsBackgroundCapture: Boolean,
)

data class EngineSessionState(
    val projectId: String,
    val engineId: CodingEngineId,
    val agentId: String,
    val active: Boolean,
)

object CodingEngineRegistry {
    val descriptors: List<CodingEngineDescriptor> = listOf(
        CodingEngineDescriptor(
            id = CodingEngineId.OPENAI_CODEX,
            displayName = "OpenAI / Codex",
            capabilities = EngineCapabilities(
                localWorkspaceRequired = false,
                supportsWorkspaceWrite = true,
                supportsBackgroundCapture = true,
            ),
        ),
        CodingEngineDescriptor(
            id = CodingEngineId.CLAUDE_CODE,
            displayName = "Claude Code",
            capabilities = EngineCapabilities(
                localWorkspaceRequired = true,
                supportsWorkspaceWrite = true,
                supportsBackgroundCapture = true,
            ),
        ),
    )

    fun engineForAgentId(agentId: String): CodingEngineId {
        return when (CodingAgentIds.normalizeStoredPrimaryId(agentId)) {
            CodingAgentIds.CLAUDE_CODE_CLI -> CodingEngineId.CLAUDE_CODE
            else -> CodingEngineId.OPENAI_CODEX
        }
    }
}
