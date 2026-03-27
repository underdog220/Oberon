package com.devloop.core.domain.agent.handoff

/**
 * Erzeugt aus einem [AgentHandoffContext] standardisierte Ausgaben:
 * Markdown (vollstaendig), Compact (fuer Prompt-Injection) und JSON (fuer Export).
 */
object AgentHandoffRenderer {

    /**
     * Volles Markdown mit Preamble und allen nicht-leeren Sektionen.
     */
    fun renderMarkdown(ctx: AgentHandoffContext): String = buildString {
        appendLine("# Agent Handoff: ${ctx.projectName.ifBlank { "(Projekt)" }}")
        appendLine()
        appendLine("## How to use this context")
        appendLine("1. Read this handoff first to orient yourself.")
        appendLine("2. Open only the primary entry-point files listed below.")
        appendLine("3. Form a plan based on the architecture and current state.")
        appendLine("4. Only broaden your analysis if the task requires it.")
        appendLine("5. Respect the rules and do-not-change areas.")
        appendLine()

        // 1: Identity
        if (ctx.projectName.isNotBlank() || ctx.purpose.isNotBlank() || ctx.shortDescription.isNotBlank()) {
            appendLine("## 1. Project Identity")
            field("Name", ctx.projectName)
            field("Purpose", ctx.purpose)
            field("Maturity", ctx.maturity.name)
            field("Description", ctx.shortDescription)
            appendLine()
        }

        // 2: Current State
        if (ctx.whatWorks.isNotBlank() || ctx.whatsMissing.isNotBlank() || ctx.currentPriority.isNotBlank() || ctx.nextStep.isNotBlank()) {
            appendLine("## 2. Current State")
            field("What works", ctx.whatWorks)
            field("What's missing", ctx.whatsMissing)
            field("Current priority", ctx.currentPriority)
            field("Next step", ctx.nextStep)
            appendLine()
        }

        // 3: Architecture
        if (ctx.modules.isNotEmpty() || ctx.layers.isNotBlank() || ctx.dataFlow.isNotBlank()) {
            appendLine("## 3. Architecture")
            field("Layers", ctx.layers)
            field("Data flow", ctx.dataFlow)
            if (ctx.modules.isNotEmpty()) {
                appendLine("**Modules:**")
                ctx.modules.forEach { m ->
                    val stab = if (m.stable) "" else " (experimental)"
                    appendLine("- **${m.name}**$stab: ${m.purpose} `${m.path}`")
                }
            }
            appendLine()
        }

        // 4: Entry Points
        if (ctx.primaryFiles.isNotEmpty() || ctx.secondaryAreas.isNotBlank()) {
            appendLine("## 4. Entry Points")
            if (ctx.primaryFiles.isNotEmpty()) {
                appendLine("**Read first:**")
                ctx.primaryFiles.filter { it.readFirst }.forEach { e ->
                    appendLine("- `${e.path}` -- ${e.description}")
                }
                val others = ctx.primaryFiles.filter { !it.readFirst }
                if (others.isNotEmpty()) {
                    appendLine("**Also relevant:**")
                    others.forEach { e -> appendLine("- `${e.path}` -- ${e.description}") }
                }
            }
            field("Secondary / historical areas", ctx.secondaryAreas)
            appendLine()
        }

        // 5: Build / Test / Run
        if (ctx.buildCommand.isNotBlank() || ctx.testCommand.isNotBlank() || ctx.runCommand.isNotBlank()) {
            appendLine("## 5. Build / Test / Run")
            if (ctx.buildCommand.isNotBlank()) appendLine("- **Build:** `${ctx.buildCommand}`")
            if (ctx.testCommand.isNotBlank()) appendLine("- **Test:** `${ctx.testCommand}`")
            if (ctx.runCommand.isNotBlank()) appendLine("- **Run:** `${ctx.runCommand}`")
            field("Environment variables", ctx.envVars)
            field("Gotchas", ctx.gotchas)
            appendLine()
        }

        // 6: Rules
        if (ctx.architecturePrinciples.isNotEmpty() || ctx.doNotChange.isNotBlank() || ctx.testingPolicy.isNotBlank()) {
            appendLine("## 6. Rules & Principles")
            if (ctx.architecturePrinciples.isNotEmpty()) {
                ctx.architecturePrinciples.forEach { appendLine("- $it") }
            }
            field("Do not change", ctx.doNotChange)
            field("Testing policy", ctx.testingPolicy)
            appendLine()
        }

        // 7: Integrity
        if (ctx.versionInfo.isNotBlank() || ctx.buildStatus.isNotBlank() || ctx.testStatus.isNotBlank()) {
            appendLine("## 7. Version / Build / Test Integrity")
            field("Version", ctx.versionInfo)
            field("Build status", ctx.buildStatus)
            field("Test status", ctx.testStatus)
            appendLine()
        }

        // 8: Open Issues
        if (ctx.knownProblems.isNotEmpty() || ctx.techDebt.isNotBlank() || ctx.currentFocus.isNotBlank()) {
            appendLine("## 8. Open Issues")
            field("Current focus", ctx.currentFocus)
            if (ctx.knownProblems.isNotEmpty()) {
                appendLine("**Known problems:**")
                ctx.knownProblems.forEach { appendLine("- $it") }
            }
            field("Tech debt", ctx.techDebt)
            field("Parked items", ctx.parkedItems)
            appendLine()
        }

        // 9: Recent Changes
        if (ctx.recentChanges.isNotEmpty()) {
            appendLine("## 9. Recent Changes")
            ctx.recentChanges.forEach { c ->
                val area = if (c.impactArea.isNotBlank()) " (${c.impactArea})" else ""
                appendLine("- **${c.date}:** ${c.summary}$area")
            }
            appendLine()
        }

        appendLine("---")
        appendLine("*Schema v${ctx.schemaVersion} | Generated by DevLoop Agent Handoff*")
    }

    /**
     * Kompakte Version fuer Prompt-Injection (Token-effizient).
     */
    fun renderCompact(ctx: AgentHandoffContext, maxChars: Int = 3000): String {
        val full = buildString {
            appendLine("[AGENT_HANDOFF projectName=${ctx.projectName.ifBlank { "?" }}]")
            if (ctx.shortDescription.isNotBlank()) appendLine("Desc: ${ctx.shortDescription}")
            if (ctx.purpose.isNotBlank()) appendLine("Purpose: ${ctx.purpose}")
            appendLine("Maturity: ${ctx.maturity.name}")
            if (ctx.currentPriority.isNotBlank()) appendLine("Priority: ${ctx.currentPriority}")
            if (ctx.nextStep.isNotBlank()) appendLine("Next: ${ctx.nextStep}")
            if (ctx.whatWorks.isNotBlank()) appendLine("Works: ${ctx.whatWorks}")
            if (ctx.whatsMissing.isNotBlank()) appendLine("Missing: ${ctx.whatsMissing}")
            if (ctx.layers.isNotBlank()) appendLine("Layers: ${ctx.layers}")
            if (ctx.modules.isNotEmpty()) {
                appendLine("Modules: ${ctx.modules.joinToString("; ") { "${it.name}(${it.path})" }}")
            }
            if (ctx.primaryFiles.isNotEmpty()) {
                appendLine("EntryFiles: ${ctx.primaryFiles.joinToString("; ") { it.path }}")
            }
            if (ctx.buildCommand.isNotBlank()) appendLine("Build: ${ctx.buildCommand}")
            if (ctx.testCommand.isNotBlank()) appendLine("Test: ${ctx.testCommand}")
            if (ctx.runCommand.isNotBlank()) appendLine("Run: ${ctx.runCommand}")
            if (ctx.gotchas.isNotBlank()) appendLine("Gotchas: ${ctx.gotchas}")
            if (ctx.architecturePrinciples.isNotEmpty()) {
                appendLine("Rules: ${ctx.architecturePrinciples.joinToString("; ")}")
            }
            if (ctx.doNotChange.isNotBlank()) appendLine("DoNotChange: ${ctx.doNotChange}")
            if (ctx.currentFocus.isNotBlank()) appendLine("Focus: ${ctx.currentFocus}")
            if (ctx.knownProblems.isNotEmpty()) {
                appendLine("Problems: ${ctx.knownProblems.joinToString("; ")}")
            }
            if (ctx.recentChanges.isNotEmpty()) {
                appendLine("Recent: ${ctx.recentChanges.joinToString("; ") { "${it.date}: ${it.summary}" }}")
            }
            appendLine("[/AGENT_HANDOFF]")
        }
        val suffix = "\n[/AGENT_HANDOFF]"
        return if (full.length <= maxChars) full
        else full.take((maxChars - suffix.length).coerceAtLeast(0)) + suffix
    }

    /**
     * JSON-Ausgabe fuer Datei-Export.
     */
    fun renderJson(ctx: AgentHandoffContext): String = ctx.toJson().toString(2)

    private fun StringBuilder.field(label: String, value: String) {
        if (value.isNotBlank()) appendLine("**$label:** $value")
    }
}
