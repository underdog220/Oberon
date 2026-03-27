package com.devloop.core.domain.experiment

/**
 * Top-level experiment: groups scenarios, defines goals and constraints.
 */
data class ExperimentDefinition(
    val id: ExperimentId,
    val name: String,
    val description: String,
    val status: ExperimentStatus = ExperimentStatus.DRAFT,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
    val tags: List<String> = emptyList(),
)

/**
 * A scenario within an experiment: a specific test situation
 * (e.g. "noisy office, German medical dictation").
 */
data class ScenarioDefinition(
    val id: ScenarioId,
    val experimentId: ExperimentId,
    val name: String,
    val description: String = "",
    val audioScenarioType: AudioScenarioType = AudioScenarioType.CLEAN_SPEECH,
    /** Path or URI to the reference audio, if applicable. */
    val referenceAudioUri: String? = null,
    /** Ground-truth transcript for WER/CER evaluation. */
    val groundTruthTranscript: String? = null,
)

/**
 * A single set of parameters for one run.
 */
data class ParameterSet(
    val sampleRateHz: Int = 16_000,
    val channels: Int = 1,
    val chunkSizeMs: Int = 1000,
    val vadEnabled: Boolean = true,
    val vadSensitivity: Double = 0.5,
    val silenceThresholdMs: Int = 500,
    val debounceMs: Int = 300,
    val transcriptionEngine: TranscriptionEngine = TranscriptionEngine.WHISPER_LOCAL,
    val transcriptionModel: String = "base",
    val language: String = "de",
    /** Arbitrary extra parameters for future extensibility. */
    val extras: Map<String, String> = emptyMap(),
) {
    /** Compact label for UI display and comparison tables. */
    fun label(): String = "${transcriptionEngine.name}/${transcriptionModel} " +
        "chunk=${chunkSizeMs}ms vad=${vadSensitivity} silence=${silenceThresholdMs}ms"
}

/**
 * Generates the cartesian product of parameter dimensions.
 */
data class ParameterMatrix(
    val sampleRates: List<Int> = listOf(16_000),
    val chunkSizes: List<Int> = listOf(1000),
    val vadSensitivities: List<Double> = listOf(0.5),
    val silenceThresholds: List<Int> = listOf(500),
    val debounceValues: List<Int> = listOf(300),
    val engines: List<TranscriptionEngine> = listOf(TranscriptionEngine.WHISPER_LOCAL),
    val models: List<String> = listOf("base"),
    val languages: List<String> = listOf("de"),
) {
    /** Expands this matrix into all individual [ParameterSet] combinations. */
    fun expand(): List<ParameterSet> {
        val result = mutableListOf<ParameterSet>()
        for (sr in sampleRates)
            for (cs in chunkSizes)
                for (vad in vadSensitivities)
                    for (sil in silenceThresholds)
                        for (deb in debounceValues)
                            for (eng in engines)
                                for (mod in models)
                                    for (lang in languages)
                                        result += ParameterSet(
                                            sampleRateHz = sr,
                                            chunkSizeMs = cs,
                                            vadSensitivity = vad,
                                            silenceThresholdMs = sil,
                                            debounceMs = deb,
                                            transcriptionEngine = eng,
                                            transcriptionModel = mod,
                                            language = lang,
                                        )
        return result
    }

    /** Total number of combinations this matrix will produce. */
    fun size(): Int = sampleRates.size * chunkSizes.size * vadSensitivities.size *
        silenceThresholds.size * debounceValues.size * engines.size * models.size * languages.size
}

/**
 * A concrete plan for one experiment run: which scenario with which parameters.
 */
data class RunPlan(
    val id: RunPlanId,
    val experimentId: ExperimentId,
    val scenarioId: ScenarioId,
    val parameters: ParameterSet,
    val sequenceIndex: Int,
    val repeatCount: Int = 1,
)

/**
 * A single execution of a [RunPlan]. Multiple executions per plan are possible (repeats).
 */
data class RunExecution(
    val id: RunExecutionId,
    val runPlanId: RunPlanId,
    val experimentId: ExperimentId,
    val repeatIndex: Int = 0,
    val status: RunExecutionStatus = RunExecutionStatus.PENDING,
    val startedAtEpochMillis: Long? = null,
    val finishedAtEpochMillis: Long? = null,
    val panopticoRunId: String? = null,
    val errorMessage: String? = null,
)

/**
 * Raw result of a single run execution.
 */
data class RunResult(
    val executionId: RunExecutionId,
    val transcript: String? = null,
    val durationMs: Long? = null,
    val timeToFirstTranscriptMs: Long? = null,
    val parameters: ParameterSet,
    val artifactUris: List<String> = emptyList(),
    /** Raw output/logs from Panoptico. */
    val rawOutput: String? = null,
)

/**
 * Evaluated metrics for a single run result.
 */
data class MetricResult(
    val executionId: RunExecutionId,
    val wer: Double? = null,
    val cer: Double? = null,
    val technicalTermAccuracy: Double? = null,
    val numberAccuracy: Double? = null,
    val timeToFirstTranscriptMs: Long? = null,
    val totalDurationMs: Long? = null,
    val stable: Boolean = true,
    /** Overall quality score (0.0–1.0), computed from weighted sub-metrics. */
    val qualityScore: Double? = null,
)

/**
 * Aggregated summary of an entire experiment.
 */
data class ExperimentSummary(
    val experimentId: ExperimentId,
    val experimentName: String,
    val totalRuns: Int,
    val completedRuns: Int,
    val failedRuns: Int,
    val bestParameterSet: ParameterSet? = null,
    val bestQualityScore: Double? = null,
    val rankedResults: List<RankedResult> = emptyList(),
    val generatedAtEpochMillis: Long,
)

/**
 * A single entry in the experiment ranking table.
 */
data class RankedResult(
    val rank: Int,
    val executionId: RunExecutionId,
    val parameters: ParameterSet,
    val metrics: MetricResult,
)
