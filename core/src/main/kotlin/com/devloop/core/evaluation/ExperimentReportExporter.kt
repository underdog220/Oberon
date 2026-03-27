package com.devloop.core.evaluation

import com.devloop.core.domain.experiment.ExperimentSummary
import com.devloop.core.domain.experiment.RankedResult
import java.util.Locale

/**
 * Exports experiment summaries as human-readable and machine-readable reports.
 */
object ExperimentReportExporter {

    /**
     * Exports an [ExperimentSummary] as JSON string.
     */
    fun toJson(summary: ExperimentSummary): String = buildString {
        appendLine("{")
        appendLine("""  "experimentId": "${summary.experimentId}",""")
        appendLine("""  "experimentName": "${escapeJson(summary.experimentName)}",""")
        appendLine("""  "totalRuns": ${summary.totalRuns},""")
        appendLine("""  "completedRuns": ${summary.completedRuns},""")
        appendLine("""  "failedRuns": ${summary.failedRuns},""")
        appendLine("""  "bestQualityScore": ${summary.bestQualityScore},""")
        summary.bestParameterSet?.let {
            appendLine("""  "bestConfig": "${escapeJson(it.label())}",""")
        }
        appendLine("""  "generatedAt": ${summary.generatedAtEpochMillis},""")
        appendLine("""  "ranking": [""")
        summary.rankedResults.forEachIndexed { i, r ->
            val comma = if (i < summary.rankedResults.lastIndex) "," else ""
            appendLine("    ${rankedResultToJson(r)}$comma")
        }
        appendLine("  ]")
        append("}")
    }

    /**
     * Exports an [ExperimentSummary] as CSV string.
     * Header + one row per ranked result.
     */
    fun toCsv(summary: ExperimentSummary): String = buildString {
        appendLine("Rank,ExecutionId,Engine,Model,ChunkMs,VadSensitivity,SilenceMs,Language,WER,CER,TermAccuracy,NumberAccuracy,TTFT_ms,Duration_ms,QualityScore")
        for (r in summary.rankedResults) {
            val p = r.parameters
            val m = r.metrics
            appendLine(listOf(
                r.rank,
                r.executionId,
                p.transcriptionEngine.name,
                p.transcriptionModel,
                p.chunkSizeMs,
                p.vadSensitivity,
                p.silenceThresholdMs,
                p.language,
                m.wer?.let { String.format(Locale.US, "%.4f", it) } ?: "",
                m.cer?.let { String.format(Locale.US, "%.4f", it) } ?: "",
                m.technicalTermAccuracy?.let { String.format(Locale.US, "%.4f", it) } ?: "",
                m.numberAccuracy?.let { String.format(Locale.US, "%.4f", it) } ?: "",
                m.timeToFirstTranscriptMs ?: "",
                m.totalDurationMs ?: "",
                m.qualityScore?.let { String.format(Locale.US, "%.4f", it) } ?: "",
            ).joinToString(","))
        }
    }

    /**
     * Produces a compact human-readable text summary.
     */
    fun toText(summary: ExperimentSummary): String = buildString {
        appendLine("=== Experiment: ${summary.experimentName} (${summary.experimentId}) ===")
        appendLine("Runs: ${summary.completedRuns}/${summary.totalRuns} abgeschlossen, ${summary.failedRuns} fehlgeschlagen")
        appendLine()
        if (summary.bestParameterSet != null) {
            appendLine("Beste Konfiguration:")
            appendLine("  ${summary.bestParameterSet!!.label()}")
            appendLine("  Quality Score: ${String.format(Locale.US, "%.1f",(summary.bestQualityScore ?: 0.0) * 100)}%")
            appendLine()
        }
        if (summary.rankedResults.isNotEmpty()) {
            appendLine("Ranking:")
            appendLine("  %-4s %-50s %8s %8s %8s".format("#", "Konfiguration", "WER", "CER", "Score"))
            appendLine("  " + "-".repeat(82))
            for (r in summary.rankedResults) {
                appendLine("  %-4d %-50s %8s %8s %8s".format(
                    r.rank,
                    r.parameters.label().take(50),
                    r.metrics.wer?.let { String.format(Locale.US, "%.1f%%",it * 100) } ?: "-",
                    r.metrics.cer?.let { String.format(Locale.US, "%.1f%%",it * 100) } ?: "-",
                    r.metrics.qualityScore?.let { String.format(Locale.US, "%.1f%%",it * 100) } ?: "-",
                ))
            }
        }
    }

    private fun rankedResultToJson(r: RankedResult): String {
        val m = r.metrics
        val p = r.parameters
        return buildString {
            append("{")
            append(""""rank":${r.rank},""")
            append(""""executionId":"${r.executionId}",""")
            append(""""engine":"${p.transcriptionEngine.name}",""")
            append(""""model":"${escapeJson(p.transcriptionModel)}",""")
            append(""""chunkMs":${p.chunkSizeMs},""")
            append(""""vadSensitivity":${p.vadSensitivity},""")
            append(""""silenceMs":${p.silenceThresholdMs},""")
            append(""""language":"${p.language}",""")
            m.wer?.let { append(""""wer":$it,""") }
            m.cer?.let { append(""""cer":$it,""") }
            m.technicalTermAccuracy?.let { append(""""termAccuracy":$it,""") }
            m.numberAccuracy?.let { append(""""numberAccuracy":$it,""") }
            m.timeToFirstTranscriptMs?.let { append(""""ttftMs":$it,""") }
            m.totalDurationMs?.let { append(""""durationMs":$it,""") }
            append(""""qualityScore":${m.qualityScore}""")
            append("}")
        }
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
