package com.devloop.core.domain.agent.prompt

import com.devloop.core.domain.agent.prompt.PromptSourceKind
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CodexCliPromptTemplateTest {

    @Test
    fun build_containsStructuredSectionsAndTask() {
        val built = CodexCliPromptTemplate.build(
            AgentPromptTemplateInput(
                rawUserIntent = "Bitte Foo.kt anpassen.",
                projectName = "MyApp",
                localRepoRoot = "C:\\code\\MyApp",
                integrationTargetLabel = "Local CLI",
                codexSandboxModeLabel = "read-only",
            ),
        )
        assertEquals("Bitte Foo.kt anpassen.", built.rawUserIntent)
        val p = built.promptForAgent
        assertContains(p, "Projekt: MyApp")
        assertContains(p, "Rolle:")
        assertContains(p, "Kontext:")
        assertContains(p, "C:\\code\\MyApp")
        assertContains(p, "Aufgabe:")
        assertContains(p, "Bitte Foo.kt anpassen.")
        assertContains(p, "Betroffene Bereiche:")
        assertContains(p, "Abnahmekriterien:")
        assertContains(p, "Grenzen:")
        assertContains(p, "Erwartete Rückgabe:")
        assertContains(p, "read-only")
        assertEquals(4, built.segments.size)
        assertEquals(PromptSourceKind.TARGET_CONTEXT, built.segments[1].sourceKind)
        assertEquals(PromptSourceKind.RAW_USER_INTENT, built.segments[2].sourceKind)
    }
}
