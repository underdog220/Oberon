package com.devloop.core.evaluation

import com.devloop.core.domain.experiment.*

/**
 * Ranks completed run executions by their metric results.
 * Produces the final [ExperimentSummary] with a ranking table.
 */
class ExperimentRanker {

    /**
     * Ranks all metric results by quality score (descending)
     * and produces a summary for the experiment.
     */
    fun summarize(
        experiment: ExperimentDefinition,
        executions: List<RunExecution>,
        plans: List<RunPlan>,
        metrics: List<MetricResult>,
    ): ExperimentSummary {
        val planById = plans.associateBy { it.id }
        val executionById = executions.associateBy { it.id }

        val ranked = metrics
            .filter { it.qualityScore != null }
            .sortedByDescending { it.qualityScore }
            .mapIndexed { index, metric ->
                val execution = executionById[metric.executionId]
                val plan = execution?.runPlanId?.let { planById[it] }
                RankedResult(
                    rank = index + 1,
                    executionId = metric.executionId,
                    parameters = plan?.parameters ?: ParameterSet(),
                    metrics = metric,
                )
            }

        val completed = executions.count { it.status == RunExecutionStatus.COMPLETED }
        val failed = executions.count { it.status == RunExecutionStatus.FAILED }

        return ExperimentSummary(
            experimentId = experiment.id,
            experimentName = experiment.name,
            totalRuns = executions.size,
            completedRuns = completed,
            failedRuns = failed,
            bestParameterSet = ranked.firstOrNull()?.parameters,
            bestQualityScore = ranked.firstOrNull()?.metrics?.qualityScore,
            rankedResults = ranked,
            generatedAtEpochMillis = System.currentTimeMillis(),
        )
    }
}
