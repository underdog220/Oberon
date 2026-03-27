package com.devloop.platform

import com.devloop.core.domain.enums.MemoryKind
import com.devloop.core.domain.model.*
import com.devloop.core.domain.repository.ConversationRepository
import com.devloop.core.domain.repository.MemoryRepository
import com.devloop.core.domain.repository.ResumeContextRepository
import com.devloop.core.domain.repository.VirtualInstanceRepository
import java.util.logging.Logger

/**
 * Assembliert den "Boost-Kontext" fuer eine virtuelle Instanz.
 *
 * Kombiniert stabiles Langzeitwissen, Resume-Kontext, letzte Konversation
 * und dynamische Hinweise zu einem token-effizienten Kontextpaket.
 *
 * Ziel: Eine neue Session soll auf aehnlichem inhaltlichem Niveau
 * weiterarbeiten koennen wie laengere fruehere Gespraeche.
 */
class ContextBoosterService(
    private val instances: VirtualInstanceRepository,
    private val memory: MemoryRepository,
    private val conversations: ConversationRepository,
    private val resumeContexts: ResumeContextRepository,
) {
    private val log = Logger.getLogger("ContextBoosterService")

    /**
     * Assembliert den vollstaendigen Boost-Kontext fuer eine virtuelle Instanz.
     */
    suspend fun assembleBoostContext(virtualInstanceId: VirtualInstanceId): BoostContext {
        val instance = instances.getById(virtualInstanceId) ?: return BoostContext.EMPTY

        // 1. Stabiles Langzeitwissen (Architektur, Regeln, Ziele)
        val stableKnowledge = memory.getForInstance(virtualInstanceId, MemoryKind.STABLE_KNOWLEDGE) +
            memory.getForProject(instance.projectId, MemoryKind.STABLE_KNOWLEDGE) +
            memory.getGlobal(MemoryKind.STABLE_KNOWLEDGE)

        // 2. Entscheidungsprotokoll
        val decisions = memory.getForInstance(virtualInstanceId, MemoryKind.DECISION_LOG) +
            memory.getForProject(instance.projectId, MemoryKind.DECISION_LOG)

        // 3. Resume-Kontext (letzter Stand, offene Punkte)
        val resume = resumeContexts.getForInstance(virtualInstanceId)

        // 4. Letzte Konversation (tail, nicht alles)
        val recentMessages = conversations.getRecentMessages(virtualInstanceId, 20)

        // 5. Dynamische Hinweise zusammenbauen
        val hints = buildDynamicHints(resume, recentMessages)

        log.fine("BoostContext fuer $virtualInstanceId: ${stableKnowledge.size} stable, ${decisions.size} decisions, ${recentMessages.size} recent msgs")

        return BoostContext(
            stableKnowledge = stableKnowledge.sortedByDescending { it.relevanceScore }.take(20),
            decisions = decisions.sortedByDescending { it.updatedAtEpochMillis }.take(10),
            resumeContext = resume,
            recentMessages = recentMessages,
            dynamicHints = hints,
        )
    }

    /**
     * Formatiert den Boost-Kontext als Text fuer die KI-Injection.
     * Token-effizient: nur das Wichtigste, strukturiert.
     */
    fun formatForInjection(ctx: BoostContext, maxChars: Int = 8000): String = buildString {
        // Resume-Kontext zuerst (wichtigster Teil)
        ctx.resumeContext?.let { resume ->
            if (resume.resumeHints.isNotBlank()) {
                appendLine("[RESUME_CONTEXT]")
                appendLine(resume.resumeHints.take(1500))
                appendLine("[/RESUME_CONTEXT]")
                appendLine()
            }
        }

        // Stabiles Wissen
        if (ctx.stableKnowledge.isNotEmpty()) {
            appendLine("[STABLE_KNOWLEDGE]")
            for (entry in ctx.stableKnowledge.take(10)) {
                appendLine("- ${entry.title}: ${entry.content.take(300)}")
            }
            appendLine("[/STABLE_KNOWLEDGE]")
            appendLine()
        }

        // Entscheidungen
        if (ctx.decisions.isNotEmpty()) {
            appendLine("[DECISIONS]")
            for (entry in ctx.decisions.take(5)) {
                appendLine("- ${entry.title}: ${entry.content.take(200)}")
            }
            appendLine("[/DECISIONS]")
            appendLine()
        }

        // Dynamische Hinweise
        if (ctx.dynamicHints.isNotEmpty()) {
            appendLine("[HINTS]")
            ctx.dynamicHints.forEach { appendLine("- $it") }
            appendLine("[/HINTS]")
        }

        // Auf Budget kuerzen
        if (length > maxChars) {
            delete(maxChars, length)
            appendLine("\n[... gekuerzt]")
        }
    }

    private fun buildDynamicHints(resume: ResumeContext?, recentMessages: List<ConversationMessage>): List<String> {
        val hints = mutableListOf<String>()

        resume?.let {
            if (it.activityState.name != "IDLE") {
                hints += "Letzter Zustand: ${it.activityState}"
            }
        }

        if (recentMessages.isNotEmpty()) {
            hints += "Letzte Konversation: ${recentMessages.size} Nachrichten"
        }

        return hints
    }
}

/**
 * Assemblierter Boost-Kontext fuer eine virtuelle Instanz.
 */
data class BoostContext(
    val stableKnowledge: List<MemoryEntry>,
    val decisions: List<MemoryEntry>,
    val resumeContext: ResumeContext?,
    val recentMessages: List<ConversationMessage>,
    val dynamicHints: List<String>,
) {
    companion object {
        val EMPTY = BoostContext(emptyList(), emptyList(), null, emptyList(), emptyList())
    }
}
