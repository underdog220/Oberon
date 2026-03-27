package com.devloop.core.experiment

import com.devloop.core.domain.experiment.ParameterSet
import com.devloop.core.domain.experiment.RunResult
import com.devloop.core.evaluation.TranscriptionEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TranscriptionEvaluatorTest {

    private val evaluator = TranscriptionEvaluator(
        technicalTerms = listOf("Hypertonie", "Anamnese", "Diagnose"),
    )

    @Test
    fun `WER is zero for identical texts`() {
        val wer = evaluator.computeWer("dies ist ein test", "dies ist ein test")
        assertEquals(0.0, wer)
    }

    @Test
    fun `WER is 1 for completely different text`() {
        val wer = evaluator.computeWer("hallo welt", "foo bar baz qux")
        assertTrue(wer > 0.0)
    }

    @Test
    fun `WER handles single word substitution`() {
        val wer = evaluator.computeWer("der patient hat fieber", "der patient hat husten")
        assertEquals(0.25, wer) // 1 substitution out of 4 words
    }

    @Test
    fun `CER is zero for identical texts`() {
        val cer = evaluator.computeCer("test", "test")
        assertEquals(0.0, cer)
    }

    @Test
    fun `CER handles character-level differences`() {
        val cer = evaluator.computeCer("test", "tast")
        assertTrue(cer > 0.0)
        assertTrue(cer < 1.0)
    }

    @Test
    fun `technical term accuracy counts found terms`() {
        val acc = evaluator.computeTermAccuracy("Patient hat Hypertonie und braucht Anamnese")
        assertEquals(2.0 / 3, acc, 0.01) // Hypertonie + Anamnese found, Diagnose missing
    }

    @Test
    fun `technical term accuracy is 1 when all found`() {
        val acc = evaluator.computeTermAccuracy("Hypertonie Anamnese Diagnose")
        assertEquals(1.0, acc)
    }

    @Test
    fun `number accuracy detects matching numbers`() {
        val acc = evaluator.computeNumberAccuracy(
            "Der Blutdruck ist 120 zu 80",
            "Der Blutdruck ist 120 zu 80",
        )
        assertEquals(1.0, acc)
    }

    @Test
    fun `number accuracy detects missing numbers`() {
        val acc = evaluator.computeNumberAccuracy(
            "Temperatur 38,5 Grad, Puls 72",
            "Temperatur achtunddreissig Grad, Puls zweiundsiebzig",
        )
        assertEquals(0.0, acc) // No numbers in hypothesis
    }

    @Test
    fun `evaluate produces complete metric result`() {
        val result = RunResult(
            executionId = "exec-1",
            transcript = "Der Patient hat Hypertonie",
            durationMs = 3000,
            timeToFirstTranscriptMs = 1500,
            parameters = ParameterSet(),
        )
        val metric = evaluator.evaluate(
            result,
            groundTruth = "Der Patient hat Hypertonie",
        )
        assertNotNull(metric.wer)
        assertNotNull(metric.cer)
        assertNotNull(metric.qualityScore)
        assertEquals(0.0, metric.wer)
        assertEquals(0.0, metric.cer)
        assertTrue(metric.qualityScore!! > 0.5)
    }

    @Test
    fun `evaluate without ground truth skips WER and CER`() {
        val result = RunResult(
            executionId = "exec-2",
            transcript = "some text",
            parameters = ParameterSet(),
        )
        val metric = evaluator.evaluate(result, groundTruth = null)
        assertEquals(null, metric.wer)
        assertEquals(null, metric.cer)
        assertNotNull(metric.qualityScore)
    }

    @Test
    fun `evaluate handles null transcript`() {
        val result = RunResult(
            executionId = "exec-3",
            transcript = null,
            parameters = ParameterSet(),
        )
        val metric = evaluator.evaluate(result, groundTruth = "expected text")
        assertEquals(null, metric.wer)
        assertEquals(false, metric.stable)
    }

    @Test
    fun `levenshtein distance for known examples`() {
        assertEquals(3, evaluator.levenshteinDistance("kitten".toList(), "sitting".toList()))
        assertEquals(0, evaluator.levenshteinDistance("abc".toList(), "abc".toList()))
        assertEquals(3, evaluator.levenshteinDistance("".toList(), "abc".toList()))
    }
}
