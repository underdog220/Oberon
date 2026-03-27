package com.devloop.core.evaluation

import com.devloop.core.domain.experiment.MetricResult
import com.devloop.core.domain.experiment.RunExecutionId
import com.devloop.core.domain.experiment.RunResult

/**
 * Evaluates transcription quality by comparing a hypothesis against a ground truth.
 *
 * Computes WER, CER, technical term accuracy, number accuracy, and latency metrics.
 * Returns a [MetricResult] with an aggregated quality score.
 */
class TranscriptionEvaluator(
    private val technicalTerms: List<String> = emptyList(),
    private val werWeight: Double = 0.4,
    private val cerWeight: Double = 0.2,
    private val termWeight: Double = 0.2,
    private val latencyWeight: Double = 0.1,
    private val stabilityWeight: Double = 0.1,
) {
    /**
     * Evaluates a single run result against a ground truth transcript.
     */
    fun evaluate(
        result: RunResult,
        groundTruth: String?,
        maxAcceptableLatencyMs: Long = 5000,
    ): MetricResult {
        val wer = if (groundTruth != null && result.transcript != null) {
            computeWer(groundTruth, result.transcript)
        } else null

        val cer = if (groundTruth != null && result.transcript != null) {
            computeCer(groundTruth, result.transcript)
        } else null

        val termAccuracy = if (groundTruth != null && result.transcript != null && technicalTerms.isNotEmpty()) {
            computeTermAccuracy(result.transcript)
        } else null

        val numberAcc = if (groundTruth != null && result.transcript != null) {
            computeNumberAccuracy(groundTruth, result.transcript)
        } else null

        val qualityScore = computeQualityScore(
            wer = wer,
            cer = cer,
            termAccuracy = termAccuracy,
            latencyMs = result.timeToFirstTranscriptMs,
            maxLatencyMs = maxAcceptableLatencyMs,
            stable = result.transcript != null,
        )

        return MetricResult(
            executionId = result.executionId,
            wer = wer,
            cer = cer,
            technicalTermAccuracy = termAccuracy,
            numberAccuracy = numberAcc,
            timeToFirstTranscriptMs = result.timeToFirstTranscriptMs,
            totalDurationMs = result.durationMs,
            stable = result.transcript != null,
            qualityScore = qualityScore,
        )
    }

    // ── WER (Word Error Rate) ────────────────────────────────────────────

    fun computeWer(reference: String, hypothesis: String): Double {
        val refWords = tokenize(reference)
        val hypWords = tokenize(hypothesis)
        if (refWords.isEmpty()) return if (hypWords.isEmpty()) 0.0 else 1.0
        val distance = levenshteinDistance(refWords, hypWords)
        return distance.toDouble() / refWords.size
    }

    // ── CER (Character Error Rate) ───────────────────────────────────────

    fun computeCer(reference: String, hypothesis: String): Double {
        val refChars = reference.lowercase().toList()
        val hypChars = hypothesis.lowercase().toList()
        if (refChars.isEmpty()) return if (hypChars.isEmpty()) 0.0 else 1.0
        val distance = levenshteinDistance(refChars, hypChars)
        return distance.toDouble() / refChars.size
    }

    // ── Technical Term Accuracy ──────────────────────────────────────────

    fun computeTermAccuracy(transcript: String): Double {
        if (technicalTerms.isEmpty()) return 1.0
        val lower = transcript.lowercase()
        val found = technicalTerms.count { lower.contains(it.lowercase()) }
        return found.toDouble() / technicalTerms.size
    }

    // ── Number Accuracy ──────────────────────────────────────────────────

    fun computeNumberAccuracy(reference: String, hypothesis: String): Double {
        val refNumbers = extractNumbers(reference)
        if (refNumbers.isEmpty()) return 1.0
        val hypNumbers = extractNumbers(hypothesis)
        val found = refNumbers.count { it in hypNumbers }
        return found.toDouble() / refNumbers.size
    }

    // ── Quality Score ────────────────────────────────────────────────────

    private fun computeQualityScore(
        wer: Double?,
        cer: Double?,
        termAccuracy: Double?,
        latencyMs: Long?,
        maxLatencyMs: Long,
        stable: Boolean,
    ): Double {
        var score = 0.0
        var totalWeight = 0.0

        if (wer != null) {
            score += werWeight * (1.0 - wer.coerceIn(0.0, 1.0))
            totalWeight += werWeight
        }
        if (cer != null) {
            score += cerWeight * (1.0 - cer.coerceIn(0.0, 1.0))
            totalWeight += cerWeight
        }
        if (termAccuracy != null) {
            score += termWeight * termAccuracy
            totalWeight += termWeight
        }
        if (latencyMs != null) {
            val latencyScore = (1.0 - (latencyMs.toDouble() / maxLatencyMs)).coerceIn(0.0, 1.0)
            score += latencyWeight * latencyScore
            totalWeight += latencyWeight
        }
        val stabilityScore = if (stable) 1.0 else 0.0
        score += stabilityWeight * stabilityScore
        totalWeight += stabilityWeight

        return if (totalWeight > 0) score / totalWeight else 0.0
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun tokenize(text: String): List<String> =
        text.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

    private fun extractNumbers(text: String): Set<String> =
        Regex("\\d+([.,]\\d+)?").findAll(text).map { it.value }.toSet()

    /**
     * Generic Levenshtein distance for lists of comparable elements.
     */
    internal fun <T> levenshteinDistance(source: List<T>, target: List<T>): Int {
        val m = source.size
        val n = target.size
        // Use two-row optimization for memory efficiency
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (source[i - 1] == target[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,       // deletion
                    curr[j - 1] + 1,   // insertion
                    prev[j - 1] + cost, // substitution
                )
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[n]
    }
}
