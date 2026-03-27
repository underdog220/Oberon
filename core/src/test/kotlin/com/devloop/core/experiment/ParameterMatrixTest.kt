package com.devloop.core.experiment

import com.devloop.core.domain.experiment.ParameterMatrix
import com.devloop.core.domain.experiment.TranscriptionEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class ParameterMatrixTest {

    @Test
    fun `single value matrix produces one parameter set`() {
        val matrix = ParameterMatrix()
        val expanded = matrix.expand()
        assertEquals(1, expanded.size)
        assertEquals(16_000, expanded[0].sampleRateHz)
        assertEquals(1000, expanded[0].chunkSizeMs)
    }

    @Test
    fun `matrix size matches cartesian product`() {
        val matrix = ParameterMatrix(
            chunkSizes = listOf(500, 1000, 2000),
            engines = listOf(TranscriptionEngine.WHISPER_LOCAL, TranscriptionEngine.WHISPER_CLOUD),
            models = listOf("base", "small"),
        )
        assertEquals(3 * 2 * 2, matrix.size())
        assertEquals(matrix.size(), matrix.expand().size)
    }

    @Test
    fun `expanded sets cover all combinations`() {
        val matrix = ParameterMatrix(
            chunkSizes = listOf(500, 1000),
            vadSensitivities = listOf(0.3, 0.7),
        )
        val sets = matrix.expand()
        assertEquals(4, sets.size)
        val combos = sets.map { it.chunkSizeMs to it.vadSensitivity }.toSet()
        assertEquals(
            setOf(500 to 0.3, 500 to 0.7, 1000 to 0.3, 1000 to 0.7),
            combos
        )
    }

    @Test
    fun `empty dimension lists produce empty result`() {
        val matrix = ParameterMatrix(chunkSizes = emptyList())
        assertEquals(0, matrix.size())
        assertEquals(0, matrix.expand().size)
    }
}
