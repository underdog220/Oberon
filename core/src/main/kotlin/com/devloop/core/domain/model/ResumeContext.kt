package com.devloop.core.domain.model

import com.devloop.core.domain.enums.ActivityState

/**
 * Resume-/Startup-Kontext einer virtuellen Instanz.
 *
 * Ermoeglicht es, eine neue Session auf dem gleichen inhaltlichen Niveau
 * weiterzufuehren wie die vorherige. Enthaealt den letzten sinnvollen
 * Arbeitsstand, offene Punkte und empfohlene naechste Schritte.
 */
data class ResumeContext(
    val virtualInstanceId: VirtualInstanceId,
    val lastConversationMessageId: ConversationMessageId? = null,
    /** JSON: letzte N Messages fuer Thread-Rekonstruktion. */
    val openAiThreadSnapshot: String = "[]",
    val activityState: ActivityState = ActivityState.IDLE,
    /** Menschenlesbare Resume-Hinweise. */
    val resumeHints: String = "",
    /** Serialisierter Kontext-Zustand (JSON). */
    val lastContextJson: String = "{}",
    val updatedAtEpochMillis: Long,
)
