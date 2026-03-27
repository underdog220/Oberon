package com.devloop.core.orchestration.experiment

import com.devloop.core.domain.experiment.*
import com.devloop.core.evaluation.ExperimentRanker
import com.devloop.core.evaluation.GroundTruthCorpus
import com.devloop.core.evaluation.TranscriptionEvaluator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Plans and orchestrates experiment runs.
 *
 * Responsibilities:
 * - Generate [RunPlan]s from scenarios × parameter matrix
 * - Delegate execution to a [RunExecutor] (which wraps Panoptico)
 * - Collect results and evaluate metrics (with optional [GroundTruthCorpus])
 * - Produce experiment summaries with rankings
 * - Support parallel execution with configurable concurrency
 *
 * This class is framework-agnostic; coroutine scope is managed by the caller
 * (ViewModel or service layer).
 */
class ExperimentOrchestrator(
    private val repository: ExperimentRepository,
    private val executor: RunExecutor,
    private val evaluator: TranscriptionEvaluator = TranscriptionEvaluator(),
    private val ranker: ExperimentRanker = ExperimentRanker(),
    /** Optional file-based ground-truth corpus. Falls back to inline ScenarioDefinition.groundTruthTranscript. */
    private val groundTruthCorpus: GroundTruthCorpus? = null,
    /** Max concurrent runs. 1 = sequential (default). */
    private val concurrency: Int = 1,
) {

    // ── Planning ─────────────────────────────────────────────────────────

    /**
     * Creates [RunPlan]s for all scenarios × parameter-set combinations.
     * Updates experiment status to PLANNED.
     */
    suspend fun planExperiment(
        experimentId: ExperimentId,
        matrix: ParameterMatrix,
    ): List<RunPlan> {
        val experiment = repository.getExperiment(experimentId)
            ?: error("Experiment not found: $experimentId")

        val scenarios = repository.getScenariosForExperiment(experimentId)
        require(scenarios.isNotEmpty()) { "No scenarios defined for experiment $experimentId" }

        val paramSets = matrix.expand()
        var sequenceIndex = 0

        val plans = scenarios.flatMap { scenario ->
            paramSets.map { params ->
                RunPlan(
                    id = UUID.randomUUID().toString(),
                    experimentId = experimentId,
                    scenarioId = scenario.id,
                    parameters = params,
                    sequenceIndex = sequenceIndex++,
                )
            }
        }

        plans.forEach { repository.upsertRunPlan(it) }
        repository.upsertExperiment(
            experiment.copy(
                status = ExperimentStatus.PLANNED,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        )
        return plans
    }

    // ── Execution ────────────────────────────────────────────────────────

    /**
     * Executes all pending plans for an experiment.
     * Uses [concurrency] to control how many runs execute in parallel.
     * Updates experiment status to RUNNING, then COMPLETED or FAILED.
     *
     * @param onProgress called after each run with (completedCount, totalCount)
     */
    suspend fun executeExperiment(
        experimentId: ExperimentId,
        onProgress: (suspend (completed: Int, total: Int) -> Unit)? = null,
    ): ExperimentSummary {
        val experiment = repository.getExperiment(experimentId)
            ?: error("Experiment not found: $experimentId")

        repository.upsertExperiment(
            experiment.copy(
                status = ExperimentStatus.RUNNING,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        )

        val plans = repository.getRunPlansForExperiment(experimentId)
        val scenarios = repository.getScenariosForExperiment(experimentId)
            .associateBy { it.id }

        // Build flat list of (plan, repeatIndex) pairs
        val work = plans.flatMap { plan ->
            (0 until plan.repeatCount).map { plan to it }
        }
        val totalCount = work.size
        val completedCounter = AtomicInteger(0)

        val executions = if (concurrency <= 1) {
            // Sequential execution
            work.map { (plan, repeatIdx) ->
                val exec = createAndRun(experimentId, plan, repeatIdx, scenarios)
                val done = completedCounter.incrementAndGet()
                onProgress?.invoke(done, totalCount)
                exec
            }
        } else {
            // Parallel execution with semaphore
            val semaphore = Semaphore(concurrency)
            coroutineScope {
                work.map { (plan, repeatIdx) ->
                    async {
                        semaphore.withPermit {
                            val exec = createAndRun(experimentId, plan, repeatIdx, scenarios)
                            val done = completedCounter.incrementAndGet()
                            onProgress?.invoke(done, totalCount)
                            exec
                        }
                    }
                }.awaitAll()
            }
        }

        // Determine final status
        val allMetrics = repository.getAllMetricsForExperiment(experimentId)
        val finalStatus = if (executions.all { it.status == RunExecutionStatus.FAILED }) {
            ExperimentStatus.FAILED
        } else {
            ExperimentStatus.COMPLETED
        }

        val updated = repository.getExperiment(experimentId)!!
        repository.upsertExperiment(
            updated.copy(
                status = finalStatus,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        )

        return ranker.summarize(
            experiment = updated.copy(status = finalStatus),
            executions = executions,
            plans = plans,
            metrics = allMetrics,
        )
    }

    // ── Single Run ───────────────────────────────────────────────────────

    private suspend fun createAndRun(
        experimentId: ExperimentId,
        plan: RunPlan,
        repeatIdx: Int,
        scenarios: Map<ScenarioId, ScenarioDefinition>,
    ): RunExecution {
        val execution = RunExecution(
            id = UUID.randomUUID().toString(),
            runPlanId = plan.id,
            experimentId = experimentId,
            repeatIndex = repeatIdx,
            status = RunExecutionStatus.PENDING,
        )
        repository.upsertExecution(execution)
        return executeSingleRun(execution, plan, scenarios[plan.scenarioId])
    }

    private suspend fun executeSingleRun(
        execution: RunExecution,
        plan: RunPlan,
        scenario: ScenarioDefinition?,
    ): RunExecution {
        val running = execution.copy(
            status = RunExecutionStatus.RUNNING,
            startedAtEpochMillis = System.currentTimeMillis(),
        )
        repository.upsertExecution(running)

        return try {
            val runResult = executor.execute(running, plan, scenario)
            repository.upsertRunResult(runResult)

            // Resolve ground truth: corpus first, then inline
            val groundTruth = resolveGroundTruth(scenario)
            val metric = evaluator.evaluate(runResult, groundTruth)
            repository.upsertMetricResult(metric)

            val finished = running.copy(
                status = RunExecutionStatus.COMPLETED,
                finishedAtEpochMillis = System.currentTimeMillis(),
            )
            repository.upsertExecution(finished)
            finished
        } catch (e: Exception) {
            val failed = running.copy(
                status = RunExecutionStatus.FAILED,
                finishedAtEpochMillis = System.currentTimeMillis(),
                errorMessage = e.message,
            )
            repository.upsertExecution(failed)
            failed
        }
    }

    /**
     * Resolves ground truth for a scenario:
     * 1. File-based corpus (by scenario ID, then by name)
     * 2. Inline ScenarioDefinition.groundTruthTranscript
     */
    private fun resolveGroundTruth(scenario: ScenarioDefinition?): String? {
        if (scenario == null) return null
        // Try corpus first
        groundTruthCorpus?.getTranscript(scenario.id, scenario.name)?.let { return it }
        // Fall back to inline
        return scenario.groundTruthTranscript
    }

    // ── Summary / Reports ────────────────────────────────────────────────

    /**
     * Generates a summary for a (possibly still running) experiment.
     */
    suspend fun getSummary(experimentId: ExperimentId): ExperimentSummary? {
        val experiment = repository.getExperiment(experimentId) ?: return null
        val executions = repository.getRunPlansForExperiment(experimentId)
            .flatMap { repository.getExecutionsForPlan(it.id) }
        val plans = repository.getRunPlansForExperiment(experimentId)
        val metrics = repository.getAllMetricsForExperiment(experimentId)
        return ranker.summarize(experiment, executions, plans, metrics)
    }
}
