package com.devloop.core.experiment

import com.devloop.core.evaluation.GroundTruthCorpus
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroundTruthCorpusTest {

    @Test
    fun `loads transcript by scenario ID`() {
        val dir = Files.createTempDirectory("corpus-test")
        try {
            Files.writeString(dir.resolve("sc-123.txt"), "Der Patient hat Fieber.")
            val corpus = GroundTruthCorpus(dir)
            assertEquals("Der Patient hat Fieber.", corpus.getTranscript("sc-123"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `loads transcript by normalized scenario name`() {
        val dir = Files.createTempDirectory("corpus-test")
        try {
            Files.writeString(dir.resolve("lautes-buero-diktat.txt"), "Diagnose Hypertonie")
            val corpus = GroundTruthCorpus(dir)
            assertEquals(
                "Diagnose Hypertonie",
                corpus.getTranscript("unknown-id", "Lautes Buero Diktat")
            )
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `uses metadata properties mapping`() {
        val dir = Files.createTempDirectory("corpus-test")
        try {
            Files.writeString(dir.resolve("ref-audio-1.txt"), "Mapped transcript")
            Files.writeString(dir.resolve("metadata.properties"), "sc-mapped=ref-audio-1.txt")
            val corpus = GroundTruthCorpus(dir)
            assertEquals("Mapped transcript", corpus.getTranscript("sc-mapped"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `returns null for missing transcript`() {
        val dir = Files.createTempDirectory("corpus-test")
        try {
            val corpus = GroundTruthCorpus(dir)
            assertNull(corpus.getTranscript("nonexistent"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `listAvailable returns txt files`() {
        val dir = Files.createTempDirectory("corpus-test")
        try {
            Files.writeString(dir.resolve("a.txt"), "A")
            Files.writeString(dir.resolve("b.txt"), "B")
            Files.writeString(dir.resolve("readme.md"), "not a transcript")
            val corpus = GroundTruthCorpus(dir)
            val available = corpus.listAvailable()
            assertEquals(2, available.size)
            assertTrue(available.containsAll(listOf("a", "b")))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `putTranscript creates file and is retrievable`() {
        val dir = Files.createTempDirectory("corpus-test")
        try {
            val corpus = GroundTruthCorpus(dir)
            corpus.putTranscript("Neuer Test", "Inhalt des Transkripts")
            assertEquals("Inhalt des Transkripts", corpus.getTranscript("x", "Neuer Test"))
            assertTrue(Files.exists(dir.resolve("neuer-test.txt")))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `loadAll returns all transcripts`() {
        val dir = Files.createTempDirectory("corpus-test")
        try {
            Files.writeString(dir.resolve("first.txt"), "First")
            Files.writeString(dir.resolve("second.txt"), "Second")
            val corpus = GroundTruthCorpus(dir)
            val all = corpus.loadAll()
            assertEquals(2, all.size)
            assertEquals("First", all["first"])
            assertEquals("Second", all["second"])
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `normalizeName handles special characters`() {
        assertEquals("lautes-buro-diktat", GroundTruthCorpus.normalizeName("Lautes Büro-Diktat!"))
        assertEquals("test-123", GroundTruthCorpus.normalizeName("Test 123"))
        assertEquals("simple", GroundTruthCorpus.normalizeName("simple"))
    }
}
