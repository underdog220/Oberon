package com.devloop.core.domain.enums

/**
 * Identifies how a user input was created.
 * Used in [com.devloop.core.domain.model.InputDraft] for analytics,
 * context hints, and UI display.
 */
enum class InputChannelType {
    /** User typed the input manually. */
    TYPED,
    /** Input came from speech-to-text / dictation. */
    VOICE,
    /** Input was pasted from clipboard. */
    PASTED,
    /** Input was selected from history or a template. */
    FROM_HISTORY,
    /** Input was generated or injected by the system. */
    SYSTEM,
    /** Input source is unknown or not tracked. */
    UNKNOWN;

    companion object {
        /** Safely parse from nullable string, defaulting to [UNKNOWN]. */
        fun fromString(value: String?): InputChannelType =
            if (value == null) UNKNOWN
            else entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}
