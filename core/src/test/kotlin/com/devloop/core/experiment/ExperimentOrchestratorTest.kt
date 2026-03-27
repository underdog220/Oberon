package com.devloop.core.experiment

import com.devloop.core.domain.experiment.*
import com.devloop.core.orchestration.experiment.ExperimentOrchestrator
import com.devloop.core.orchestration.experiment.RunExecutor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExperimentOrchestratorTest {

    private val repo = InMemoryExperimentRepository()

    /** Simple executor that returns a fixed transcript. */
    private val stubExecutor = object : RunExecutor {
        var callCount = 0
        override suspend fun execute(
            execution: RunExecution,
            plan: RunPlan,
            scenario: ScenarioDefinition?,
        ): RunResult {
            callCount++
            return RunResult(
                executionId = execution.id,
                transcript = "Dies ist ein Testtranskript",
                durationMs = 2000,
                timeToFirstTranscriptMs = 800,
                parameters = plan.parameters,
            )
        }
    }

    private val orchestrator = ExperimentOrchestrator(repo, stubExecutor)

    @Test
    fun `planExperiment creates run plans for all scenario-parameter combinations`() = runTest {
        val experiment = createExperiment()
        val scenario1 = createScenario(experiment.id, "Szenario A")
        val scenario2 = createScenario(experiment.id, "Szenario B")

        val matrix = ParameterMatrix(
            chunkSizes = listOf(500, 1000),
            engines = listOf(TranscriptionEngine.WHISPER_LOCAL),
        )

        val plans = orchestrator.planExperiment(experiment.id, matrix)
        assertEquals(4, plans.size) // 2 scenarios × 2 chunk sizes

        val updated = repo.getExperiment(experiment.id)
        assertEquals(ExperimentStatus.PLANNED, updated?.status)
    }

    @Test
    fun `executeExperiment runs all plans and produces summary`() = runTest {
        val experiment = createExperiment()
        createScenario(experiment.id, "Test", groundTruth = "Dies ist ein Testtranskript")

        val matrix = ParameterMatrix(
            chunkSizes = listOf(500, 1000),
        )
        orchestrator.planExperiment(experiment.id, matrix)

        val summary = orchestrator.executeExperiment(experiment.id)

        assertEquals(2, summary.totalRuns)
        assertEquals(2, summary.completedRuns)
        assertEquals(0, summary.failedRuns)
        assertNotNull(summary.bestParameterSet)
        assertNotNull(summary.bestQualityScore)
        assertTrue(summary.rankedResults.isNotEmpty())
        assertEquals(2, stubExecutor.callCount)
    }

    @Test
    fun `executeExperiment handles failing executor`() = runTest {
        val failingExecutor = object : RunExecutor {
            override suspend fun execute(
                execution: RunExecution,
                plan: RunPlan,
                scenario: ScenarioDefinition?,
            ): RunResult {
                error("Panoptico connection failed")
            }
        }
        val orch = ExperimentOrchestrator(repo, failingExecutor)

        val experiment = createExperiment("fail-exp")
        createScenario(experiment.id, "Test")

        orch.planExperiment(experiment.id, ParameterMatrix())

        val summary = orch.executeExperiment(experiment.id)
        assertEquals(1, summary.totalRuns)
        assertEquals(0, summary.completedRuns)
        assertEquals(1, summary.failedRuns)

        val expStatus = repo.getExperiment(experiment.id)?.status
        assertEquals(ExperimentStatus.FAILED, expStatus)
    }

    @Test
    fun `getSummary returns null for unknown experiment`() = runTest {
        val summary = orchestrator.getSummary("nonexistent")
        assertEquals(null, summary)
    }

    @Test
    fun `progress callback reports correctly`() = runTest {
        val experiment = createExperiment()
        createScenario(experiment.id, "S1")
        orchestrator.planExperiment(experiment.id, ParameterMatrix())

        val progressUpdates = mutableListOf<Pair<Int, Int>>()
        orchestrator.executeExperiment(experiment.id) { completed, total ->
            progressUpdates += completed to total
        }

        assertTrue(progressUpdates.isNotEmpty())
        assertEquals(1 to 1, progressUpdates.last())
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private suspend fun createExperiment(id: String = "exp-${System.nanoTime()}"): ExperimentDefinition {
        val exp = ExperimentDefinition(
            id = id,
            name = "Test Experiment",
            description = "Automated test",
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        repo.upsertExperiment(exp)
        return exp
    }

    private suspend fun createScenario(
        experimentId: ExperimentId = "exp-1",
        name: String = "Default",
        groundTruth: String? = null,
    ): ScenarioDefinition {
        val scenario = ScenarioDefinition(
            id = "sc-${System.nanoTime()}",
            experimentId = experimentId,
            name = name,
            groundTruthTranscript = groundTruth,
        )
        repo.upsertScenario(scenario)
        return scenario
    }
}
