package com.example.beforethebell.data

import android.content.Context
import com.example.beforethebell.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class StorageManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("before_the_bell_prefs", Context.MODE_PRIVATE)

    private val charactersDir = File(context.filesDir, "characters").apply { if (!exists()) mkdirs() }
    private val presetsDir = File(context.filesDir, "prompt_presets").apply { if (!exists()) mkdirs() }
    private val campaignsDir = File(context.filesDir, "campaigns").apply { if (!exists()) mkdirs() }
    private val campaignsBackupDir = File(context.filesDir, "campaigns_backup").apply { if (!exists()) mkdirs() }

    init {
        seedDefaults()
        cleanupRedundantDuplicates()
    }

    private fun seedDefaults() {
        // Seed default character if none exist
        if (listCharacters().isEmpty()) {
            saveCharacter(DefaultData.DEFAULT_CHARACTER_SARAH_VANCE)
        }

        // Only seed default presets on a fresh installation
        val presetsSeeded = prefs.getBoolean("presets_seeded_v1", false)
        if (!presetsSeeded) {
            for (builtin in DefaultData.BUILTIN_PRESETS) {
                savePreset(builtin)
            }
            prefs.edit().putBoolean("presets_seeded_v1", true).apply()
        }
    }

    // --- Settings ---

    fun loadSettings(): AppSettings {
        return AppSettings(
            provider = prefs.getString("api_provider", "vertex") ?: "vertex",
            vertexApiKey = prefs.getString("vertex_api_key", "") ?: "",
            vertexModelId = prefs.getString("vertex_model_id", "gemini-4-argon") ?: "gemini-4-argon",
            openRouterApiKey = prefs.getString("openrouter_api_key", "") ?: "",
            openRouterModelId = prefs.getString("openrouter_model_id", "google/gemini-2.5-flash") ?: "google/gemini-2.5-flash",
            geminiApiKey = prefs.getString("gemini_api_key", "") ?: "",
            geminiModelId = prefs.getString("gemini_model_id", "gemini-2.5-flash") ?: "gemini-2.5-flash",
            customApiKey = prefs.getString("custom_api_key", "") ?: "",
            customBaseUrl = prefs.getString("custom_base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
            customModelId = prefs.getString("custom_model_id", "gpt-4o") ?: "gpt-4o",
            temperature = prefs.getFloat("temperature", 0.8f),
            topP = prefs.getFloat("top_p", 0.95f),
            maxTokens = prefs.getInt("max_tokens", 2048),
            contextCompressionEnabled = prefs.getBoolean("context_compression_enabled", true),
            inputTokenBudget = prefs.getInt("input_token_budget", 20000)
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().apply {
            putString("api_provider", settings.provider)
            putString("vertex_api_key", settings.vertexApiKey.trim())
            putString("vertex_model_id", settings.vertexModelId.trim())
            putString("openrouter_api_key", settings.openRouterApiKey.trim())
            putString("openrouter_model_id", settings.openRouterModelId.trim())
            putString("gemini_api_key", settings.geminiApiKey.trim())
            putString("gemini_model_id", settings.geminiModelId.trim())
            putString("custom_api_key", settings.customApiKey.trim())
            putString("custom_base_url", settings.customBaseUrl.trim())
            putString("custom_model_id", settings.customModelId.trim())
            putFloat("temperature", settings.temperature)
            putFloat("top_p", settings.topP)
            putInt("max_tokens", settings.maxTokens)
            putBoolean("context_compression_enabled", settings.contextCompressionEnabled)
            putInt("input_token_budget", settings.inputTokenBudget)
            apply()
        }
    }

    // --- Characters ---

    fun listCharacters(): List<CharacterCard> {
        val files = charactersDir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        val list = mutableListOf<CharacterCard>()
        for (f in files) {
            try {
                val json = JSONObject(f.readText())
                list.add(
                    CharacterCard(
                        id = json.optString("id", f.nameWithoutExtension),
                        name = json.optString("name", "Unknown"),
                        title = json.optString("title", ""),
                        description = json.optString("description", ""),
                        personality = json.optString("personality", ""),
                        scenario = json.optString("scenario", ""),
                        firstMessage = json.optString("firstMessage", ""),
                        mesExample = json.optString("mesExample", ""),
                        creatorNotes = json.optString("creatorNotes", ""),
                        avatarBase64 = if (json.has("avatarBase64") && !json.isNull("avatarBase64")) json.optString("avatarBase64") else null,
                        sourceType = json.optString("sourceType", "custom"),
                        importedAt = json.optString("importedAt", ""),
                        worldFoundations = json.optString("worldFoundations", "")
                    )
                )
            } catch (e: Exception) {
                // skip corrupted
            }
        }
        return list.sortedBy { it.name }
    }

    fun getCharacter(id: String): CharacterCard? {
        val file = File(charactersDir, "$id.json")
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            CharacterCard(
                id = json.optString("id", id),
                name = json.optString("name", "Unknown"),
                title = json.optString("title", ""),
                description = json.optString("description", ""),
                personality = json.optString("personality", ""),
                scenario = json.optString("scenario", ""),
                firstMessage = json.optString("firstMessage", ""),
                mesExample = json.optString("mesExample", ""),
                creatorNotes = json.optString("creatorNotes", ""),
                avatarBase64 = if (json.has("avatarBase64") && !json.isNull("avatarBase64")) json.optString("avatarBase64") else null,
                sourceType = json.optString("sourceType", "custom"),
                importedAt = json.optString("importedAt", ""),
                worldFoundations = json.optString("worldFoundations", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveCharacter(card: CharacterCard) {
        val json = JSONObject().apply {
            put("id", card.id)
            put("name", card.name)
            put("title", card.title)
            put("description", card.description)
            put("personality", card.personality)
            put("scenario", card.scenario)
            put("firstMessage", card.firstMessage)
            put("mesExample", card.mesExample)
            put("creatorNotes", card.creatorNotes)
            put("avatarBase64", card.avatarBase64 ?: JSONObject.NULL)
            put("sourceType", card.sourceType)
            put("importedAt", card.importedAt)
            put("worldFoundations", card.worldFoundations)
        }
        val file = File(charactersDir, "${card.id}.json")
        file.writeText(json.toString(2))
    }

    fun deleteCharacter(id: String): Boolean {
        val file = File(charactersDir, "$id.json")
        return file.delete()
    }

    fun getActivePresetId(): String {
        return prefs.getString("active_preset_id", "preset_before_the_bell") ?: "preset_before_the_bell"
    }

    fun setActivePresetId(id: String) {
        prefs.edit().putString("active_preset_id", id).apply()
    }

    fun getActivePreset(): WritingPreset? {
        val activeId = getActivePresetId()
        if (activeId.isNotBlank()) {
            val preset = getPreset(activeId)
            if (preset != null) return preset
        }
        return listPresets().firstOrNull { it.isDefault } ?: listPresets().firstOrNull()
    }

    fun listPresets(): List<WritingPreset> {
        val files = presetsDir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        val list = mutableListOf<WritingPreset>()
        for (f in files) {
            try {
                val json = JSONObject(f.readText())
                list.add(WritingPreset.fromJsonObject(json))
            } catch (e: Exception) {
                // skip
            }
        }
        return list.sortedWith(compareBy({ !it.isDefault }, { it.name }))
    }

    fun getPreset(id: String): WritingPreset? {
        val file = File(presetsDir, "$id.json")
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            WritingPreset.fromJsonObject(json)
        } catch (e: Exception) {
            null
        }
    }

    fun savePreset(preset: WritingPreset) {
        val file = File(presetsDir, "${preset.id}.json")
        file.writeText(preset.toJsonObject().toString(2))
    }

    fun deletePreset(id: String): Boolean {
        val file = File(presetsDir, "$id.json")
        val deleted = file.delete()
        if (deleted && getActivePresetId() == id) {
            val nextPreset = listPresets().firstOrNull()
            setActivePresetId(nextPreset?.id ?: "")
        }
        return deleted
    }

    // --- Campaigns ---

    fun listCampaigns(): List<Campaign> {
        val files = campaignsDir.listFiles { f ->
            f.extension == "json" && !f.name.endsWith(".bak.json") && !f.name.endsWith(".bak")
        } ?: return emptyList()

        val map = mutableMapOf<String, Campaign>()
        for (f in files) {
            try {
                val json = JSONObject(f.readText())
                val camp = loadCampaignFromJson(json)
                if (camp != null) {
                    val existing = map[camp.campaignId]
                    if (existing == null || camp.turnHistory.size > existing.turnHistory.size || camp.lastUpdated > existing.lastUpdated) {
                        map[camp.campaignId] = camp
                    }
                }
            } catch (e: Exception) {
                // skip
            }
        }
        return map.values.sortedByDescending { it.lastUpdated }
    }

    fun getCampaign(id: String): Campaign? {
        val file = File(campaignsDir, "$id.json")
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            loadCampaignFromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    fun getLatestCampaign(): Campaign? {
        return listCampaigns().firstOrNull()
    }

    fun saveCampaign(campaign: Campaign) {
        campaign.lastUpdated = System.currentTimeMillis()
        val json = saveCampaignToJson(campaign)
        val file = File(campaignsDir, "${campaign.campaignId}.json")
        if (file.exists()) {
            val backupFile = File(campaignsBackupDir, "${campaign.campaignId}.bak.json")
            try {
                file.copyTo(backupFile, overwrite = true)
            } catch (_: Exception) {}
        }
        file.writeText(json.toString(2))
    }

    fun deleteCampaign(id: String): Boolean {
        val file = File(campaignsDir, "$id.json")
        val legacyBak = File(campaignsDir, "$id.bak.json")
        if (legacyBak.exists()) legacyBak.delete()
        val legacyBak2 = File(campaignsDir, "$id.bak")
        if (legacyBak2.exists()) legacyBak2.delete()
        val backupFile = File(campaignsBackupDir, "$id.bak.json")
        if (backupFile.exists()) backupFile.delete()
        return file.delete()
    }

    fun cleanupRedundantDuplicates() {
        try {
            // 1. Process any legacy .bak.json or .bak files in campaignsDir
            val legacyBakFiles = campaignsDir.listFiles { f ->
                f.name.endsWith(".bak.json") || f.name.endsWith(".bak")
            } ?: emptyArray()

            for (bakFile in legacyBakFiles) {
                try {
                    val bakJson = JSONObject(bakFile.readText())
                    val bakCamp = loadCampaignFromJson(bakJson)
                    if (bakCamp != null) {
                        val mainFile = File(campaignsDir, "${bakCamp.campaignId}.json")
                        if (mainFile.exists()) {
                            val mainCamp = try {
                                loadCampaignFromJson(JSONObject(mainFile.readText()))
                            } catch (_: Exception) { null }

                            if (mainCamp != null && mainCamp.turnHistory.size >= bakCamp.turnHistory.size) {
                                // bakFile is demonstrably redundant. Preserve recoverable copy in backupDir.
                                val safeBackup = File(campaignsBackupDir, bakFile.name)
                                bakFile.copyTo(safeBackup, overwrite = true)
                                bakFile.delete()
                            } else if (mainCamp != null && bakCamp.turnHistory.size > mainCamp.turnHistory.size) {
                                // bakFile has unique progress! Promote bakFile to main, archive old main
                                val oldMainBackup = File(campaignsBackupDir, "${mainCamp.campaignId}_prev.bak.json")
                                mainFile.copyTo(oldMainBackup, overwrite = true)
                                bakFile.copyTo(mainFile, overwrite = true)
                                bakFile.delete()
                            } else {
                                val safeBackup = File(campaignsBackupDir, bakFile.name)
                                bakFile.copyTo(safeBackup, overwrite = true)
                                bakFile.delete()
                            }
                        } else {
                            // Main file missing: restore this backup as main!
                            bakFile.copyTo(mainFile, overwrite = false)
                            val safeBackup = File(campaignsBackupDir, bakFile.name)
                            bakFile.copyTo(safeBackup, overwrite = true)
                            bakFile.delete()
                        }
                    } else {
                        val safeBackup = File(campaignsBackupDir, bakFile.name)
                        bakFile.copyTo(safeBackup, overwrite = true)
                        bakFile.delete()
                    }
                } catch (_: Exception) {
                    val safeBackup = File(campaignsBackupDir, bakFile.name)
                    try { bakFile.copyTo(safeBackup, overwrite = true) } catch (_: Exception) {}
                    bakFile.delete()
                }
            }

            // 2. Identify and resolve demonstrably redundant campaign sessions created by rapid repeated taps or UI recreation
            val validFiles = campaignsDir.listFiles { f ->
                f.extension == "json" && !f.name.endsWith(".bak.json") && !f.name.endsWith(".bak")
            } ?: emptyArray()

            val loadedCampaigns = mutableListOf<Pair<File, Campaign>>()
            for (f in validFiles) {
                try {
                    val c = loadCampaignFromJson(JSONObject(f.readText()))
                    if (c != null) {
                        loadedCampaigns.add(f to c)
                    }
                } catch (_: Exception) {}
            }

            // Group campaigns by NPC ID and Player Name to find close-in-time duplicates
            val grouped = loadedCampaigns.groupBy { (_, camp) ->
                "${camp.npc.id}_${camp.player.name}"
            }

            for ((_, list) in grouped) {
                if (list.size > 1) {
                    val sorted = list.sortedBy { it.second.lastUpdated }
                    for (i in 0 until sorted.size - 1) {
                        val (fileA, campA) = sorted[i]
                        val (fileB, campB) = sorted[i + 1]

                        val timeDiff = kotlin.math.abs(campB.lastUpdated - campA.lastUpdated)
                        val idDiff = try {
                            val idA = campA.campaignId.removePrefix("camp_").takeWhile { it.isDigit() }.toLong()
                            val idB = campB.campaignId.removePrefix("camp_").takeWhile { it.isDigit() }.toLong()
                            kotlin.math.abs(idB - idA)
                        } catch (_: Exception) { Long.MAX_VALUE }

                        if (timeDiff <= 60_000 || idDiff <= 60_000) {
                            if (campA.turnHistory.isEmpty() && campB.turnHistory.isNotEmpty()) {
                                val backup = File(campaignsBackupDir, fileA.name)
                                fileA.copyTo(backup, overwrite = true)
                                fileA.delete()
                            } else if (campB.turnHistory.isEmpty() && campA.turnHistory.isNotEmpty()) {
                                val backup = File(campaignsBackupDir, fileB.name)
                                fileB.copyTo(backup, overwrite = true)
                                fileB.delete()
                            } else if (campA.turnHistory.isEmpty() && campB.turnHistory.isEmpty()) {
                                val backup = File(campaignsBackupDir, fileA.name)
                                fileA.copyTo(backup, overwrite = true)
                                fileA.delete()
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Best effort cleanup
        }
    }

    fun exportTranscript(campaign: Campaign): String {
        val sb = StringBuilder()
        sb.append("=== BEFORE THE BELL ===\n")
        sb.append("Title: ${campaign.title}\n")
        sb.append("Character: ${campaign.npc.name} (${campaign.npc.title})\n")
        sb.append("Player: ${campaign.player.name} (${campaign.player.role})\n")
        val objDisplay = if (com.example.beforethebell.engine.ObjectiveManager.isOpenEnded(campaign)) "None — Open-ended" else campaign.objective
        sb.append("Objective: $objDisplay\n")
        sb.append("Setting: ${campaign.settingName}\n")
        sb.append("Clock: ${campaign.clock.formattedTime}\n\n")

        sb.append("--- PREMISE ---\n")
        sb.append(campaign.approvedPremise).append("\n\n")

        sb.append("--- TRANSCRIPT ---\n\n")
        for (turn in campaign.turnHistory) {
            if (turn.turnNumber == 0) {
                sb.append("[OPENING SCENE]\n")
                sb.append(turn.storyText).append("\n\n")
            } else {
                sb.append("[TURN ${turn.turnNumber}: ${campaign.player.name}]\n")
                sb.append(turn.playerInput).append("\n\n")
                sb.append("[RESPONSE: ${campaign.npc.name}]\n")
                sb.append(turn.storyText).append("\n")
                if (!turn.consequenceNote.isNullOrBlank()) {
                    sb.append("Consequence: ${turn.consequenceNote}\n")
                }
                sb.append("\n")
            }
        }

        if (campaign.activeEnding != null) {
            sb.append("[RESOLUTION: ${campaign.activeEnding}]\n")
            sb.append(campaign.activeEndingText ?: "").append("\n")
        }

        return sb.toString()
    }

    fun exportCampaignJson(campaign: Campaign): String {
        return saveCampaignToJson(campaign).toString(2)
    }

    fun exportCampaignJsonFile(campaign: Campaign): File {
        val exportsDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val sanitizedTitle = campaign.title.replace(Regex("""[^\w\-]+"""), "_").take(30).ifBlank { "session" }
        val fileName = "${sanitizedTitle}_${campaign.campaignId}.json"
        val file = File(exportsDir, fileName)
        file.writeText(exportCampaignJson(campaign))
        return file
    }

    fun exportPresetJsonFile(preset: WritingPreset): File {
        val exportsDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val sanitizedTitle = preset.name.replace(Regex("""[^\w\-]+"""), "_").take(30).ifBlank { "preset" }
        val fileName = "preset_${sanitizedTitle}.json"
        val file = File(exportsDir, fileName)
        file.writeText(preset.toJsonObject().toString(2))
        return file
    }

    fun importCampaignFromJson(jsonString: String): Campaign? {
        val json = JSONObject(jsonString)
        val original = loadCampaignFromJson(json) ?: return null
        // Generate new ID for imported session if already existing or to keep isolated
        val newId = "camp_${System.currentTimeMillis()}_${(1000..9999).random()}"
        val imported = original.copy(
            campaignId = newId,
            lastUpdated = System.currentTimeMillis()
        )
        saveCampaign(imported)
        return imported
    }

    /**
     * Creates an independent branch of an existing campaign at a specific completed turn.
     * Preserves state exactly up to that point without leaking later state.
     * Returns Result with the new Campaign, or error if snapshot is missing.
     */
    fun branchCampaignAtTurn(source: Campaign, turnIndex: Int): Result<Campaign> {
        if (turnIndex !in source.turnHistory.indices) {
            return Result.failure(IllegalArgumentException("Invalid turn index for branching."))
        }

        val targetTurn = source.turnHistory[turnIndex]

        // Sublist of turns up to target turn (inclusive)
        val branchedTurns = mutableListOf<TurnLog>()
        for (i in 0..turnIndex) {
            branchedTurns.add(source.turnHistory[i])
        }

        // Determine the restored state at the end of targetTurn
        val restoredClock: GameClock
        val restoredTrust: Int
        val restoredDisp: Int
        val restoredFacts: MutableList<ContinuityFact>
        val restoredImp: String
        val restoredImpsList: MutableList<String>
        val restoredNotable: MutableList<String>
        val restoredObj: ScenarioObjectiveRecord

        if (turnIndex < source.turnHistory.size - 1) {
            // Next turn has the rollbackSnapshot recording the exact state right after targetTurn
            val nextTurn = source.turnHistory[turnIndex + 1]
            val snapshot = nextTurn.rollbackSnapshot
            if (snapshot == null) {
                return Result.failure(IllegalStateException("This older turn lacks saved snapshot data needed to branch accurately without leaking later state."))
            }
            restoredClock = GameClock(
                startMinutes = snapshot.clockMinutes,
                currentMinutes = snapshot.clockMinutes,
                formattedTime = snapshot.formattedTime
            )
            restoredTrust = snapshot.trust
            restoredDisp = snapshot.disposition
            restoredFacts = snapshot.facts.map { it.copy() }.toMutableList()
            restoredImp = snapshot.currentImpression ?: ""
            restoredImpsList = snapshot.impressionHistory.toMutableList()
            restoredNotable = snapshot.notableEvents.toMutableList()
            restoredObj = snapshot.objectiveRecord?.copy() ?: source.objectiveRecord.copy(status = "ongoing", resolutionReason = null)
        } else {
            // Target turn is the latest turn of the campaign
            restoredClock = source.clock.copy()
            restoredTrust = source.npc.trust
            restoredDisp = source.npc.disposition
            restoredFacts = source.continuity.facts.map { it.copy() }.toMutableList()
            restoredImp = source.journal.currentImpression
            restoredImpsList = source.journal.impressionHistory.toMutableList()
            restoredNotable = source.journal.notableEvents.toMutableList()
            restoredObj = source.objectiveRecord.copy()
        }

        val restoredStoryMemory: StoryMemory? = if (turnIndex < source.turnHistory.size - 1) {
            val nextTurn = source.turnHistory[turnIndex + 1]
            val snapMemory = nextTurn.rollbackSnapshot?.storyMemorySnapshot
            if (snapMemory != null && snapMemory.coveredTurnNumber < turnIndex) {
                snapMemory.copy()
            } else if (source.storyMemory != null && source.storyMemory!!.coveredTurnNumber < turnIndex) {
                source.storyMemory!!.copy()
            } else {
                null
            }
        } else {
            source.storyMemory?.copy()
        }

        val newCampaignId = "camp_${System.currentTimeMillis()}_${(1000..9999).random()}"
        val branchTitle = "${source.title} (Branch T$turnIndex)"

        val branchedCampaign = source.copy(
            campaignId = newCampaignId,
            title = branchTitle,
            clock = restoredClock,
            npc = source.npc.copy(trust = restoredTrust, disposition = restoredDisp),
            continuity = ContinuityRecord(facts = restoredFacts, lastUpdatedTurn = turnIndex),
            journal = source.journal.copy(
                currentImpression = restoredImp,
                impressionHistory = restoredImpsList,
                notableEvents = restoredNotable
            ),
            objectiveRecord = restoredObj,
            turnHistory = branchedTurns,
            storyMemory = restoredStoryMemory,
            activeEnding = if (turnIndex == source.turnHistory.size - 1) source.activeEnding else null,
            activeEndingText = if (turnIndex == source.turnHistory.size - 1) source.activeEndingText else null,
            draftInput = "",
            lastUpdated = System.currentTimeMillis()
        )

        saveCampaign(branchedCampaign)
        return Result.success(branchedCampaign)
    }

    fun saveCampaignToJson(camp: Campaign): JSONObject {
        val json = JSONObject()
        json.put("campaignId", camp.campaignId)
        json.put("title", camp.title)
        json.put("inputMode", camp.inputMode)
        json.put("approvedPremise", camp.approvedPremise)
        json.put("objective", camp.objective)
        json.put("settingName", camp.settingName)
        json.put("relationshipDynamic", camp.relationshipDynamic)
        json.put("activeEnding", camp.activeEnding ?: JSONObject.NULL)
        json.put("activeEndingText", camp.activeEndingText ?: JSONObject.NULL)
        json.put("draftInput", camp.draftInput)
        json.put("lastUpdated", camp.lastUpdated)

        // Cockpit
        val cockpitObj = JSONObject().apply {
            put("npcInitiative", camp.cockpit.npcInitiative)
            put("twists", camp.cockpit.twists)
            put("consequences", camp.cockpit.consequences)
            put("dialogueActionRatio", camp.cockpit.dialogueActionRatio)
            put("pacing", camp.cockpit.pacing)
            put("mystery", camp.cockpit.mystery)
        }
        json.put("cockpit", cockpitObj)

        // Player
        val playerObj = JSONObject().apply {
            put("name", camp.player.name)
            put("gender", camp.player.gender)
            put("customGender", camp.player.customGender ?: JSONObject.NULL)
            put("pronouns", camp.player.pronouns)
            put("role", camp.player.role)
            put("background", camp.player.background)
            put("source", camp.player.source)
            put("cardId", camp.player.cardId ?: JSONObject.NULL)
            put("title", camp.player.title)
            put("description", camp.player.description)
            put("personality", camp.player.personality)
            put("mesExample", camp.player.mesExample)
            put("avatarBase64", camp.player.avatarBase64 ?: JSONObject.NULL)
        }
        json.put("player", playerObj)

        // NPC
        val npcObj = JSONObject().apply {
            put("id", camp.npc.id)
            put("name", camp.npc.name)
            put("title", camp.npc.title)
            put("portraitUrl", camp.npc.portraitUrl ?: JSONObject.NULL)
            put("personality", camp.npc.personality)
            put("agenda", camp.npc.agenda)
            put("duty", camp.npc.duty)
            put("disposition", camp.npc.disposition)
            put("trust", camp.npc.trust)
        }
        json.put("npc", npcObj)

        // Clock
        val clockObj = JSONObject().apply {
            put("startMinutes", camp.clock.startMinutes)
            put("currentMinutes", camp.clock.currentMinutes)
            put("formattedTime", camp.clock.formattedTime)
        }
        json.put("clock", clockObj)

        // Continuity
        json.put("continuity", camp.continuity.toJsonObject())

        // Journal
        val journalObj = JSONObject().apply {
            put("startingPremise", camp.journal.startingPremise)
            put("situation", camp.journal.situation)
            put("playerRole", camp.journal.playerRole)
            put("relationshipDynamic", camp.journal.relationshipDynamic)
            put("objective", camp.journal.objective)
            put("currentImpression", camp.journal.currentImpression)
            val impArray = JSONArray()
            camp.journal.impressionHistory.forEach { impArray.put(it) }
            put("impressionHistory", impArray)
            val notableEventsArr = JSONArray()
            camp.journal.notableEvents.forEach { notableEventsArr.put(it) }
            put("notableEvents", notableEventsArr)
            put("showPrivateImpressions", camp.journal.showPrivateImpressions)
        }
        json.put("journal", journalObj)

        // Objective Record
        json.put("objectiveRecord", camp.objectiveRecord.toJsonObject())

        // Scenario seeds & hidden AI-authored scenario sheet
        camp.seeds?.let { json.put("seeds", it.toJsonObject()) }
        camp.hiddenScenario?.let { json.put("hiddenScenario", it.toJsonObject()) }
        camp.storyMemory?.let { json.put("storyMemory", it.toJsonObject()) }

        // Active modules
        val modulesArr = JSONArray()
        camp.activeModules.forEach { modulesArr.put(it.toJsonObject()) }
        json.put("activeModules", modulesArr)

        // Turn history
        val turnsArr = JSONArray()
        for (turn in camp.turnHistory) {
            val tObj = JSONObject().apply {
                put("turnNumber", turn.turnNumber)
                put("playerInput", turn.playerInput)
                put("storyText", turn.storyText)
                put("speech", turn.speech)
                put("internalThoughts", turn.internalThoughts ?: JSONObject.NULL)
                put("consequenceNote", turn.consequenceNote ?: JSONObject.NULL)
                if (turn.journalUpdated) put("journalUpdated", true)
                put("timestamp", turn.timestamp)
                put("trustDelta", turn.trustDelta)
                put("dispositionDelta", turn.dispositionDelta)
                put("timeSpentMinutes", turn.timeSpentMinutes)

                if (!turn.playerIntent.isNullOrBlank()) put("playerIntent", turn.playerIntent)

                val suggestionsArr = JSONArray()
                for (s in turn.actionSuggestions) {
                    suggestionsArr.put(JSONObject().apply {
                        put("label", s.label)
                        put("prose", s.prose)
                        put("verb", s.verb)
                        put("timeCostMinutes", s.timeCostMinutes)
                        put("category", s.category)
                    })
                }
                put("actionSuggestions", suggestionsArr)

                val speechArr = JSONArray()
                for (ss in turn.speechSuggestions) {
                    speechArr.put(ss)
                }
                put("speechSuggestions", speechArr)

                val cuArr = JSONArray()
                for (cu in turn.continuityUpdates) {
                    cuArr.put(cu)
                }
                put("continuityUpdates", cuArr)

                if (turn.rollbackSnapshot != null) {
                    val snapObj = JSONObject().apply {
                        put("clockMinutes", turn.rollbackSnapshot.clockMinutes)
                        put("formattedTime", turn.rollbackSnapshot.formattedTime)
                        put("trust", turn.rollbackSnapshot.trust)
                        put("disposition", turn.rollbackSnapshot.disposition)
                        val fArr = JSONArray()
                        turn.rollbackSnapshot.facts.forEach { fArr.put(it.toJsonObject()) }
                        put("facts", fArr)
                        put("currentImpression", turn.rollbackSnapshot.currentImpression ?: JSONObject.NULL)
                        val imps = JSONArray()
                        turn.rollbackSnapshot.impressionHistory.forEach { imps.put(it) }
                        put("impressionHistory", imps)
                        val evs = JSONArray()
                        turn.rollbackSnapshot.notableEvents.forEach { evs.put(it) }
                        put("notableEvents", evs)
                        if (turn.rollbackSnapshot.objectiveRecord != null) {
                            put("objectiveRecord", turn.rollbackSnapshot.objectiveRecord.toJsonObject())
                        }
                        turn.rollbackSnapshot.storyMemorySnapshot?.let {
                            put("storyMemorySnapshot", it.toJsonObject())
                        }
                    }
                    put("rollbackSnapshot", snapObj)
                }
            }
            turnsArr.put(tObj)
        }
        json.put("turnHistory", turnsArr)

        return json
    }

    fun loadCampaignFromJson(json: JSONObject): Campaign? {
        val campaignId = json.optString("campaignId", "")
        if (campaignId.isBlank()) return null

        val cockpitObj = json.optJSONObject("cockpit")
        val cockpit = ScenarioCockpit(
            npcInitiative = cockpitObj?.optInt("npcInitiative", 50) ?: 50,
            twists = cockpitObj?.optInt("twists", 50) ?: 50,
            consequences = cockpitObj?.optInt("consequences", 50) ?: 50,
            dialogueActionRatio = cockpitObj?.optInt("dialogueActionRatio", 50) ?: 50,
            pacing = cockpitObj?.optInt("pacing", 50) ?: 50,
            mystery = cockpitObj?.optInt("mystery", 50) ?: 50
        )

        val playerObj = json.optJSONObject("player")
        val player = PlayerPersona(
            name = playerObj?.optString("name", "The Protagonist") ?: "The Protagonist",
            gender = playerObj?.optString("gender", "unspecified") ?: "unspecified",
            customGender = if (playerObj != null && playerObj.has("customGender") && !playerObj.isNull("customGender")) playerObj.optString("customGender") else null,
            pronouns = playerObj?.optString("pronouns", "") ?: "",
            role = playerObj?.optString("role", "Independent actor") ?: "Independent actor",
            background = playerObj?.optString("background", "") ?: "",
            source = playerObj?.optString("source", "custom") ?: "custom",
            cardId = if (playerObj != null && playerObj.has("cardId") && !playerObj.isNull("cardId")) playerObj.optString("cardId") else null,
            title = playerObj?.optString("title", "") ?: "",
            description = playerObj?.optString("description", "") ?: "",
            personality = playerObj?.optString("personality", "") ?: "",
            mesExample = playerObj?.optString("mesExample", "") ?: "",
            avatarBase64 = if (playerObj != null && playerObj.has("avatarBase64") && !playerObj.isNull("avatarBase64")) playerObj.optString("avatarBase64") else null
        )

        val npcObj = json.optJSONObject("npc")
        val npc = NpcState(
            id = npcObj?.optString("id", "custom_npc") ?: "custom_npc",
            name = npcObj?.optString("name", "Opponent") ?: "Opponent",
            title = npcObj?.optString("title", "Adjudicator") ?: "Adjudicator",
            portraitUrl = if (npcObj != null && npcObj.has("portraitUrl") && !npcObj.isNull("portraitUrl")) npcObj.optString("portraitUrl") else null,
            personality = npcObj?.optString("personality", "Observant and strict") ?: "Observant and strict",
            agenda = npcObj?.optString("agenda", "") ?: "",
            duty = npcObj?.optString("duty", "") ?: "",
            disposition = npcObj?.optInt("disposition", 0) ?: 0,
            trust = npcObj?.optInt("trust", 3) ?: 3
        )

        val clockObj = json.optJSONObject("clock")
        val clock = GameClock(
            startMinutes = clockObj?.optInt("startMinutes", 0) ?: 0,
            currentMinutes = clockObj?.optInt("currentMinutes", 0) ?: 0,
            formattedTime = clockObj?.optString("formattedTime", "") ?: ""
        )

        val continuity = ContinuityRecord.fromJsonObject(json.optJSONObject("continuity"))

        val journalObj = json.optJSONObject("journal")
        val impsList = mutableListOf<String>()
        val impsArr = journalObj?.optJSONArray("impressionHistory")
        if (impsArr != null) {
            for (i in 0 until impsArr.length()) {
                impsList.add(impsArr.optString(i))
            }
        }
        val notableEventsList = mutableListOf<String>()
        val evsArr = journalObj?.optJSONArray("notableEvents")
        if (evsArr != null) {
            for (i in 0 until evsArr.length()) {
                val ev = evsArr.optString(i, "").trim()
                if (ev.isNotBlank()) notableEventsList.add(ev)
            }
        }
        val journal = JournalState(
            startingPremise = journalObj?.optString("startingPremise", "") ?: "",
            situation = journalObj?.optString("situation", "") ?: "", 
            playerRole = journalObj?.optString("playerRole", player.role) ?: player.role,
            relationshipDynamic = journalObj?.optString("relationshipDynamic", "Strangers") ?: "Strangers",
            objective = journalObj?.optString("objective", "Survive") ?: "Survive",
            currentImpression = journalObj?.optString("currentImpression", "") ?: "",
            impressionHistory = impsList,
            showPrivateImpressions = journalObj?.optBoolean("showPrivateImpressions", true) ?: true,
            notableEvents = notableEventsList
        )

        val objTitle = json.optString("objective", "Survive")
        val objRecord = ScenarioObjectiveRecord.fromJsonObject(json.optJSONObject("objectiveRecord"))
            ?: if (com.example.beforethebell.engine.ObjectiveManager.isOpenEnded(objTitle)) {
                com.example.beforethebell.engine.ObjectiveManager.createOpenEndedObjectiveRecord()
            } else {
                ScenarioObjectiveRecord(
                    title = objTitle,
                    detail = json.optString("approvedPremise", "Resolve the immediate crisis"),
                    successCondition = "De-escalate the mortal threat or reach safety.",
                    failureCondition = "Sustain fatal physical trauma or permanent incapacitation."
                )
            }

        val activeModules = mutableListOf<PromptModule>()
        val modsArr = json.optJSONArray("activeModules")
        if (modsArr != null) {
            for (i in 0 until modsArr.length()) {
                val mObj = modsArr.optJSONObject(i)
                if (mObj != null) {
                    activeModules.add(PromptModule.fromJsonObject(mObj, i))
                }
            }
        } else {
            activeModules.addAll(DefaultData.PRESET_BEFORE_THE_BELL.modules)
        }

        val turnHistory = mutableListOf<TurnLog>()
        val turnsArr = json.optJSONArray("turnHistory")
        if (turnsArr != null) {
            for (i in 0 until turnsArr.length()) {
                val tObj = turnsArr.optJSONObject(i) ?: continue

                val suggestions = mutableListOf<ActionOption>()
                val sArr = tObj.optJSONArray("actionSuggestions")
                if (sArr != null) {
                    for (j in 0 until sArr.length()) {
                        val sObj = sArr.optJSONObject(j) ?: continue
                        suggestions.add(
                            ActionOption(
                                label = sObj.optString("label", "Act"),
                                prose = sObj.optString("prose", sObj.optString("narrative", "")),
                                verb = sObj.optString("verb", "Act"),
                                timeCostMinutes = sObj.optInt("timeCostMinutes", 1),
                                category = sObj.optString("category", "general")
                            )
                        )
                    }
                }

                val speechList = mutableListOf<String>()
                val spArr = tObj.optJSONArray("speechSuggestions")
                if (spArr != null) {
                    for (j in 0 until spArr.length()) {
                        val s = spArr.optString(j, "").trim()
                        if (s.isNotBlank()) speechList.add(s)
                    }
                }

                val cuList = mutableListOf<JSONObject>()
                val cuArr = tObj.optJSONArray("continuityUpdates")
                if (cuArr != null) {
                    for (j in 0 until cuArr.length()) {
                        cuArr.optJSONObject(j)?.let { cuList.add(it) }
                    }
                }

                val snapObj = tObj.optJSONObject("rollbackSnapshot")
                val rollbackSnap = if (snapObj != null) {
                    val factsSnap = mutableListOf<ContinuityFact>()
                    val fArr = snapObj.optJSONArray("facts")
                    if (fArr != null) {
                        for (k in 0 until fArr.length()) {
                            fArr.optJSONObject(k)?.let { factsSnap.add(ContinuityFact.fromJsonObject(it)) }
                        }
                    } else {
                        // Explicit legacy snapshot migration from the snapshot's own recorded inventory (if present)
                        val legacyPlayer = snapObj.optJSONObject("player")
                        val invArr = legacyPlayer?.optJSONArray("inventory")
                        if (invArr != null) {
                            for (k in 0 until invArr.length()) {
                                val item = invArr.optString(k, "").trim()
                                if (item.isNotBlank()) {
                                    factsSnap.add(
                                        ContinuityFact(
                                            id = "legacy_snap_inv_$k",
                                            category = "possession",
                                            subject = "player",
                                            text = item,
                                            establishedTurn = 0,
                                            active = true
                                        )
                                    )
                                }
                            }
                        }
                    }
                    val impsSnap = mutableListOf<String>()
                    val impsSnapArr = snapObj.optJSONArray("impressionHistory")
                    if (impsSnapArr != null) {
                        for (k in 0 until impsSnapArr.length()) {
                            impsSnap.add(impsSnapArr.optString(k))
                        }
                    }
                    val evsSnap = mutableListOf<String>()
                    val evsSnapArr = snapObj.optJSONArray("notableEvents")
                    if (evsSnapArr != null) {
                        for (k in 0 until evsSnapArr.length()) {
                            val ev = evsSnapArr.optString(k, "").trim()
                            if (ev.isNotBlank()) evsSnap.add(ev)
                        }
                    }
                    TurnRollbackSnapshot(
                        clockMinutes = snapObj.optInt("clockMinutes", 0),
                        formattedTime = snapObj.optString("formattedTime", ""),
                        trust = snapObj.optInt("trust", 3),
                        disposition = snapObj.optInt("disposition", 0),
                        facts = factsSnap,
                        currentImpression = if (snapObj.has("currentImpression") && !snapObj.isNull("currentImpression")) snapObj.optString("currentImpression") else null,
                        impressionHistory = impsSnap,
                        objectiveRecord = ScenarioObjectiveRecord.fromJsonObject(snapObj.optJSONObject("objectiveRecord")),
                        notableEvents = evsSnap,
                        storyMemorySnapshot = StoryMemory.fromJsonObject(snapObj.optJSONObject("storyMemorySnapshot"))
                    )
                } else null

                turnHistory.add(
                    TurnLog(
                        turnNumber = tObj.optInt("turnNumber", i),
                        playerInput = tObj.optString("playerInput", ""),
                        playerIntent = if (tObj.has("playerIntent") && !tObj.isNull("playerIntent")) tObj.optString("playerIntent").ifBlank { null } else null,
                        storyText = tObj.optString("storyText", ""),
                        speech = tObj.optString("speech", ""),
                        internalThoughts = if (tObj.has("internalThoughts") && !tObj.isNull("internalThoughts")) tObj.optString("internalThoughts") else null,
                        actionSuggestions = suggestions,
                        speechSuggestions = speechList,
                        consequenceNote = if (tObj.has("consequenceNote") && !tObj.isNull("consequenceNote")) tObj.optString("consequenceNote") else null,
                        journalUpdated = tObj.optBoolean("journalUpdated", false),
                        timestamp = tObj.optString("timestamp", ""),
                        trustDelta = tObj.optInt("trustDelta", 0),
                        dispositionDelta = tObj.optInt("dispositionDelta", 0),
                        timeSpentMinutes = tObj.optInt("timeSpentMinutes", 1),
                        continuityUpdates = cuList,
                        rollbackSnapshot = rollbackSnap
                    )
                )
            }
        }

        // Legacy migration: If journal.notableEvents was not saved (or empty) but historical turns have substantive consequenceNotes,
        // migrate them into notableEvents and mark journalUpdated on those turns so the subtle "Journal updated" indicator displays.
        if (journal.notableEvents.isEmpty() && turnHistory.isNotEmpty()) {
            val nonNotableValues = setOf("none", "n/a", "no change", "unchanged", "nothing changed", "no consequences")
            val routineMovementRegex = Regex("""(?i)\b(takes a step|steps forward|steps back|sits down|stands up|shifts weight|looks at|observes|nods|crosses arms|unfurls)\b""")
            val migratedEvents = mutableListOf<String>()

            for (turn in turnHistory) {
                val note = turn.consequenceNote?.trim()
                if (!note.isNullOrBlank() && !nonNotableValues.contains(note.lowercase())) {
                    // Check if it's substantive (clues, item transfers, wounds, promises, dynamic shifts)
                    val isSubstantive = !routineMovementRegex.matches(note)
                    if (isSubstantive) {
                        if (!migratedEvents.contains(note)) {
                            migratedEvents.add(note)
                        }
                        // Ensure the turn reflects that a journal entry exists for this turn
                        val idx = turnHistory.indexOf(turn)
                        if (idx >= 0 && !turn.journalUpdated) {
                            turnHistory[idx] = turn.copy(journalUpdated = true)
                        }
                    }
                }
            }
            if (migratedEvents.isNotEmpty()) {
                journal.notableEvents.addAll(migratedEvents)
            }
        }

        return Campaign(
            campaignId = campaignId,
            title = json.optString("title", "New Campaign"),
            inputMode = json.optString("inputMode", "free_writing"),
            approvedPremise = json.optString("approvedPremise", ""),
            objective = json.optString("objective", "Survive"),
            settingName = json.optString("settingName", "Main Room"),
            relationshipDynamic = json.optString("relationshipDynamic", "Strangers"),
            cockpit = cockpit,
            player = player,
            npc = npc,
            clock = clock,
            continuity = continuity,
            journal = journal,
            objectiveRecord = objRecord,
            activeModules = activeModules,
            turnHistory = turnHistory,
            activeEnding = if (json.has("activeEnding") && !json.isNull("activeEnding")) json.optString("activeEnding") else null,
            activeEndingText = if (json.has("activeEndingText") && !json.isNull("activeEndingText")) json.optString("activeEndingText") else null,
            draftInput = json.optString("draftInput", ""),
            lastUpdated = json.optLong("lastUpdated", System.currentTimeMillis()),
            seeds = ScenarioSeeds.fromJsonObject(json.optJSONObject("seeds")),
            hiddenScenario = HiddenScenario.fromJsonObject(json.optJSONObject("hiddenScenario")),
            storyMemory = StoryMemory.fromJsonObject(json.optJSONObject("storyMemory"))
        )
    }
}
