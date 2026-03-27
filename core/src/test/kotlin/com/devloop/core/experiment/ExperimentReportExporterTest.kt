package com.devloop.core.experiment

import com.devloop.core.domain.experiment.*
import com.devloop.core.evaluation.ExperimentReportExporter
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertContains

class ExperimentReportExporterTest {

    private val summary = ExperimentSummary(
        experimentId = "exp-1",
        experimentName = "Audio Quality Test",
        totalRuns = 4,
        completedRuns = 3,
        failedRuns = 1,
        bestParameterSet = ParameterSet(chunkSizeMs = 500, transcriptionModel = "small"),
        bestQualityScore = 0.85,
        rankedResults = listOf(
            RankedResult(
                rank = 1,
                executionId = "exec-1",
                parameters = ParameterSet(chunkSizeMs = 500, transcriptionModel = "small"),
                metrics = MetricResult("exec-1", wer = 0.08, cer = 0.03, qualityScore = 0.85),
            ),
            RankedResult(
                rank = 2,
                executionId = "exec-2",
                parameters = ParameterSet(chunkSizeMs = 1000, transcriptionModel = "base"),
                metrics = MetricResult("exec-2", wer = 0.15, cer = 0.07, qualityScore = 0.72),
            ),
        ),
        generatedAtEpochMillis = 1711200000000,
    )

    @Test
    fun `toJson produces valid JSON structure`() {
        val json = ExperimentReportExporter.toJson(summary)
        assertContains(json, """"experimentId": "exp-1"""")
        assertContains(json, """"totalRuns": 4""")
        assertContains(json, """"bestQualityScore": 0.85""")
        assertContains(json, """"ranking": [""")
        assertContains(json, """"rank":1""")
        assertContains(json, """"rank":2""")
    }

    @Test
    fun `toCsv produces header and data rows`() {
        val csv = ExperimentReportExporter.toCsv(summary)
        val lines = csv.lines().filter { it.isNotBlank() }
        assertTrue(lines.size >= 3) // header + 2 data rows
        assertContains(lines[0], "Rank,ExecutionId")
        assertContains(lines[1], "1,exec-1")
        assertContains(lines[2], "2,exec-2")
    }

    @Test
    fun `toText produces readable summary`() {
        val text = ExperimentReportExporter.toText(summary)
        assertContains(text, "Audio Quality Test")
        assertContains(text, "3/4 abgeschlossen")
        assertContains(text, "1 fehlgeschlagen")
        assertContains(text, "85.0%")
        assertContains(text, "Ranking:")
    }

    @Test
    fun `empty summary produces valid output`() {
        val empty = ExperimentSummary(
            experimentId = "empty",
            experimentName = "Empty",
            totalRuns = 0,
            completedRuns = 0,
            failedRuns = 0,
            generatedAtEpochMillis = 0,
        )
        val json = ExperimentReportExporter.toJson(empty)
        assertContains(json, """"totalRuns": 0""")
        assertContains(json, """"ranking": [""")

        val csv = ExperimentReportExporter.toCsv(empty)
        assertTrue(csv.lines().filter { it.isNotBlank() }.size == 1) // header only
    }
}
