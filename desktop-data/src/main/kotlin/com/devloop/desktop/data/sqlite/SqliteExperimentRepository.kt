package com.devloop.desktop.data.sqlite

import com.devloop.core.domain.experiment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.sql.Connection
import java.sql.ResultSet

/**
 * SQLite-backed [ExperimentRepository] for the desktop module.
 *
 * Uses a separate database file (`experiments.db`) to keep experiment data
 * isolated from the main DevLoop database.
 */
class SqliteExperimentRepository(dbPath: Path) : ExperimentRepository {

    private val db = SqliteDevLoopDatabase(dbPath)

    private val experimentsCache = MutableStateFlow<List<ExperimentDefinition>>(emptyList())
    private val executionsCache = MutableStateFlow<Map<ExperimentId, List<RunExecution>>>(emptyMap())

    init {
        db.tx { initExperimentSchema() }
        refresh()
    }

    private fun refresh() {
        db.tx {
            experimentsCache.value = loadExperiments()
        }
    }

    private fun refreshExecutions(experimentId: ExperimentId) {
        db.tx {
            val current = executionsCache.value.toMutableMap()
            current[experimentId] = loadExecutionsForExperiment(experimentId)
            executionsCache.value = current
        }
    }

    // ── Schema ───────────────────────────────────────────────────────────

    private fun Connection.initExperimentSchema() {
        createStatement().use { st ->
            st.execute("""
                CREATE TABLE IF NOT EXISTS experiments (
                  id TEXT PRIMARY KEY NOT NULL,
                  name TEXT NOT NULL,
                  description TEXT NOT NULL DEFAULT '',
                  status TEXT NOT NULL DEFAULT 'DRAFT',
                  createdAtEpochMillis INTEGER NOT NULL,
                  updatedAtEpochMillis INTEGER NOT NULL,
                  tags TEXT NOT NULL DEFAULT ''
                )
            """.trimIndent())

            st.execute("""
                CREATE TABLE IF NOT EXISTS scenarios (
                  id TEXT PRIMARY KEY NOT NULL,
                  experimentId TEXT NOT NULL,
                  name TEXT NOT NULL,
                  description TEXT NOT NULL DEFAULT '',
                  audioScenarioType TEXT NOT NULL DEFAULT 'CLEAN_SPEECH',
                  referenceAudioUri TEXT,
                  groundTruthTranscript TEXT
                )
            """.trimIndent())

            st.execute("""
                CREATE TABLE IF NOT EXISTS run_plans (
                  id TEXT PRIMARY KEY NOT NULL,
                  experimentId TEXT NOT NULL,
                  scenarioId TEXT NOT NULL,
                  parametersJson TEXT NOT NULL,
                  sequenceIndex INTEGER NOT NULL,
                  repeatCount INTEGER NOT NULL DEFAULT 1
                )
            """.trimIndent())

            st.execute("""
                CREATE TABLE IF NOT EXISTS run_executions (
                  id TEXT PRIMARY KEY NOT NULL,
                  runPlanId TEXT NOT NULL,
                  experimentId TEXT NOT NULL,
                  repeatIndex INTEGER NOT NULL DEFAULT 0,
                  status TEXT NOT NULL DEFAULT 'PENDING',
                  startedAtEpochMillis INTEGER,
                  finishedAtEpochMillis INTEGER,
                  panopticoRunId TEXT,
                  errorMessage TEXT
                )
            """.trimIndent())

            st.execute("""
                CREATE TABLE IF NOT EXISTS run_results (
                  executionId TEXT PRIMARY KEY NOT NULL,
                  transcript TEXT,
                  durationMs INTEGER,
                  timeToFirstTranscriptMs INTEGER,
                  parametersJson TEXT NOT NULL,
                  artifactUris TEXT NOT NULL DEFAULT '',
                  rawOutput TEXT
                )
            """.trimIndent())

            st.execute("""
                CREATE TABLE IF NOT EXISTS metric_results (
                  executionId TEXT PRIMARY KEY NOT NULL,
                  wer REAL,
                  cer REAL,
                  technicalTermAccuracy REAL,
                  numberAccuracy REAL,
                  timeToFirstTranscriptMs INTEGER,
                  totalDurationMs INTEGER,
                  stable INTEGER NOT NULL DEFAULT 1,
                  qualityScore REAL
                )
            """.trimIndent())
        }
    }

    // ── Experiments ──────────────────────────────────────────────────────

    override fun observeExperiments(): Flow<List<ExperimentDefinition>> =
        experimentsCache.asStateFlow()

    override suspend fun getExperiment(id: ExperimentId): ExperimentDefinition? =
        withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("SELECT * FROM experiments WHERE id = ?").use { ps ->
                    ps.setString(1, id)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.toExperiment() else null }
                }
            }
        }

    override suspend fun upsertExperiment(experiment: ExperimentDefinition) =
        withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("""
                    INSERT OR REPLACE INTO experiments (id, name, description, status, createdAtEpochMillis, updatedAtEpochMillis, tags)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()).use { ps ->
                    ps.setString(1, experiment.id)
                    ps.setString(2, experiment.name)
                    ps.setString(3, experiment.description)
                    ps.setString(4, experiment.status.name)
                    ps.setLong(5, experiment.createdAtEpochMillis)
                    ps.setLong(6, experiment.updatedAtEpochMillis)
                    ps.setString(7, experiment.tags.joinToString(","))
                    ps.executeUpdate()
                }
            }
            refresh()
        }

    override suspend fun deleteExperiment(id: ExperimentId): Unit = withContext(Dispatchers.IO) {
        db.tx {
            prepareStatement("DELETE FROM experiments WHERE id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeUpdate()
            }
        }
        refresh()
    }

    // ── Scenarios ────────────────────────────────────────────────────────

    override suspend fun getScenariosForExperiment(experimentId: ExperimentId): List<ScenarioDefinition> =
        withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("SELECT * FROM scenarios WHERE experimentId = ?").use { ps ->
                    ps.setString(1, experimentId)
                    ps.executeQuery().use { rs ->
                        buildList { while (rs.next()) add(rs.toScenario()) }
                    }
                }
            }
        }

    override suspend fun upsertScenario(scenario: ScenarioDefinition): Unit =
        withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("""
                    INSERT OR REPLACE INTO scenarios (id, experimentId, name, description, audioScenarioType, referenceAudioUri, groundTruthTranscript)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()).use { ps ->
                    ps.setString(1, scenario.id)
                    ps.setString(2, scenario.experimentId)
                    ps.setString(3, scenario.name)
                    ps.setString(4, scenario.description)
                    ps.setString(5, scenario.audioScenarioType.name)
                    ps.setString(6, scenario.referenceAudioUri)
                    ps.setString(7, scenario.groundTruthTranscript)
                    ps.executeUpdate()
                }
            }
        }

    override suspend fun deleteScenario(id: ScenarioId): Unit = withContext(Dispatchers.IO) {
        db.tx {
            prepareStatement("DELETE FROM scenarios WHERE id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeUpdate()
            }
        }
    }

    // ── Run Plans ────────────────────────────────────────────────────────

    override suspend fun getRunPlansForExperiment(experimentId: ExperimentId): List<RunPlan> =
        withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("SELECT * FROM run_plans WHERE experimentId = ? ORDER BY sequenceIndex").use { ps ->
                    ps.setString(1, experimentId)
                    ps.executeQuery().use { rs ->
                        buildList { while (rs.next()) add(rs.toRunPlan()) }
                    }
                }
            }
        }

    override suspend fun upsertRunPlan(plan: RunPlan): Unit = withContext(Dispatchers.IO) {
        db.tx {
            prepareStatement("""
                INSERT OR REPLACE INTO run_plans (id, experimentId, scenarioId, parametersJson, sequenceIndex, repeatCount)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { ps ->
                ps.setString(1, plan.id)
                ps.setString(2, plan.experimentId)
                ps.setString(3, plan.scenarioId)
                ps.setString(4, plan.parameters.toJson())
                ps.setInt(5, plan.sequenceIndex)
                ps.setInt(6, plan.repeatCount)
                ps.executeUpdate()
            }
        }
    }

    // ── Executions ───────────────────────────────────────────────────────

    override fun observeExecutionsForExperiment(experimentId: ExperimentId): Flow<List<RunExecution>> =
        executionsCache.map { it[experimentId] ?: emptyList() }

    override suspend fun getExecution(id: RunExecutionId): RunExecution? =
        withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("SELECT * FROM run_executions WHERE id = ?").use { ps ->
                    ps.setString(1, id)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.toExecution() else null }
                }
            }
        }

    override suspend fun upsertExecution(execution: RunExecution): Unit = withContext(Dispatchers.IO) {
        db.tx {
            prepareStatement("""
                INSERT OR REPLACE INTO run_executions (id, runPlanId, experimentId, repeatIndex, status, startedAtEpochMillis, finishedAtEpochMillis, panopticoRunId, errorMessage)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { ps ->
                ps.setString(1, execution.id)
                ps.setString(2, execution.runPlanId)
                ps.setString(3, execution.experimentId)
                ps.setInt(4, execution.repeatIndex)
                ps.setString(5, execution.status.name)
                execution.startedAtEpochMillis?.let { ps.setLong(6, it) } ?: ps.setNull(6, java.sql.Types.BIGINT)
                execution.finishedAtEpochMillis?.let { ps.setLong(7, it) } ?: ps.setNull(7, java.sql.Types.BIGINT)
                ps.setString(8, execution.panopticoRunId)
                ps.setString(9, execution.errorMessage)
                ps.executeUpdate()
            }
        }
        refreshExecutions(execution.experimentId)
    }

    override suspend fun getExecutionsForPlan(planId: RunPlanId): List<RunExecution> =
        withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("SELECT * FROM run_executions WHERE runPlanId = ?").use { ps ->
                    ps.setString(1, planId)
                    ps.executeQuery().use { rs ->
                        buildList { while (rs.next()) add(rs.toExecution()) }
                    }
                }
            }
        }

    // ── Results ──────────────────────────────────────────────────────────

    override suspend fun upsertRunResult(result: RunResult): Unit = withContext(Dispatchers.IO) {
        db.tx {
            prepareStatement("""
                INSERT OR REPLACE INTO run_results (executionId, transcript, durationMs, timeToFirstTranscriptMs, parametersJson, artifactUris, rawOutput)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { ps ->
                ps.setString(1, result.executionId)
                ps.setString(2, result.transcript)
                result.durationMs?.let { ps.setLong(3, it) } ?: ps.setNull(3, java.sql.Types.BIGINT)
                result.timeToFirstTranscriptMs?.let { ps.setLong(4, it) } ?: ps.setNull(4, java.sql.Types.BIGINT)
                ps.setString(5, result.parameters.toJson())
                ps.setString(6, result.artifactUris.joinToString("|"))
                ps.setString(7, result.rawOutput)
                ps.executeUpdate()
            }
        }
    }

    override suspend fun getRunResult(executionId: RunExecutionId): RunResult? =
        withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("SELECT * FROM run_results WHERE executionId = ?").use { ps ->
                    ps.setString(1, executionId)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.toRunResult() else null }
                }
            }
        }

    // ── Metrics ──────────────────────────────────────────────────────────

    override suspend fun upsertMetricResult(metric: MetricResult): Unit = withContext(Dispatchers.IO) {
        db.tx {
            prepareStatement("""
                INSERT OR REPLACE INTO metric_results (executionId, wer, cer, technicalTermAccuracy, numberAccuracy, timeToFirstTranscriptMs, totalDurationMs, stable, qualityScore)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { ps ->
                ps.setString(1, metric.executionId)
                metric.wer?.let { ps.setDouble(2, it) } ?: ps.setNull(2, java.sql.Types.DOUBLE)
                metric.cer?.let { ps.setDouble(3, it) } ?: ps.setNull(3, java.sql.Types.DOUBLE)
                metric.technicalTermAccuracy?.let { ps.setDouble(4, it) } ?: ps.setNull(4, java.sql.Types.DOUBLE)
                metric.numberAccuracy?.let { ps.setDouble(5, it) } ?: ps.setNull(5, java.sql.Types.DOUBLE)
                metric.timeToFirstTranscriptMs?.let { ps.setLong(6, it) } ?: ps.setNull(6, java.sql.Types.BIGINT)
                metric.totalDurationMs?.let { ps.setLong(7, it) } ?: ps.setNull(7, java.sql.Types.BIGINT)
                ps.setInt(8, if (metric.stable) 1 else 0)
                metric.qualityScore?.let { ps.setDouble(9, it) } ?: ps.setNull(9, java.sql.Types.DOUBLE)
                ps.executeUpdate()
            }
        }
    }

    override suspend fun getMetricResult(executionId: RunExecutionId): MetricResult? =
        withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("SELECT * FROM metric_results WHERE executionId = ?").use { ps ->
                    ps.setString(1, executionId)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.toMetricResult() else null }
                }
            }
        }

    override suspend fun getAllMetricsForExperiment(experimentId: ExperimentId): List<MetricResult> =
        withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("""
                    SELECT m.* FROM metric_results m
                    INNER JOIN run_executions e ON m.executionId = e.id
                    WHERE e.experimentId = ?
                """.trimIndent()).use { ps ->
                    ps.setString(1, experimentId)
                    ps.executeQuery().use { rs ->
                        buildList { while (rs.next()) add(rs.toMetricResult()) }
                    }
                }
            }
        }

    // ── ResultSet Mappers ────────────────────────────────────────────────

    private fun ResultSet.toExperiment() = ExperimentDefinition(
        id = getString("id"),
        name = getString("name"),
        description = getString("description"),
        status = ExperimentStatus.valueOf(getString("status")),
        createdAtEpochMillis = getLong("createdAtEpochMillis"),
        updatedAtEpochMillis = getLong("updatedAtEpochMillis"),
        tags = getString("tags").split(",").filter { it.isNotBlank() },
    )

    private fun ResultSet.toScenario() = ScenarioDefinition(
        id = getString("id"),
        experimentId = getString("experimentId"),
        name = getString("name"),
        description = getString("description"),
        audioScenarioType = AudioScenarioType.valueOf(getString("audioScenarioType")),
        referenceAudioUri = getString("referenceAudioUri"),
        groundTruthTranscript = getString("groundTruthTranscript"),
    )

    private fun ResultSet.toRunPlan() = RunPlan(
        id = getString("id"),
        experimentId = getString("experimentId"),
        scenarioId = getString("scenarioId"),
        parameters = parameterSetFromJson(getString("parametersJson")),
        sequenceIndex = getInt("sequenceIndex"),
        repeatCount = getInt("repeatCount"),
    )

    private fun ResultSet.toExecution(): RunExecution {
        val startMs = getLong("startedAtEpochMillis")
        val finishMs = getLong("finishedAtEpochMillis")
        return RunExecution(
            id = getString("id"),
            runPlanId = getString("runPlanId"),
            experimentId = getString("experimentId"),
            repeatIndex = getInt("repeatIndex"),
            status = RunExecutionStatus.valueOf(getString("status")),
            startedAtEpochMillis = if (wasNull()) null else startMs,
            finishedAtEpochMillis = if (wasNull()) null else finishMs,
            panopticoRunId = getString("panopticoRunId"),
            errorMessage = getString("errorMessage"),
        )
    }

    private fun ResultSet.toRunResult() = RunResult(
        executionId = getString("executionId"),
        transcript = getString("transcript"),
        durationMs = getLong("durationMs").takeIf { !wasNull() },
        timeToFirstTranscriptMs = getLong("timeToFirstTranscriptMs").takeIf { !wasNull() },
        parameters = parameterSetFromJson(getString("parametersJson")),
        artifactUris = getString("artifactUris").split("|").filter { it.isNotBlank() },
        rawOutput = getString("rawOutput"),
    )

    private fun ResultSet.toMetricResult() = MetricResult(
        executionId = getString("executionId"),
        wer = getDouble("wer").takeIf { !wasNull() },
        cer = getDouble("cer").takeIf { !wasNull() },
        technicalTermAccuracy = getDouble("technicalTermAccuracy").takeIf { !wasNull() },
        numberAccuracy = getDouble("numberAccuracy").takeIf { !wasNull() },
        timeToFirstTranscriptMs = getLong("timeToFirstTranscriptMs").takeIf { !wasNull() },
        totalDurationMs = getLong("totalDurationMs").takeIf { !wasNull() },
        stable = getInt("stable") == 1,
        qualityScore = getDouble("qualityScore").takeIf { !wasNull() },
    )

    private fun Connection.loadExperiments(): List<ExperimentDefinition> {
        return createStatement().use { st ->
            st.executeQuery("SELECT * FROM experiments ORDER BY updatedAtEpochMillis DESC").use { rs ->
                buildList { while (rs.next()) add(rs.toExperiment()) }
            }
        }
    }

    private fun Connection.loadExecutionsForExperiment(experimentId: ExperimentId): List<RunExecution> {
        return prepareStatement("SELECT * FROM run_executions WHERE experimentId = ? ORDER BY repeatIndex").use { ps ->
            ps.setString(1, experimentId)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toExecution()) }
            }
        }
    }
}

// ── JSON serialization for ParameterSet using org.json ───────────────────────

fun ParameterSet.toJson(): String {
    val o = org.json.JSONObject()
    o.put("sampleRateHz", sampleRateHz)
    o.put("channels", channels)
    o.put("chunkSizeMs", chunkSizeMs)
    o.put("vadEnabled", vadEnabled)
    o.put("vadSensitivity", vadSensitivity)
    o.put("silenceThresholdMs", silenceThresholdMs)
    o.put("debounceMs", debounceMs)
    o.put("transcriptionEngine", transcriptionEngine.name)
    o.put("transcriptionModel", transcriptionModel)
    o.put("language", language)
    if (extras.isNotEmpty()) {
        val extrasObj = org.json.JSONObject()
        extras.forEach { (k, v) -> extrasObj.put(k, v) }
        o.put("extras", extrasObj)
    }
    return o.toString()
}

fun parameterSetFromJson(json: String): ParameterSet {
    val o = org.json.JSONObject(json)
    val extrasMap = mutableMapOf<String, String>()
    o.optJSONObject("extras")?.let { ext ->
        for (key in ext.keys()) { extrasMap[key] = ext.optString(key, "") }
    }
    return ParameterSet(
        sampleRateHz = o.optInt("sampleRateHz", 16_000),
        channels = o.optInt("channels", 1),
        chunkSizeMs = o.optInt("chunkSizeMs", 1000),
        vadEnabled = o.optBoolean("vadEnabled", true),
        vadSensitivity = o.optDouble("vadSensitivity", 0.5),
        silenceThresholdMs = o.optInt("silenceThresholdMs", 500),
        debounceMs = o.optInt("debounceMs", 300),
        transcriptionEngine = o.optString("transcriptionEngine", "WHISPER_LOCAL").let {
            runCatching { TranscriptionEngine.valueOf(it) }.getOrDefault(TranscriptionEngine.WHISPER_LOCAL)
        },
        transcriptionModel = o.optString("transcriptionModel", "base"),
        language = o.optString("language", "de"),
        extras = extrasMap,
    )
}
