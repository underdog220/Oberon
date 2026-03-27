package com.devloop.core.domain.agent.handoff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentHandoffRendererTest {

    private val sampleContext = AgentHandoffContext(
        projectName = "DevLoop",
        purpose = "Multi-Agent Coding Supervisor",
        maturity = ProjectMaturity.MVP,
        shortDescription = "Desktop app orchestrating coding agents",
        whatWorks = "Supervisor chat, agent orchestration, shell integration",
        whatsMissing = "Shell-supervised-loop, token tracking",
        currentPriority = "Agent Handoff system",
        nextStep = "Implement handoff UI",
        modules = listOf(
            ModuleInfo("core", "Domain models", "core/", stable = true),
            ModuleInfo("desktop", "Compose Desktop UI", "desktop/", stable = true),
            ModuleInfo("bridge", "Agent adapters", "bridge/", stable = true),
        ),
        layers = "core -> bridge -> desktop (Clean Architecture)",
        dataFlow = "User -> Supervisor -> Agent -> Shell -> Result",
        primaryFiles = listOf(
            EntryPoint("desktop/.../DesktopSupervisorController.kt", "Main orchestrator", readFirst = true),
            EntryPoint("core/.../CodingAgent.kt", "Agent interface", readFirst = true),
            EntryPoint("bridge/.../OpenAiCodingAgent.kt", "API agent impl"),
        ),
        secondaryAreas = "app/ (Android, experimental)",
        buildCommand = "./gradlew :desktop:compileKotlin",
        testCommand = "./gradlew :desktop:test :core:test :bridge:test",
        runCommand = "./gradlew :desktop:run",
        gotchas = "Windows-only JNA features",
        architecturePrinciples = listOf(
            "No silent fallback between agents",
            "Clean Architecture: core has no framework deps",
        ),
        doNotChange = "SupervisorAutoLoopGuards (well-tested)",
        testingPolicy = "kotlin.test for new tests",
        currentFocus = "Agent Handoff / Project Context system",
        knownProblems = listOf("No CI/CD yet", "Settings dialog too large"),
        recentChanges = listOf(
            RecentChange("2026-03-23", "Extracted CodingAgentExecutionEngine", "desktop/supervisor"),
            RecentChange("2026-03-23", "Added Agent Dropdown to UI", "desktop/ui"),
        ),
    )

    @Test
    fun jsonRoundTrip() {
        val json = sampleContext.toJson().toString()
        val restored = AgentHandoffContext.fromJson(json)
        assertEquals(sampleContext.projectName, restored.projectName)
        assertEquals(sampleContext.purpose, restored.purpose)
        assertEquals(sampleContext.maturity, restored.maturity)
        assertEquals(sampleContext.modules.size, restored.modules.size)
        assertEquals(sampleContext.modules[0].name, restored.modules[0].name)
        assertEquals(sampleContext.primaryFiles.size, restored.primaryFiles.size)
        assertEquals(sampleContext.primaryFiles[0].readFirst, restored.primaryFiles[0].readFirst)
        assertEquals(sampleContext.architecturePrinciples.size, restored.architecturePrinciples.size)
        assertEquals(sampleContext.knownProblems, restored.knownProblems)
        assertEquals(sampleContext.recentChanges.size, restored.recentChanges.size)
        assertEquals(sampleContext.recentChanges[0].summary, restored.recentChanges[0].summary)
    }

    @Test
    fun jsonRoundTripEmptyContext() {
        val empty = AgentHandoffContext()
        val json = empty.toJson().toString()
        val restored = AgentHandoffContext.fromJson(json)
        assertEquals(empty.projectName, restored.projectName)
        assertEquals(empty.modules, restored.modules)
        assertEquals(empty.knownProblems, restored.knownProblems)
    }

    @Test
    fun fromJsonEmptyString() {
        val ctx = AgentHandoffContext.fromJson("")
        assertEquals("", ctx.projectName)
        assertEquals(ProjectMaturity.PROTOTYPE, ctx.maturity)
    }

    @Test
    fun markdownContainsSectionHeaders() {
        val md = AgentHandoffRenderer.renderMarkdown(sampleContext)
        assertContains(md, "## 1. Project Identity")
        assertContains(md, "## 2. Current State")
        assertContains(md, "## 3. Architecture")
        assertContains(md, "## 4. Entry Points")
        assertContains(md, "## 5. Build / Test / Run")
        assertContains(md, "## 6. Rules & Principles")
        assertContains(md, "## 8. Open Issues")
        assertContains(md, "## 9. Recent Changes")
        assertContains(md, "## How to use this context")
    }

    @Test
    fun markdownOmitsEmptySections() {
        val minimal = AgentHandoffContext(projectName = "Test", purpose = "Testing")
        val md = AgentHandoffRenderer.renderMarkdown(minimal)
        assertContains(md, "## 1. Project Identity")
        assertFalse(md.contains("## 3. Architecture"), "Empty architecture section should be omitted")
        assertFalse(md.contains("## 5. Build"), "Empty build section should be omitted")
        assertFalse(md.contains("## 9. Recent Changes"), "Empty recent changes should be omitted")
    }

    @Test
    fun markdownContainsProjectContent() {
        val md = AgentHandoffRenderer.renderMarkdown(sampleContext)
        assertContains(md, "DevLoop")
        assertContains(md, "Multi-Agent Coding Supervisor")
        assertContains(md, "MVP")
        assertContains(md, "DesktopSupervisorController.kt")
        assertContains(md, "./gradlew :desktop:run")
        assertContains(md, "No silent fallback")
    }

    @Test
    fun compactRespectsMaxChars() {
        val compact = AgentHandoffRenderer.renderCompact(sampleContext, maxChars = 500)
        assertTrue(compact.length <= 500, "Compact output (${compact.length}) should be <= 500 chars")
        assertContains(compact, "[/AGENT_HANDOFF]")
    }

    @Test
    fun compactContainsKeyInfo() {
        val compact = AgentHandoffRenderer.renderCompact(sampleContext)
        assertContains(compact, "[AGENT_HANDOFF projectName=DevLoop]")
        assertContains(compact, "Maturity: MVP")
        assertContains(compact, "[/AGENT_HANDOFF]")
    }

    @Test
    fun renderJsonIsValidJson() {
        val json = AgentHandoffRenderer.renderJson(sampleContext)
        val parsed = org.json.JSONObject(json)
        assertEquals("DevLoop", parsed.getString("projectName"))
        assertEquals("MVP", parsed.getString("maturity"))
        assertTrue(parsed.getJSONArray("modules").length() == 3)
    }

    @Test
    fun maturityFromStringCaseInsensitive() {
        assertEquals(ProjectMaturity.MVP, ProjectMaturity.fromString("mvp"))
        assertEquals(ProjectMaturity.PRODUCTION, ProjectMaturity.fromString("PRODUCTION"))
        assertEquals(ProjectMaturity.PROTOTYPE, ProjectMaturity.fromString("unknown"))
    }
}
