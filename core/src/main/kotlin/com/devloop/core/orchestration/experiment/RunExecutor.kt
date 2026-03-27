package com.devloop.core.orchestration.experiment

import com.devloop.core.domain.experiment.*

/**
 * Abstraction for executing a single run plan.
 *
 * The bridge module provides a Panoptico-backed implementation;
 * tests can use a simple stub.
 */
interface RunExecutor {
    /**
     * Executes a single run and returns the result.
     * Implementations handle submission, polling, and result retrieval.
     */
    suspend fun execute(
        execution: RunExecution,
        plan: RunPlan,
        scenario: ScenarioDefinition?,
    ): RunResult
}
