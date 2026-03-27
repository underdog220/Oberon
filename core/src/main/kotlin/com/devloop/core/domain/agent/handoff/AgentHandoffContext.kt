package com.devloop.core.domain.agent.handoff

import org.json.JSONArray
import org.json.JSONObject

enum class ProjectMaturity {
    PROTOTYPE, MVP, BETA, PRODUCTION, MAINTENANCE;

    companion object {
        fun fromString(raw: String): ProjectMaturity =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: PROTOTYPE
    }
}

data class ModuleInfo(
    val name: String,
    val purpose: String = "",
    val path: String = "",
    val stable: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("purpose", purpose)
        put("path", path)
        put("stable", stable)
    }

    companion object {
        fun fromJson(o: JSONObject): ModuleInfo = ModuleInfo(
            name = o.optString("name"),
            purpose = o.optString("purpose"),
            path = o.optString("path"),
            stable = o.optBoolean("stable", true),
        )
    }
}

data class EntryPoint(
    val path: String,
    val description: String = "",
    val readFirst: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("path", path)
        put("description", description)
        put("readFirst", readFirst)
    }

    companion object {
        fun fromJson(o: JSONObject): EntryPoint = EntryPoint(
            path = o.optString("path"),
            description = o.optString("description"),
            readFirst = o.optBoolean("readFirst", false),
        )
    }
}

data class RecentChange(
    val date: String,
    val summary: String,
    val impactArea: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("date", date)
        put("summary", summary)
        put("impactArea", impactArea)
    }

    companion object {
        fun fromJson(o: JSONObject): RecentChange = RecentChange(
            date = o.optString("date"),
            summary = o.optString("summary"),
            impactArea = o.optString("impactArea"),
        )
    }
}

/**
 * Strukturierter Agent-Handoff-Kontext fuer ein Projekt.
 * Wird beim Wechsel zwischen Coding Agents uebergeben, damit der neue Agent
 * schnell arbeitsbereit wird, ohne das gesamte Repository breit analysieren zu muessen.
 *
 * Schema-Version: [schemaVersion] fuer Vorwaertskompatibilitaet.
 */
data class AgentHandoffContext(
    // Sektion 1: Identitaet
    val projectName: String = "",
    val purpose: String = "",
    val maturity: ProjectMaturity = ProjectMaturity.PROTOTYPE,
    val shortDescription: String = "",

    // Sektion 2: Aktueller Stand
    val whatWorks: String = "",
    val whatsMissing: String = "",
    val currentPriority: String = "",
    val nextStep: String = "",

    // Sektion 3: Architektur
    val modules: List<ModuleInfo> = emptyList(),
    val layers: String = "",
    val dataFlow: String = "",

    // Sektion 4: Einstiegspunkte
    val primaryFiles: List<EntryPoint> = emptyList(),
    val secondaryAreas: String = "",

    // Sektion 5: Build / Test / Run
    val buildCommand: String = "",
    val testCommand: String = "",
    val runCommand: String = "",
    val envVars: String = "",
    val gotchas: String = "",

    // Sektion 6: Regeln
    val architecturePrinciples: List<String> = emptyList(),
    val doNotChange: String = "",
    val testingPolicy: String = "",

    // Sektion 7: Integritaet
    val versionInfo: String = "",
    val buildStatus: String = "",
    val testStatus: String = "",

    // Sektion 8: Offene Punkte
    val knownProblems: List<String> = emptyList(),
    val techDebt: String = "",
    val parkedItems: String = "",
    val currentFocus: String = "",

    // Sektion 9: Letzte Aenderungen
    val recentChanges: List<RecentChange> = emptyList(),

    // Metadaten
    val lastUpdatedEpochMillis: Long = System.currentTimeMillis(),
    val schemaVersion: Int = 1,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("lastUpdatedEpochMillis", lastUpdatedEpochMillis)
        // 1
        put("projectName", projectName)
        put("purpose", purpose)
        put("maturity", maturity.name)
        put("shortDescription", shortDescription)
        // 2
        put("whatWorks", whatWorks)
        put("whatsMissing", whatsMissing)
        put("currentPriority", currentPriority)
        put("nextStep", nextStep)
        // 3
        put("modules", JSONArray(modules.map { it.toJson() }))
        put("layers", layers)
        put("dataFlow", dataFlow)
        // 4
        put("primaryFiles", JSONArray(primaryFiles.map { it.toJson() }))
        put("secondaryAreas", secondaryAreas)
        // 5
        put("buildCommand", buildCommand)
        put("testCommand", testCommand)
        put("runCommand", runCommand)
        put("envVars", envVars)
        put("gotchas", gotchas)
        // 6
        put("architecturePrinciples", JSONArray(architecturePrinciples))
        put("doNotChange", doNotChange)
        put("testingPolicy", testingPolicy)
        // 7
        put("versionInfo", versionInfo)
        put("buildStatus", buildStatus)
        put("testStatus", testStatus)
        // 8
        put("knownProblems", JSONArray(knownProblems))
        put("techDebt", techDebt)
        put("parkedItems", parkedItems)
        put("currentFocus", currentFocus)
        // 9
        put("recentChanges", JSONArray(recentChanges.map { it.toJson() }))
    }

    companion object {
        fun fromJson(raw: String): AgentHandoffContext {
            if (raw.isBlank()) return AgentHandoffContext()
            val o = JSONObject(raw)
            return AgentHandoffContext(
                schemaVersion = o.optInt("schemaVersion", 1),
                lastUpdatedEpochMillis = o.optLong("lastUpdatedEpochMillis", 0L),
                projectName = o.optString("projectName"),
                purpose = o.optString("purpose"),
                maturity = ProjectMaturity.fromString(o.optString("maturity")),
                shortDescription = o.optString("shortDescription"),
                whatWorks = o.optString("whatWorks"),
                whatsMissing = o.optString("whatsMissing"),
                currentPriority = o.optString("currentPriority"),
                nextStep = o.optString("nextStep"),
                modules = o.optJSONArray("modules")?.let { arr ->
                    (0 until arr.length()).map { ModuleInfo.fromJson(arr.getJSONObject(it)) }
                } ?: emptyList(),
                layers = o.optString("layers"),
                dataFlow = o.optString("dataFlow"),
                primaryFiles = o.optJSONArray("primaryFiles")?.let { arr ->
                    (0 until arr.length()).map { EntryPoint.fromJson(arr.getJSONObject(it)) }
                } ?: emptyList(),
                secondaryAreas = o.optString("secondaryAreas"),
                buildCommand = o.optString("buildCommand"),
                testCommand = o.optString("testCommand"),
                runCommand = o.optString("runCommand"),
                envVars = o.optString("envVars"),
                gotchas = o.optString("gotchas"),
                architecturePrinciples = o.optJSONArray("architecturePrinciples")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                doNotChange = o.optString("doNotChange"),
                testingPolicy = o.optString("testingPolicy"),
                versionInfo = o.optString("versionInfo"),
                buildStatus = o.optString("buildStatus"),
                testStatus = o.optString("testStatus"),
                knownProblems = o.optJSONArray("knownProblems")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                techDebt = o.optString("techDebt"),
                parkedItems = o.optString("parkedItems"),
                currentFocus = o.optString("currentFocus"),
                recentChanges = o.optJSONArray("recentChanges")?.let { arr ->
                    (0 until arr.length()).map { RecentChange.fromJson(arr.getJSONObject(it)) }
                } ?: emptyList(),
            )
        }
    }
}
