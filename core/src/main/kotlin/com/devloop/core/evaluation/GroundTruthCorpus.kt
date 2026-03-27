package com.devloop.core.evaluation

import com.devloop.core.domain.experiment.ScenarioId
import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer

/**
 * Manages ground-truth transcripts for experiment evaluation.
 *
 * Expected directory layout:
 * ```
 * corpus/
 *   scenario-a.txt          ← plain text transcript
 *   scenario-b.txt
 *   metadata.properties     ← optional: scenarioId=filename mappings
 * ```
 *
 * Files are loaded lazily and cached. Supports two lookup modes:
 * 1. By scenario ID (via metadata.properties mapping)
 * 2. By filename (scenario name normalized to filename)
 */
class GroundTruthCorpus(private val corpusDir: Path) {

    private val cache = mutableMapOf<String, String>()
    private val scenarioMapping: Map<String, String> by lazy { loadMapping() }

    /**
     * Returns the ground-truth transcript for a scenario, or null if not found.
     * Lookup order:
     * 1. metadata.properties mapping (scenarioId → filename)
     * 2. Direct filename match: `{scenarioId}.txt`
     * 3. Normalized name match: `{scenarioName-normalized}.txt`
     */
    fun getTranscript(scenarioId: ScenarioId, scenarioName: String? = null): String? {
        // 1. Check mapping
        val mappedFile = scenarioMapping[scenarioId]
        if (mappedFile != null) {
            return loadFile(mappedFile)
        }

        // 2. Direct ID match
        val byId = loadFile("$scenarioId.txt")
        if (byId != null) return byId

        // 3. Normalized name
        if (scenarioName != null) {
            val normalized = normalizeName(scenarioName)
            return loadFile("$normalized.txt")
        }

        return null
    }

    /**
     * Lists all available ground-truth files in the corpus directory.
     */
    fun listAvailable(): List<String> {
        if (!Files.isDirectory(corpusDir)) return emptyList()
        return Files.list(corpusDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".txt") }
                .map { it.fileName.toString().removeSuffix(".txt") }
                .toList()
        }
    }

    /**
     * Adds or updates a ground-truth transcript in the corpus.
     */
    fun putTranscript(name: String, content: String) {
        Files.createDirectories(corpusDir)
        val file = corpusDir.resolve("${normalizeName(name)}.txt")
        Files.writeString(file, content)
        cache[file.fileName.toString()] = content
    }

    /**
     * Returns all transcripts as a map of name → content.
     */
    fun loadAll(): Map<String, String> {
        if (!Files.isDirectory(corpusDir)) return emptyMap()
        return Files.list(corpusDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".txt") }
                .toList()
        }.associate { path ->
            val name = path.fileName.toString().removeSuffix(".txt")
            val content = cache.getOrPut(path.fileName.toString()) {
                Files.readString(path).trim()
            }
            name to content
        }
    }

    // ── Internals ────────────────────────────────────────────────────────

    private fun loadFile(filename: String): String? {
        cache[filename]?.let { return it }
        val path = corpusDir.resolve(filename)
        if (!Files.isRegularFile(path)) return null
        val content = Files.readString(path).trim()
        cache[filename] = content
        return content
    }

    private fun loadMapping(): Map<String, String> {
        val propsFile = corpusDir.resolve("metadata.properties")
        if (!Files.isRegularFile(propsFile)) return emptyMap()
        return Files.readAllLines(propsFile)
            .filter { it.contains('=') && !it.trimStart().startsWith('#') }
            .associate { line ->
                val (key, value) = line.split('=', limit = 2)
                key.trim() to value.trim()
            }
    }

    companion object {
        fun normalizeName(name: String): String {
            // Decompose accented characters, then strip diacritical marks
            val normalized = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD)
            return normalized
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
        }
    }
}
