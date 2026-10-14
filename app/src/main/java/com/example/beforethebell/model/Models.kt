package com.example.beforethebell.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Character card representation compatible with SillyTavern V1 / V2
 */
data class CharacterCard(
    val id: String,
    val name: String,
    val title: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val mesExample: String = "",
    val creatorNotes: String = "",
    val avatarBase64: String? = null,
    val sourceType: String = "custom",
    val importedAt: String = "",
    val worldFoundations: String = "",
    val rawJson: String = ""
)

/**
 * Prompt module definition
 */
data class PromptModule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val content: String,
    val placement: String = "before_history", // "before_history" | "after_history" | "in_history"
    val position: String = placement,
    val depth: Int = 0,
    val role: String = "system", // "system" | "user" | "assistant"
    val scope: String = "both", // "opening" | "continuation" | "both"
    val order: Int = 10,
    val isBuiltin: Boolean = false
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("enabled", enabled)
            put("content", content)
            put("placement", placement)
            put("position", placement)
            put("depth", depth)
            put("role", role)
            put("scope", scope)
            put("order", order)
            put("isBuiltin", isBuiltin)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject, defaultIndex: Int = 0): PromptModule {
            val placementRaw = json.optString("placement", json.optString("position", "before_history"))
            val placement = if (placementRaw == "after_history" || placementRaw == "in_history") placementRaw else "before_history"
            val defaultRole = if (placement == "after_history") "user" else "system"
            val role = json.optString("role", defaultRole)
            val depth = json.optInt("depth", 0).coerceAtLeast(0)
            val order = json.optInt("order", (defaultIndex + 1) * 10)
            val scope = json.optString("scope", "both")

            return PromptModule(
                id = json.optString("id", "mod_${System.currentTimeMillis()}_$defaultIndex"),
                name = json.optString("name", "Writing Instruction"),
                enabled = json.optBoolean("enabled", true),
                content = json.optString("content", ""),
                placement = placement,
                position = placement,
                depth = depth,
                role = role,
                scope = if (scope == "opening" || scope == "continuation") scope else "both",
                order = order,
                isBuiltin = json.optBoolean("isBuiltin", false)
            )
        }
    }
}

/**
 * Writing preset definition
 */
data class WritingPreset(
    val id: String,
    val name: String,
    val description: String = "",
    val modules: List<PromptModule> = emptyList(),
    val isDefault: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    fun toJsonObject(): JSONObject {
        val array = JSONArray()
        modules.forEach { array.put(it.toJsonObject()) }
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("description", description)
            put("modules", array)
            put("isDefault", isDefault)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): WritingPreset {
            val modulesList = mutableListOf<PromptModule>()
            val modulesArray = json.optJSONArray("modules")
            if (modulesArray != null) {
                for (i in 0 until modulesArray.length()) {
                    val modObj = modulesArray.optJSONObject(i)
                    if (modObj != null) {
                        modulesList.add(PromptModule.fromJsonObject(modObj, i))
                    }
                }
            }
            return WritingPreset(
                id = json.optString("id", "preset_${System.currentTimeMillis()}"),
                name = json.optString("name", "Custom Preset"),
                description = json.optString("description", ""),
                modules = modulesList,
                isDefault = json.optBoolean("isDefault", false),
                createdAt = json.optString("createdAt", ""),
                updatedAt = json.optString("updatedAt", "")
            )
        }
    }
}

/**
 * Objective tracking record
 */
data class ScenarioObjectiveRecord(
    val title: String,
    val detail: String,
    val successCondition: String,
    val failureCondition: String,
    val status: String = "ongoing", // "ongoing" | "success" | "failure"
    val resolutionReason: String? = null
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("title", title)
            put("detail", detail)
            put("successCondition", successCondition)
            put("failureCondition", failureCondition)
            put("status", status)
            if (resolutionReason != null) put("resolutionReason", resolutionReason)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject?): ScenarioObjectiveRecord? {
            if (json == null) return null
            return ScenarioObjectiveRecord(
                title = json.optString("title", "Survive"),
                detail = json.optString("detail", "Resolve the situation"),
                successCondition = json.optString("successCondition", ""),
                failureCondition = json.optString("failureCondition", ""),
                status = json.optString("status", "ongoing"),
                resolutionReason = if (json.has("resolutionReason") && !json.isNull("resolutionReason")) json.optString("resolutionReason") else null
            )
        }
    }
}

/**
 * Continuity fact record
 */
data class ContinuityFact(
    val id: String,
    val category: String, // "condition" | "capability" | "restraint" | "possession" | "location" | "discovery" | "commitment"
    val subject: String,
    val text: String,
    val establishedTurn: Int,
    val active: Boolean = true
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("category", category)
            put("subject", subject)
            put("text", text)
            put("establishedTurn", establishedTurn)
            put("active", active)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): ContinuityFact {
            return ContinuityFact(
                id = json.optString("id", "fact_${System.currentTimeMillis()}"),
                category = json.optString("category", "condition"),
                subject = json.optString("subject", "Player"),
                text = json.optString("text", ""),
                establishedTurn = json.optInt("establishedTurn", 0),
                active = json.optBoolean("active", true)
            )
        }
    }
}

data class ContinuityRecord(
    val facts: MutableList<ContinuityFact> = mutableListOf(),
    var lastUpdatedTurn: Int = 0
) {
    fun toJsonObject(): JSONObject {
        val factsArray = JSONArray()
        facts.forEach { factsArray.put(it.toJsonObject()) }
        return JSONObject().apply {
            put("facts", factsArray)
            put("lastUpdatedTurn", lastUpdatedTurn)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject?): ContinuityRecord {
            if (json == null) return ContinuityRecord()
            val factsList = mutableListOf<ContinuityFact>()
            val factsArray = json.optJSONArray("facts")
            if (factsArray != null) {
                for (i in 0 until factsArray.length()) {
                    val fObj = factsArray.optJSONObject(i)
                    if (fObj != null) {
                        factsList.add(ContinuityFact.fromJsonObject(fObj))
                    }
                }
            }
            return ContinuityRecord(
                facts = factsList,
                lastUpdatedTurn = json.optInt("lastUpdatedTurn", 0)
            )
        }
    }
}

data class ActionOption(
    val label: String,
    val prose: String = "",
    val verb: String = "Act",
    val timeCostMinutes: Int = 1,
    val category: String = "general"
)

data class StoryMemory(
    val summary: String = "",
    val coveredTurnNumber: Int = 0, // inclusive: turns 0..coveredTurnNumber are summarized
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("summary", summary)
        put("coveredTurnNumber", coveredTurnNumber)
        put("lastUpdatedTimestamp", lastUpdatedTimestamp)
    }

    companion object {
        fun fromJsonObject(json: JSONObject?): StoryMemory? {
            if (json == null) return null
            val sum = json.optString("summary", "").trim()
            if (sum.isBlank()) return null
            return StoryMemory(
                summary = sum,
                coveredTurnNumber = json.optInt("coveredTurnNumber", 0),
                lastUpdatedTimestamp = json.optLong("lastUpdatedTimestamp", System.currentTimeMillis())
            )
        }
    }
}

data class TurnRollbackSnapshot(
    val clockMinutes: Int,
    val formattedTime: String,
    val trust: Int,
    val disposition: Int,
    val facts: List<ContinuityFact>,
    val currentImpression: String?,
    val impressionHistory: List<String>,
    val objectiveRecord: ScenarioObjectiveRecord?,
    val notableEvents: List<String> = emptyList(),
    val storyMemorySnapshot: StoryMemory? = null
)

data class TurnLog(
    val turnNumber: Int,
    val playerInput: String,
    val storyText: String,
    val speech: String = "",
    val internalThoughts: String? = null,
    val actionSuggestions: List<ActionOption> = emptyList(),
    val speechSuggestions: List<String> = emptyList(),
    val consequenceNote: String? = null,
    val journalUpdated: Boolean = false,
    val timestamp: String = "",
    val trustDelta: Int = 0,
    val dispositionDelta: Int = 0,
    val timeSpentMinutes: Int = 1,
    val continuityUpdates: List<JSONObject> = emptyList(),
    val rollbackSnapshot: TurnRollbackSnapshot? = null,
    val playerIntent: String? = null
)

data class PlayerPersona(
    val name: String = "The Protagonist",
    val gender: String = "unspecified",
    val customGender: String? = null,
    val pronouns: String = "",
    val role: String = "Independent actor",
    val background: String = "",
    val source: String = "custom", // "card" | "custom"
    val cardId: String? = null,
    val title: String = "",
    val description: String = "",
    val personality: String = "",
    val mesExample: String = "",
    val avatarBase64: String? = null
)

data class NpcState(
    val id: String,
    val name: String,
    val title: String,
    val portraitUrl: String? = null,
    val personality: String,
    val agenda: String = "",
    val duty: String = "",
    var disposition: Int = 0, // -10 to 10
    var trust: Int = 3 // 0 to 10
)

data class GameClock(
    val startMinutes: Int = 0,
    var currentMinutes: Int = 0,
    var formattedTime: String = ""
)

data class JournalState(
    val startingPremise: String = "",
    val situation: String = "",
    val playerRole: String,
    val relationshipDynamic: String,
    val objective: String,
    var currentImpression: String = "",
    val impressionHistory: MutableList<String> = mutableListOf(),
    var showPrivateImpressions: Boolean = true,
    val notableEvents: MutableList<String> = mutableListOf()
)

data class ScenarioCockpit(
    val npcInitiative: Int = 50,
    val twists: Int = 50,
    val consequences: Int = 50,
    val dialogueActionRatio: Int = 50,
    val pacing: Int = 50,
    val mystery: Int = 50
)

/**
 * Vague, genre-agnostic scenario seeds chosen by the player.
 * Blank values mean "Let the story decide".
 */
data class ScenarioSeeds(
    val genre: String = "",
    val relationship: String = "",
    val directionalRole: String = "",
    val objective: String = "",
    val setting: String = "",
    val directions: String = ""
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("genre", genre)
        put("relationship", relationship)
        put("directionalRole", directionalRole)
        put("objective", objective)
        put("setting", setting)
        put("directions", directions)
    }

    companion object {
        fun fromJsonObject(json: JSONObject?): ScenarioSeeds? {
            if (json == null) return null
            return ScenarioSeeds(
                genre = json.optString("genre", json.optString("tone", "")),
                relationship = json.optString("relationship", json.optString("bond", "")),
                directionalRole = json.optString("directionalRole", ""),
                objective = json.optString("objective", json.optString("want", "")),
                setting = json.optString("setting", json.optString("place", "")),
                directions = json.optString("directions", json.optString("notes", ""))
            )
        }
    }
}

/**
 * Private scenario sheet authored by the AI in the opening call.
 * Never displayed directly during play.
 */
data class HiddenScenario(
    val situation: String = "",
    val npcAgenda: String = "",
    val secrets: List<String> = emptyList(),
    val teaser: String = ""
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("situation", situation)
        put("npcAgenda", npcAgenda)
        put("secrets", JSONArray().apply { secrets.forEach { put(it) } })
        put("teaser", teaser)
    }

    companion object {
        fun fromJsonObject(json: JSONObject?): HiddenScenario? {
            if (json == null) return null
            val secretsList = mutableListOf<String>()
            val arr = json.optJSONArray("secrets")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "").trim()
                    if (s.isNotBlank()) secretsList.add(s)
                }
            }
            return HiddenScenario(
                situation = json.optString("situation", json.optString("premise", "")).trim(),
                npcAgenda = json.optString("npcAgenda", "").trim(),
                secrets = secretsList,
                teaser = json.optString("teaser", "").trim()
            )
        }
    }
}

/**
 * Complete Campaign / Session State
 */
data class Campaign(
    val campaignId: String,
    var title: String,
    val inputMode: String = "free_writing", // "free_writing" | "assisted"
    val approvedPremise: String,
    val objective: String,
    val settingName: String,
    val relationshipDynamic: String,
    val cockpit: ScenarioCockpit = ScenarioCockpit(),
    val player: PlayerPersona,
    val npc: NpcState,
    var clock: GameClock = GameClock(),
    val continuity: ContinuityRecord = ContinuityRecord(),
    val journal: JournalState,
    var objectiveRecord: ScenarioObjectiveRecord,
    val activeModules: MutableList<PromptModule> = mutableListOf(),
    val turnHistory: MutableList<TurnLog> = mutableListOf(),
    var activeEnding: String? = null,
    var activeEndingText: String? = null,
    var draftInput: String = "",
    var lastUpdated: Long = System.currentTimeMillis(),
    // Seed-driven scenarios (null for legacy sessions with a fixed premise)
    val seeds: ScenarioSeeds? = null,
    var hiddenScenario: HiddenScenario? = null,
    var storyMemory: StoryMemory? = null
)

data class AppSettings(
    val provider: String = "vertex", // "vertex" | "openrouter" | "gemini" | "custom"
    val vertexApiKey: String = "",
    val vertexModelId: String = "gemini-4-argon",
    val openRouterApiKey: String = "",
    val openRouterModelId: String = "google/gemini-2.5-flash",
    val geminiApiKey: String = "",
    val geminiModelId: String = "gemini-2.5-flash",
    val customApiKey: String = "",
    val customBaseUrl: String = "https://api.openai.com/v1",
    val customModelId: String = "gpt-4o",
    val temperature: Float = 0.8f,
    val topP: Float = 0.95f,
    val maxTokens: Int = 2048,
    val contextCompressionEnabled: Boolean = true,
    val inputTokenBudget: Int = 20000
) {
    val activeModelId: String
        get() = when (provider) {
            "vertex" -> vertexModelId
            "openrouter" -> openRouterModelId
            "gemini" -> geminiModelId
            "custom" -> customModelId
            else -> vertexModelId
        }

    val activeApiKey: String
        get() = when (provider) {
            "vertex" -> vertexApiKey
            "openrouter" -> openRouterApiKey
            "gemini" -> geminiApiKey
            "custom" -> customApiKey
            else -> ""
        }

    // Compatibility getter
    val modelId: String
        get() = activeModelId

    val compressionTriggerTokens: Int
        get() = (inputTokenBudget * 0.75f).toInt()
}

