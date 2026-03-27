package com.devloop.core.domain.agent.trace

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.regex.Pattern

/**
 * Forensic trace log for the coding-agent prompt pipeline. **Off** unless `-Ddevloop.agent.trace=true`.
 *
 * Log file: `{user.home}/.devloop/agent-trace.log` (directory created on first write).
 * Thread-safe; write failures are swallowed (never throws to callers).
 */
object AgentTraceLogger {

    const val ENABLE_PROPERTY = "devloop.agent.trace"

    private val wallTs: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    private val writeLock = Any()

    private val logFile: Path by lazy {
        Path.of(System.getProperty("user.home"), ".devloop", "agent-trace.log").toAbsolutePath().normalize()
    }

    /** Absolute path of the trace log file (for UI / docs), regardless of [isEnabled]. */
    fun logFileAbsolutePath(): String = logFile.toString()

    fun isEnabled(): Boolean =
        System.getProperty(ENABLE_PROPERTY, "false").trim().equals("true", ignoreCase = true)

    /** Returns a new run id if tracing is enabled, else `null`. */
    fun newRunIdIfEnabled(): String? = if (isEnabled()) UUID.randomUUID().toString() else null

    private val skPattern = Pattern.compile("\\bsk-[a-zA-Z0-9]{8,}\\b")
    private val bearerPattern = Pattern.compile("(?i)Bearer\\s+[\\w-_.]{10,}")

    fun sanitizePreview(text: String, maxChars: Int = 4000): String {
        var s = text.replace("\r\n", "\n")
        s = skPattern.matcher(s).replaceAll("[REDACTED-sk]")
        s = bearerPattern.matcher(s).replaceAll("Bearer [REDACTED]")
        if (s.length <= maxChars) return s
        return s.take(maxChars) + "\n… [truncated, totalChars=${text.length}]"
    }

    fun appendLines(runId: String, section: String, lines: List<Pair<String, String>>) {
        if (!isEnabled()) return
        try {
            val block = buildString {
                appendLine("[$section | runId=$runId]")
                lines.forEach { (k, v) ->
                    append(k).append(": ").append(v.replace("\r\n", "\n").replace("\n", " | ")).appendLine()
                }
            }
            writeRaw(block)
        } catch (_: Throwable) {
        }
    }

    fun beginRun(
        runId: String,
        projectId: String,
        payloadSource: String,
        inputContextLabel: String,
    ) {
        if (!isEnabled()) return
        try {
            ensureDir()
            val ts = wallTs.format(Instant.now())
            val thread = Thread.currentThread().name
            writeRaw(
                buildString {
                    appendLine()
                    appendLine("======== RUN BEGIN ========")
                    appendLine("timestamp: $ts")
                    appendLine("runId: $runId")
                    appendLine("thread: $thread")
                    appendLine("projectId: $projectId")
                    appendLine("payloadSource: $payloadSource")
                    appendLine("inputContextLabel: $inputContextLabel")
                    appendLine("logFile: ${logFileAbsolutePath()}")
                    appendLine("===========================")
                },
            )
        } catch (_: Throwable) {
        }
    }

    fun logPromptBuild(
        runId: String,
        internalComposerUsed: Boolean,
        codexTemplateUsed: Boolean,
        directRawIntentAsPrompt: Boolean,
        manualAgentFieldBypass: Boolean,
        portfolioAppendixChars: Int,
        promptOrigin: String,
        rawUserIntentLength: Int,
        rawUserIntentPreview: String,
        promptForAgentLength: Int,
        promptForAgentPreview: String,
        rawIntentIdenticalToFinalPrompt: Boolean,
        traceLogFile: String,
    ) {
        if (!isEnabled()) return
        appendLines(
            runId,
            "PROMPT BUILD",
            listOf(
                "promptOrigin" to promptOrigin,
                "manualAgentFieldBypass" to manualAgentFieldBypass.toString(),
                "InternalCodingPromptComposer" to internalComposerUsed.toString(),
                "CodexCliPromptTemplate" to codexTemplateUsed.toString(),
                "directRawIntentAsPrompt" to directRawIntentAsPrompt.toString(),
                "portfolioAppendix.chars" to portfolioAppendixChars.toString(),
                "rawUserIntent.length" to rawUserIntentLength.toString(),
                "rawUserIntent.preview" to sanitizePreview(rawUserIntentPreview),
                "promptForAgent.length" to promptForAgentLength.toString(),
                "promptForAgent.preview" to sanitizePreview(promptForAgentPreview),
                "rawUserIntent.equals.promptForAgent" to rawIntentIdenticalToFinalPrompt.toString(),
                "traceLogFile" to traceLogFile,
            ),
        )
    }

    fun logExecutionContext(
        runId: String,
        agentId: String,
        agentClass: String,
        workingDirectory: String?,
        integrationTargetLabel: String?,
        codexSandbox: String?,
        cliConfigPresent: Boolean,
        manualDirectAgentInput: Boolean,
    ) {
        if (!isEnabled()) return
        appendLines(
            runId,
            "EXECUTION CONTEXT",
            listOf(
                "selectedAgentId" to agentId,
                "agentClass" to agentClass,
                "manualDirectAgentInput" to manualDirectAgentInput.toString(),
                "workingDirectory" to (workingDirectory ?: "—"),
                "integrationTarget.label" to (integrationTargetLabel ?: "—"),
                "codexSandbox" to (codexSandbox ?: "—"),
                "cliTargetConfigJson.present" to cliConfigPresent.toString(),
                "traceLogFile" to logFileAbsolutePath(),
            ),
        )
    }

    /** Schreibt einmalig den absoluten Trace-Pfad (wenn `-Ddevloop.agent.trace=true`). */
    fun logStartupBannerToTraceFile() {
        if (!isEnabled()) return
        try {
            synchronized(writeLock) {
                ensureDir()
                writeRaw(
                    buildString {
                        appendLine()
                        appendLine("======== DevLoop agent trace (JVM start) ========")
                        appendLine("timestamp: ${Instant.now()}")
                        appendLine("traceLogFile: ${logFileAbsolutePath()}")
                        appendLine("enable: -D$ENABLE_PROPERTY=true")
                        appendLine("==============================================")
                        appendLine()
                    },
                )
            }
        } catch (_: Throwable) {
        }
    }

    fun logPostProcess(
        runId: String,
        trimApplied: Boolean,
        placeholderUsed: Boolean,
        finalVisibleLength: Int,
        finalVisiblePreview: String,
    ) {
        if (!isEnabled()) return
        appendLines(
            runId,
            "POST PROCESS",
            listOf(
                "trimApplied" to trimApplied.toString(),
                "emptyPlaceholderUsed" to placeholderUsed.toString(),
                "finalVisible.length" to finalVisibleLength.toString(),
                "finalVisible.preview" to sanitizePreview(finalVisiblePreview),
            ),
        )
    }

    fun endRun(runId: String, status: String) {
        if (!isEnabled()) return
        try {
            val tsEnd = wallTs.format(Instant.now())
            writeRaw(
                buildString {
                    appendLine("======== RUN END ========")
                    appendLine("runId: $runId")
                    appendLine("timestamp: $tsEnd")
                    appendLine("status: $status")
                    appendLine("=========================")
                    appendLine()
                },
            )
        } catch (_: Throwable) {
        }
    }

    fun logSupervisorToAgentPayload(
        runId: String,
        agentId: String,
        workingDirectory: String?,
        finalPromptLength: Int,
        finalPromptPreview: String,
    ) {
        if (!isEnabled()) return
        appendLines(
            runId,
            "SUPERVISOR → AGENT",
            listOf(
                "agentId" to agentId,
                "workingDirectory" to (workingDirectory ?: "—"),
                "finalPrompt.length" to finalPromptLength.toString(),
                "finalPrompt.preview" to sanitizePreview(finalPromptPreview),
            ),
        )
    }

    fun logAgentExecutionPhase(runId: String, agentClass: String, phase: String) {
        if (!isEnabled()) return
        appendLines(
            runId,
            "AGENT EXECUTION",
            listOf(
                "agentClass" to agentClass,
                "phase" to phase,
            ),
        )
    }

    fun logAgentResponseBlock(runId: String, rawOutputLength: Int, rawOutputPreview: String) {
        if (!isEnabled()) return
        appendLines(
            runId,
            "AGENT RESPONSE",
            listOf(
                "rawOutput.length" to rawOutputLength.toString(),
                "rawOutput.preview" to sanitizePreview(rawOutputPreview),
            ),
        )
    }

    fun logSupervisorReceive(runId: String, responseReceived: Boolean, responseLength: Int) {
        if (!isEnabled()) return
        appendLines(
            runId,
            "SUPERVISOR RECEIVE",
            listOf(
                "responseReceived" to responseReceived.toString(),
                "response.length" to responseLength.toString(),
            ),
        )
    }

    fun logUiStateApplied(runId: String, visibleOutputLength: Int) {
        if (!isEnabled()) return
        appendLines(
            runId,
            "UI STATE",
            listOf(
                "lastAgentOutputForPanel.length" to visibleOutputLength.toString(),
                "cursorDraftUnchanged" to "true",
            ),
        )
    }

    fun logOpenAiSystemPromptNote(runId: String) {
        if (!isEnabled()) return
        appendLines(
            runId,
            "OPENAI CODING AGENT",
            listOf(
                "systemMessage" to "fixed concise coding assistant (see OpenAiCodingAgent.kt) — not logged verbatim",
                "userMessage.isTaskPrompt" to "true",
            ),
        )
    }

    fun logInternalComposerInvoked(runId: String, inputLength: Int, outputLength: Int) {
        if (!isEnabled()) return
        appendLines(
            runId,
            "InternalCodingPromptComposer",
            listOf(
                "invoked" to "true",
                "input.length" to inputLength.toString(),
                "output.length" to outputLength.toString(),
            ),
        )
    }

    fun logCodexTemplateInvoked(runId: String, taskSectionLength: Int, fullPromptLength: Int) {
        if (!isEnabled()) return
        appendLines(
            runId,
            "CodexCliPromptTemplate",
            listOf(
                "invoked" to "true",
                "embeddedTask.length" to taskSectionLength.toString(),
                "fullPrompt.length" to fullPromptLength.toString(),
            ),
        )
    }

    private fun ensureDir() {
        Files.createDirectories(logFile.parent)
    }

    private fun writeRaw(text: String) {
        synchronized(writeLock) {
            ensureDir()
            Files.writeString(
                logFile,
                text,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }
}
