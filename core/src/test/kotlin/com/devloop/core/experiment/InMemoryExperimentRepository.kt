package com.devloop.core.experiment

import com.devloop.core.domain.experiment.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [ExperimentRepository] for unit tests.
 */
class InMemoryExperimentRepository : ExperimentRepository {

    private val experiments = MutableStateFlow<Map<ExperimentId, ExperimentDefinition>>(emptyMap())
    private val scenarios = mutableMapOf<ScenarioId, ScenarioDefinition>()
    private val runPlans = mutableMapOf<RunPlanId, RunPlan>()
    private val executions = MutableStateFlow<Map<RunExecutionId, RunExecution>>(emptyMap())
    private val runResults = mutableMapOf<RunExecutionId, RunResult>()
    private val metricResults = mutableMapOf<RunExecutionId, MetricResult>()

    override fun observeExperiments(): Flow<List<ExperimentDefinition>> =
        experiments.map { it.values.toList() }

    override suspend fun getExperiment(id: ExperimentId): ExperimentDefinition? =
        experiments.value[id]

    override suspend fun upsertExperiment(experiment: ExperimentDefinition) {
        experiments.value = experiments.value + (experiment.id to experiment)
    }

    override suspend fun deleteExperiment(id: ExperimentId) {
        experiments.value = experiments.value - id
    }

    override suspend fun getScenariosForExperiment(experimentId: ExperimentId): List<ScenarioDefinition> =
        scenarios.values.filter { it.experimentId == experimentId }

    override suspend fun upsertScenario(scenario: ScenarioDefinition) {
        scenarios[scenario.id] = scenario
    }

    override suspend fun deleteScenario(id: ScenarioId) {
        scenarios.remove(id)
    }

    override suspend fun getRunPlansForExperiment(experimentId: ExperimentId): List<RunPlan> =
        runPlans.values.filter { it.experimentId == experimentId }.sortedBy { it.sequenceIndex }

    override suspend fun upsertRunPlan(plan: RunPlan) {
        runPlans[plan.id] = plan
    }

    override fun observeExecutionsForExperiment(experimentId: ExperimentId): Flow<List<RunExecution>> =
        executions.map { map -> map.values.filter { it.experimentId == experimentId } }

    override suspend fun getExecution(id: RunExecutionId): RunExecution? =
        executions.value[id]

    override suspend fun upsertExecution(execution: RunExecution) {
        executions.value = executions.value + (execution.id to execution)
    }

    override suspend fun getExecutionsForPlan(planId: RunPlanId): List<RunExecution> =
        executions.value.values.filter { it.runPlanId == planId }

    override suspend fun upsertRunResult(result: RunResult) {
        runResults[result.executionId] = result
    }

    override suspend fun getRunResult(executionId: RunExecutionId): RunResult? =
        runResults[executionId]

    override suspend fun upsertMetricResult(metric: MetricResult) {
        metricResults[metric.executionId] = metric
    }

    override suspend fun getMetricResult(executionId: RunExecutionId): MetricResult? =
        metricResults[executionId]

    override suspend fun getAllMetricsForExperiment(experimentId: ExperimentId): List<MetricResult> {
        val executionIds = executions.value.values
            .filter { it.experimentId == experimentId }
            .map { it.id }
            .toSet()
        return metricResults.values.filter { it.executionId in executionIds }
    }
}
