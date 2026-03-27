package com.devloop.core.domain.agent

import kotlin.test.Test
import kotlin.test.assertEquals

class CodingEngineModelsTest {

    @Test
    fun `maps claude agent id to claude engine`() {
        assertEquals(
            CodingEngineId.CLAUDE_CODE,
            CodingEngineRegistry.engineForAgentId(CodingAgentIds.CLAUDE_CODE_CLI),
        )
    }

    @Test
    fun `maps unknown to openai codex engine via normalized id`() {
        assertEquals(
            CodingEngineId.OPENAI_CODEX,
            CodingEngineRegistry.engineForAgentId("unknown-agent"),
        )
    }
}
