package com.devloop.core.domain.agent

/**
 * Outcome of a coding-agent process (Cursor CLI, Codex CLI, …); API-only agents may omit fields.
 */
enum class CodingAgentRunOutcome {
    SUCCESS,
    ERROR_NONZERO_EXIT,
    /** exit 0 but no usable stdout — kein stiller Erfolg. */
    NO_STDOUT_EXIT_ZERO,
    TIMEOUT,
    /** Prozess beendet, stdout/stderr-Reader beenden nicht rechtzeitig (blockierender Child o. Ä.). */
    STREAM_DRAIN_TIMEOUT,
    SETUP_OR_RESOLVE_ERROR,
}

/**
 * Metrics from a CLI (or similar) run for UI / logging.
 */
data class CodingAgentExecutionDetails(
    val outcome: CodingAgentRunOutcome,
    val exitCode: Int? = null,
    val stdoutLength: Int = 0,
    val stderrLength: Int = 0,
    val stderrPreview: String? = null,
    val processWallDurationMs: Long = 0L,
    /**
     * Optional: vom jeweiligen Adapter gesetzt, z. B. Codex CLI — **effektive** Startparameter des gestarteten Prozesses
     * (nicht nur UI-Wunsch), für Laufdiagnostik / Timeline.
     */
    val launchDiagnosticSummary: String? = null,
)

/**
 * [Result.failure] from local CLI adapters (Cursor CLI, Codex CLI, …) may carry this for supervisor UI.
 */
class CodingAgentRunFailedException(
    message: String,
    val details: CodingAgentExecutionDetails?,
) : Exception(message)
