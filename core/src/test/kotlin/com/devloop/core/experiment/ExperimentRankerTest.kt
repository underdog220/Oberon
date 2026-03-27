package com.devloop.core.experiment

import com.devloop.core.domain.experiment.*
import com.devloop.core.evaluation.ExperimentRanker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExperimentRankerTest {

    private val ranker = ExperimentRanker()

    @Test
    fun `summarize ranks by quality score descending`() {
        val experiment = ExperimentDefinition(
            id = "exp-1",
            name = "Ranking Test",
            description = "",
            createdAtEpochMillis = 0,
        )
        val plans = listOf(
            RunPlan("plan-1", "exp-1", "sc-1", ParameterSet(chunkSizeMs = 500), 0),
            RunPlan("plan-2", "exp-1", "sc-1", ParameterSet(chunkSizeMs = 1000), 1),
        )
        val executions = listOf(
            RunExecution("exec-1", "plan-1", "exp-1", status = RunExecutionStatus.COMPLETED),
            RunExecution("exec-2", "plan-2", "exp-1", status = RunExecutionStatus.COMPLETED),
        )
        val metrics = listOf(
            MetricResult("exec-1", wer = 0.2, qualityScore = 0.7),
            MetricResult("exec-2", wer = 0.1, qualityScore = 0.9),
        )

        val summary = ranker.summarize(experiment, executions, plans, metrics)

        assertEquals(2, summary.rankedResults.size)
        assertEquals("exec-2", summary.rankedResults[0].executionId) // Higher score first
        assertEquals(1, summary.rankedResults[0].rank)
        assertEquals("exec-1", summary.rankedResults[1].executionId)
        assertEquals(2, summary.rankedResults[1].rank)
        assertEquals(0.9, summary.bestQualityScore)
    }

    @Test
    fun `summarize counts completed and failed runs`() {
        val experiment = ExperimentDefinition("exp-2", "Count Test", "", createdAtEpochMillis = 0)
        val executions = listOf(
            RunExecution("e1", "p1", "exp-2", status = RunExecutionStatus.COMPLETED),
            RunExecution("e2", "p2", "exp-2", status = RunExecutionStatus.FAILED),
            RunExecution("e3", "p3", "exp-2", status = RunExecutionStatus.COMPLETED),
        )
        val summary = ranker.summarize(experiment, executions, emptyList(), emptyList())

        assertEquals(3, summary.totalRuns)
        assertEquals(2, summary.completedRuns)
        assertEquals(1, summary.failedRuns)
    }

    @Test
    fun `summarize handles empty results`() {
        val experiment = ExperimentDefinition("exp-3", "Empty", "", createdAtEpochMillis = 0)
        val summary = ranker.summarize(experiment, emptyList(), emptyList(), emptyList())

        assertEquals(0, summary.totalRuns)
        assertEquals(null, summary.bestParameterSet)
        assertEquals(null, summary.bestQualityScore)
        assertTrue(summary.rankedResults.isEmpty())
    }

    @Test
    fun `metrics without quality score are excluded from ranking`() {
        val experiment = ExperimentDefinition("exp-4", "NoScore", "", createdAtEpochMillis = 0)
        val executions = listOf(
            RunExecution("e1", "p1", "exp-4", status = RunExecutionStatus.COMPLETED),
        )
        val metrics = listOf(
            MetricResult("e1", wer = 0.3, qualityScore = null),
        )
        val summary = ranker.summarize(experiment, executions, emptyList(), metrics)

        assertTrue(summary.rankedResults.isEmpty())
    }
}
