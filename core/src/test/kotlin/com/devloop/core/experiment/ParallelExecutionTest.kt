package com.devloop.core.experiment

import com.devloop.core.domain.experiment.*
import com.devloop.core.orchestration.experiment.ExperimentOrchestrator
import com.devloop.core.orchestration.experiment.RunExecutor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ParallelExecutionTest {

    @Test
    fun `parallel execution completes all runs`() = runTest {
        val repo = InMemoryExperimentRepository()
        val callCount = AtomicInteger(0)
        val executor = object : RunExecutor {
            override suspend fun execute(
                execution: RunExecution,
                plan: RunPlan,
                scenario: ScenarioDefinition?,
            ): RunResult {
                callCount.incrementAndGet()
                return RunResult(
                    executionId = execution.id,
                    transcript = "Parallel result",
                    durationMs = 100,
                    parameters = plan.parameters,
                )
            }
        }

        val orchestrator = ExperimentOrchestrator(
            repository = repo,
            executor = executor,
            concurrency = 4,
        )

        val exp = ExperimentDefinition("p-exp", "Parallel Test", "", createdAtEpochMillis = 0)
        repo.upsertExperiment(exp)
        repo.upsertScenario(ScenarioDefinition("s1", "p-exp", "S1"))
        repo.upsertScenario(ScenarioDefinition("s2", "p-exp", "S2"))

        orchestrator.planExperiment("p-exp", ParameterMatrix(
            chunkSizes = listOf(500, 1000, 2000),
        ))

        val summary = orchestrator.executeExperiment("p-exp")

        assertEquals(6, summary.totalRuns) // 2 scenarios × 3 chunk sizes
        assertEquals(6, summary.completedRuns)
        assertEquals(0, summary.failedRuns)
        assertEquals(6, callCount.get())
    }

    @Test
    fun `parallel execution reports progress correctly`() = runTest {
        val repo = InMemoryExperimentRepository()
        val executor = object : RunExecutor {
            override suspend fun execute(
                execution: RunExecution,
                plan: RunPlan,
                scenario: ScenarioDefinition?,
            ): RunResult = RunResult(
                executionId = execution.id,
                transcript = "ok",
                parameters = plan.parameters,
            )
        }

        val orchestrator = ExperimentOrchestrator(repo, executor, concurrency = 2)

        val exp = ExperimentDefinition("prog-exp", "Progress Test", "", createdAtEpochMillis = 0)
        repo.upsertExperiment(exp)
        repo.upsertScenario(ScenarioDefinition("s1", "prog-exp", "S1"))

        orchestrator.planExperiment("prog-exp", ParameterMatrix(
            chunkSizes = listOf(500, 1000, 2000, 4000),
        ))

        val progressValues = mutableListOf<Pair<Int, Int>>()
        orchestrator.executeExperiment("prog-exp") { completed, total ->
            progressValues += completed to total
        }

        assertEquals(4, progressValues.size)
        // All should report total = 4
        assertTrue(progressValues.all { it.second == 4 })
        // Final completed should be 4
        assertEquals(4, progressValues.maxOf { it.first })
    }

    @Test
    fun `concurrency 1 runs sequentially`() = runTest {
        val repo = InMemoryExperimentRepository()
        val executor = object : RunExecutor {
            override suspend fun execute(
                execution: RunExecution,
                plan: RunPlan,
                scenario: ScenarioDefinition?,
            ): RunResult = RunResult(
                executionId = execution.id,
                transcript = "seq",
                parameters = plan.parameters,
            )
        }

        val orchestrator = ExperimentOrchestrator(repo, executor, concurrency = 1)

        val exp = ExperimentDefinition("seq-exp", "Seq Test", "", createdAtEpochMillis = 0)
        repo.upsertExperiment(exp)
        repo.upsertScenario(ScenarioDefinition("s1", "seq-exp", "S1"))

        orchestrator.planExperiment("seq-exp", ParameterMatrix(
            chunkSizes = listOf(500, 1000),
        ))

        val summary = orchestrator.executeExperiment("seq-exp")
        assertEquals(2, summary.completedRuns)
    }
}
