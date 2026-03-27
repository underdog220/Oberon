package com.devloop.core.domain.agent.prompt

import com.devloop.core.domain.agent.CodingAgentIds
import com.devloop.core.domain.agent.trace.AgentTraceLogger

/**
 * Registrierte Strategien (Reihenfolge: erste passende gewinnt). Erweiterung: Eintrag in [strategies] anhängen.
 */
object CodingAgentPromptStrategyRegistry {
    private val strategies: List<CodingAgentPromptStrategy> = listOf(
        CodexCliPromptStrategy,
    )

    fun strategyFor(normalizedAgentId: String): CodingAgentPromptStrategy? =
        strategies.firstOrNull { it.supports(normalizedAgentId) }
}

/**
 * Strukturierter Auftrag für OpenAI Codex CLI: kompakt, mit klaren Grenzen und erwarteter Rückgabe.
 * Annahmen ohne Datenlage werden nicht vorgetäuscht (explizite Hinweise im Kontext-Abschnitt).
 */
object CodexCliPromptStrategy : CodingAgentPromptStrategy {
    override fun supports(normalizedAgentId: String): Boolean =
        normalizedAgentId == CodingAgentIds.CODEX_CLI

    override fun build(input: AgentPromptTemplateInput): BuiltCodingAgentPrompt =
        CodexCliPromptTemplate.build(input)
}

object CodexCliPromptTemplate {
    fun build(input: AgentPromptTemplateInput): BuiltCodingAgentPrompt {
        val task = input.rawUserIntent.trim()
        val project = input.projectName?.trim()?.takeIf { it.isNotEmpty() } ?: "—"
        val repo = input.localRepoRoot?.trim()?.takeIf { it.isNotEmpty() }
        val target = input.integrationTargetLabel?.trim()?.takeIf { it.isNotEmpty() }
        val sandbox = input.codexSandboxModeLabel?.trim()?.takeIf { it.isNotEmpty() }

        val head = buildString {
            appendLine("Projekt: $project")
            appendLine()
            appendLine("Rolle:")
            appendLine("Du bist der ausführende Coding-Agent für dieses Projekt. Arbeite präzise, minimal-invasiv und zielorientiert.")
        }.trim()

        val contextBlock = buildString {
            appendLine("Kontext:")
            if (repo != null) {
                appendLine("- Lokales Repo-Root (DevLoop Local-CLI): $repo")
            } else {
                appendLine("- Lokales Repo-Root: in diesem Lauf nicht gesetzt — aus Arbeitsverzeichnis/Umgebung ableiten.")
            }
            if (target != null) {
                appendLine("- Aktives Integration-Target (Label): $target")
            }
            if (sandbox != null) {
                appendLine("- Codex-Sandbox-Modus: $sandbox")
            }
            appendLine("- Technischer Stand / Architektur: keine separaten DevLoop-Einträge — aus Aufgabe und Codebasis schließen.")
            appendLine("- Relevante frühere Entscheidungen: in diesem Lauf nicht strukturiert erfasst — ggf. aus Nutzerauftrag; Annahmen explizit benennen.")
        }.trim()

        val taskHeader = "Aufgabe:"
        val tail = buildString {
            appendLine("Betroffene Bereiche:")
            appendLine("- nicht separat von DevLoop erfasst — aus der Aufgabe und dem Repository identifizieren")
            appendLine()
            appendLine("Abnahmekriterien:")
            appendLine("- nicht separat erfasst — aus der Aufgabe ableiten; bestehende Funktionalität nicht regressieren")
            appendLine("- Build/Test nicht verschlechtern (bestehende Suites weiterhin ausführbar)")
            appendLine("- Änderungen nachvollziehbar und minimal halten")
            appendLine()
            appendLine("Grenzen:")
            appendLine("- keine unnötigen Refactorings")
            appendLine("- bestehende Architektur respektieren")
            appendLine("- nur notwendige Änderungen")
            appendLine("- Build/Test nicht verschlechtern")
            appendLine()
            appendLine("Erwartete Rückgabe:")
            appendLine("- kurze Zusammenfassung")
            appendLine("- geänderte Dateien")
            appendLine("- offene Punkte")
            appendLine("- Risiken")
            appendLine("- nächste sinnvolle Schritte")
        }.trim()

        val prompt = buildString {
            appendLine(head)
            appendLine()
            appendLine(contextBlock)
            appendLine()
            appendLine(taskHeader)
            appendLine(task)
            appendLine()
            appendLine(tail)
        }.trim()

        val resolverLine = "CodingAgentSendPayloadResolver → ${input.payloadResolutionSource}"
        val templateRef = "CodexCliPromptTemplate.build()"

        var seg = 0
        fun nextId() = "codex-${++seg}"

        val segments = listOf(
            PromptSegment(
                id = nextId(),
                title = "Projektzeile & Rolle",
                sourceKind = PromptSourceKind.AGENT_TEMPLATE,
                sourceDetail = "$templateRef · Kopf (Projekt, Rolle)",
                text = head,
            ),
            PromptSegment(
                id = nextId(),
                title = "Kontext (Repo, Target, Sandbox)",
                sourceKind = PromptSourceKind.TARGET_CONTEXT,
                sourceDetail = "$templateRef · Kontextblock",
                text = contextBlock,
            ),
            PromptSegment(
                id = nextId(),
                title = "Nutzerauftrag (Aufgabe)",
                sourceKind = PromptSourceKind.RAW_USER_INTENT,
                sourceDetail = "$resolverLine · $templateRef · Abschnitt „Aufgabe“",
                text = task,
            ),
            PromptSegment(
                id = nextId(),
                title = "Struktur (Bereiche, Abnahme, Grenzen, Rückgabe)",
                sourceKind = PromptSourceKind.AGENT_TEMPLATE,
                sourceDetail = "$templateRef · Rahmen nach Aufgabe",
                text = tail,
            ),
        )

        input.traceRunId?.let { rid ->
            AgentTraceLogger.logCodexTemplateInvoked(
                runId = rid,
                taskSectionLength = task.length,
                fullPromptLength = prompt.length,
            )
        }

        return BuiltCodingAgentPrompt(
            promptForAgent = prompt,
            rawUserIntent = input.rawUserIntent,
            assemblyFlags = AgentPromptAssemblyFlags(codexTemplateUsed = true),
            segments = segments,
        )
    }
}
