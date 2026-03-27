package com.devloop.core.domain.experiment

import kotlinx.coroutines.flow.Flow

/**
 * Port for persisting and querying experiment data.
 * Desktop module provides a SQLite implementation; tests use in-memory.
 */
interface ExperimentRepository {

    // ── Experiments ──────────────────────────────────────────────────────

    fun observeExperiments(): Flow<List<ExperimentDefinition>>
    suspend fun getExperiment(id: ExperimentId): ExperimentDefinition?
    suspend fun upsertExperiment(experiment: ExperimentDefinition)
    suspend fun deleteExperiment(id: ExperimentId)

    // ── Scenarios ────────────────────────────────────────────────────────

    suspend fun getScenariosForExperiment(experimentId: ExperimentId): List<ScenarioDefinition>
    suspend fun upsertScenario(scenario: ScenarioDefinition)
    suspend fun deleteScenario(id: ScenarioId)

    // ── Run Plans ────────────────────────────────────────────────────────

    suspend fun getRunPlansForExperiment(experimentId: ExperimentId): List<RunPlan>
    suspend fun upsertRunPlan(plan: RunPlan)

    // ── Run Executions ───────────────────────────────────────────────────

    fun observeExecutionsForExperiment(experimentId: ExperimentId): Flow<List<RunExecution>>
    suspend fun getExecution(id: RunExecutionId): RunExecution?
    suspend fun upsertExecution(execution: RunExecution)
    suspend fun getExecutionsForPlan(planId: RunPlanId): List<RunExecution>

    // ── Results & Metrics ────────────────────────────────────────────────

    suspend fun upsertRunResult(result: RunResult)
    suspend fun getRunResult(executionId: RunExecutionId): RunResult?
    suspend fun upsertMetricResult(metric: MetricResult)
    suspend fun getMetricResult(executionId: RunExecutionId): MetricResult?
    suspend fun getAllMetricsForExperiment(experimentId: ExperimentId): List<MetricResult>
}
