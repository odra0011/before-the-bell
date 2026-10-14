package com.example.beforethebell.engine

import com.example.beforethebell.model.Campaign
import com.example.beforethebell.model.CharacterCard
import com.example.beforethebell.model.ContinuityFact
import com.example.beforethebell.model.ContinuityRecord
import com.example.beforethebell.model.PlayerPersona
import org.json.JSONObject

object ContinuityManager {

    fun isValidContinuityFact(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = text.trim()
        if (t.length < 3) return false

        // Threats, intentions, warnings
        val isHypotheticalOrThreat = Regex("""\b(threatens?|threatened to|warns?|warned that|promises? to|will cut|will kill|will break|plans? to|intends? to|vows? to)\b""", RegexOption.IGNORE_CASE).containsMatchIn(t)
        if (isHypotheticalOrThreat) return false

        // Unsuccessful attempts
        val isFailedAttempt = Regex("""\b(tried to|attempted to|failed to|struggles? in vain|swings? and misses?|unable to reach)\b""", RegexOption.IGNORE_CASE).containsMatchIn(t)
        if (isFailedAttempt) return false

        // Mere suspicions or internal thoughts
        val isSuspicion = Regex("""\b(suspects? that|wonders? if|believes? secretly|might be|could be)\b""", RegexOption.IGNORE_CASE).containsMatchIn(t)
        if (isSuspicion) return false

        return true
    }

    fun normalizeFactText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace(Regex("""^[^:]+:\s*"""), "")
            .lowercase()
            .replace(Regex("""[^\w\s]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun isWornKeyword(text: String): Boolean {
        val lower = text.lowercase()
        val wornWords = listOf(
            "cloak", "cape", "coat", "jacket", "tunic", "shirt", "vest", "gown", "dress",
            "robe", "breeches", "trousers", "pants", "skirt", "boots", "shoes", "gloves",
            "gauntlet", "hood", "hat", "cap", "apron", "belt", "sash", "ring", "amulet",
            "necklace", "pendant", "bracelet", "shawl", "scarf", "armor", "armour", "cuirass",
            "helm", "helmet", "bracer", "greaves"
        )
        return wornWords.any { lower.contains(it) }
    }

    private fun cleanItemName(raw: String): String {
        var s = raw.trim()
            .replace(Regex("""^[-*•\d.]+\s*"""), "")
            .replace(Regex("""^(?:a|an|the|her|his|their|some|pair of|set of|worn|wearing)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:in|at|on|around|by|under|for|with)\s+(?:her|his|their|the|a)\b.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (s.endsWith(".") || s.endsWith(",")) s = s.dropLast(1).trim()
        return s.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun isValidItemName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.length < 3 || trimmed.length > 55) return false
        val lower = trimmed.lowercase()
        val invalidWords = setOf(
            "unknown", "none", "nothing", "anything", "everything", "someone", "herself", "himself",
            "itself", "myself", "confidence", "silence", "calm", "dignity", "grace", "patience",
            "quiet", "fear", "anger", "sorrow", "pride", "shame", "secret", "secrets", "mystery",
            "history", "past", "future", "mind", "thoughts", "intentions", "motives", "agenda"
        )
        if (invalidWords.contains(lower)) return false
        if (lower.all { !it.isLetter() }) return false
        return true
    }

    private fun splitAndAddItems(
        raw: String,
        isWorn: Boolean,
        out: MutableSet<Pair<String, Boolean>>
    ) {
        if (raw.isBlank()) return
        val parts = raw.split(Regex(""",|;|\band\b""", RegexOption.IGNORE_CASE))
        for (part in parts) {
            val cleaned = cleanItemName(part)
            if (isValidItemName(cleaned)) {
                val worn = isWorn || isWornKeyword(cleaned)
                out.add(Pair(cleaned, worn))
            }
        }
    }

    fun parsePossessionsFromText(
        text: String,
        out: MutableSet<Pair<String, Boolean>>
    ) {
        if (text.isBlank()) return

        val lines = text.lines()
        var inInventorySection = false
        var sectionIsWorn = false

        val headerRegex = Regex("""(?i)^\s*(?:[-*•#]{1,3}\s*)?(equipment|gear|inventory|items|tools|possessions|carried|attire|clothing|clothes|apparel|dress|garb|weapons|armament)\s*[:=\-]\s*(.*)$""")
        val wornHeaders = setOf("attire", "clothing", "clothes", "apparel", "dress", "garb")

        for (line in lines) {
            val headerMatch = headerRegex.find(line)
            if (headerMatch != null) {
                inInventorySection = true
                val hName = headerMatch.groupValues[1].lowercase()
                sectionIsWorn = wornHeaders.any { hName.contains(it) }
                val rest = headerMatch.groupValues[2].trim()
                if (rest.isNotBlank()) {
                    splitAndAddItems(rest, sectionIsWorn, out)
                }
                continue
            }

            if (inInventorySection) {
                val trimmed = line.trim()
                if (trimmed.isBlank() || (trimmed.contains(":") && !trimmed.startsWith("-") && !trimmed.startsWith("*") && !trimmed.startsWith("•"))) {
                    inInventorySection = false
                } else {
                    val cleanLine = trimmed.replace(Regex("""^[-*•\d.]+\s*"""), "").trim()
                    if (cleanLine.isNotBlank()) {
                        splitAndAddItems(cleanLine, sectionIsWorn, out)
                    }
                }
            }
        }

        // Prose clauses matching carried / equipped / worn items
        val carriedProseRegex = Regex("""(?i)\b(?:carries|carrying|equipped with|armed with|holds|keeps in (?:her|his|their) (?:pockets?|satchel|bag|pouch)|at (?:her|his|their) (?:belt|side|hip)|in (?:her|his|their) (?:pockets?|satchel|bag|pouch))\s+([^.;!\n]+)""")
        val wornProseRegex = Regex("""(?i)\b(?:wears|wearing|dressed in|clad in|garbed in|has on)\s+([^.;!\n]+)""")

        for (m in carriedProseRegex.findAll(text)) {
            splitAndAddItems(m.groupValues[1].trim(), isWorn = false, out)
        }

        for (m in wornProseRegex.findAll(text)) {
            splitAndAddItems(m.groupValues[1].trim(), isWorn = true, out)
        }
    }

    /**
     * Extracts established starting possessions from the player persona and optional character card.
     */
    fun extractStartingPossessions(
        persona: PlayerPersona,
        characterCard: CharacterCard? = null
    ): List<ContinuityFact> {
        val items = mutableListOf<ContinuityFact>()
        val playerName = persona.name.ifBlank { "Player" }

        val textSources = listOfNotNull(
            characterCard?.description,
            persona.description,
            persona.background,
            characterCard?.personality
        ).filter { it.isNotBlank() }

        val rawItemsFound = mutableSetOf<Pair<String, Boolean>>()

        for (text in textSources) {
            parsePossessionsFromText(text, rawItemsFound)
        }

        var idx = 0
        for ((itemName, isWorn) in rawItemsFound) {
            val cleanName = cleanItemName(itemName)
            if (isValidItemName(cleanName)) {
                val suffix = if (isWorn && !cleanName.contains("worn", ignoreCase = true)) " (worn)" else ""
                val factText = "$playerName: $cleanName$suffix"
                items.add(
                    ContinuityFact(
                        id = "fact_start_pos_${System.currentTimeMillis()}_${idx++}",
                        category = "possession",
                        subject = playerName,
                        text = factText,
                        establishedTurn = 0,
                        active = true
                    )
                )
            }
        }

        return items
    }

    /**
     * Establishes starting possessions and initial physical constraints for turn 0
     * from the AI model's opening response, falling back to character card extraction if empty.
     */
    fun establishOpeningPossessions(
        campaign: Campaign,
        startingPossessions: List<String>,
        startingConstraints: List<String>,
        fallbackCard: CharacterCard? = null
    ): List<ContinuityFact> {
        val playerName = campaign.player.name.ifBlank { "Player" }
        val establishedFacts = mutableListOf<ContinuityFact>()
        var idx = 0

        // 1. Process possessions from model response if provided
        if (startingPossessions.isNotEmpty()) {
            for (raw in startingPossessions) {
                val cleaned = cleanItemName(raw)
                if (isValidItemName(cleaned)) {
                    val isWorn = isWornKeyword(raw) || raw.contains("worn", ignoreCase = true)
                    val suffix = if (isWorn && !cleaned.contains("worn", ignoreCase = true)) " (worn)" else ""
                    val factText = "$playerName: $cleaned$suffix"
                    val fact = ContinuityFact(
                        id = "fact_pos_0_${System.currentTimeMillis()}_${idx++}",
                        category = "possession",
                        subject = playerName,
                        text = factText,
                        establishedTurn = 0,
                        active = true
                    )
                    campaign.continuity.facts.add(fact)
                    establishedFacts.add(fact)
                }
            }
        } else {
            // Fallback: extract from player persona and card
            val extracted = extractStartingPossessions(campaign.player, fallbackCard)
            for (fact in extracted) {
                campaign.continuity.facts.add(fact)
                establishedFacts.add(fact)
            }
        }

        // 2. Process starting physical constraints/restraints from model response
        for (constraint in startingConstraints) {
            val trimmed = constraint.trim()
            if (isValidContinuityFact(trimmed)) {
                val formatted = if (trimmed.contains(":")) trimmed else "$playerName: $trimmed"
                val fact = ContinuityFact(
                    id = "fact_restr_0_${System.currentTimeMillis()}_${idx++}",
                    category = "restraint",
                    subject = playerName,
                    text = formatted,
                    establishedTurn = 0,
                    active = true
                )
                campaign.continuity.facts.add(fact)
                establishedFacts.add(fact)
            }
        }

        campaign.continuity.lastUpdatedTurn = 0
        return establishedFacts
    }

    /**
     * Seeds starting possessions into legacy campaign continuity if not already established.
     * Prevents duplicate seeding. Does not use prose regex to guess loss.
     */
    fun seedStartingPossessions(
        campaign: Campaign,
        characterCard: CharacterCard? = null
    ): List<ContinuityFact> {
        val existingPossessions = campaign.continuity.facts.filter { it.category.equals("possession", ignoreCase = true) }
        if (existingPossessions.isNotEmpty()) {
            return emptyList()
        }

        val startingPossessions = extractStartingPossessions(campaign.player, characterCard)
        if (startingPossessions.isEmpty()) return emptyList()

        val finalFacts = mutableListOf<ContinuityFact>()
        for (fact in startingPossessions) {
            campaign.continuity.facts.add(fact)
            finalFacts.add(fact)
        }

        campaign.continuity.lastUpdatedTurn = campaign.turnHistory.size
        return finalFacts
    }

    fun extractPhysicalFactsFromProse(
        story: String?,
        playerName: String,
        npcName: String,
        turnNumber: Int
    ): List<ContinuityFact> {
        val extracted = mutableListOf<ContinuityFact>()
        if (story.isNullOrBlank()) return extracted

        val digitNames = listOf("little finger", "pinky", "ring finger", "middle finger", "index finger", "thumb")
        val severVerbs = Regex("""\b(sever|severed|severing|cleav|cleaved|cut off|sheared|struck through)\b""", RegexOption.IGNORE_CASE)

        if (severVerbs.containsMatchIn(story)) {
            val hand = if (Regex("""\b(left hand|uninjured hand|second hand)\b""", RegexOption.IGNORE_CASE).containsMatchIn(story) || turnNumber >= 11) "left" else "right"
            for (d in digitNames) {
                if (Regex("""\b$d\b""", RegexOption.IGNORE_CASE).containsMatchIn(story)) {
                    val cleanD = if (d == "pinky") "little finger" else d
                    extracted.add(
                        ContinuityFact(
                            id = "fact_prose_${System.currentTimeMillis()}_${(100..999).random()}",
                            category = "condition",
                            subject = playerName,
                            text = "$playerName ($hand hand): $cleanD severed at joint.",
                            establishedTurn = turnNumber,
                            active = true
                        )
                    )
                }
            }
        }

        // Burns
        if (Regex("""\b(scorched flesh|raw burn|forearm burned|burned.*iron poker)\b""", RegexOption.IGNORE_CASE).containsMatchIn(story)) {
            extracted.add(
                ContinuityFact(
                    id = "fact_prose_${System.currentTimeMillis()}_${(100..999).random()}",
                    category = "condition",
                    subject = playerName,
                    text = "$playerName: Forearm burned and blistered by heated iron.",
                    establishedTurn = turnNumber,
                    active = true
                )
            )
        }

        return extracted
    }

    fun applyContinuityUpdates(
        campaign: Campaign,
        updates: List<JSONObject>,
        turnNumber: Int,
        story: String,
        playerInput: String = ""
    ): List<ContinuityFact> {
        val continuity = campaign.continuity
        val playerName = campaign.player.name
        val newlyAdded = mutableListOf<ContinuityFact>()

        // 1. Process explicit updates from model
        for (upd in updates) {
            val rawFact = upd.optString("fact", "").trim()
            if (!isValidContinuityFact(rawFact)) continue

            val category = upd.optString("category", "condition").lowercase()
            val subject = upd.optString("subject", playerName).trim()
            val updatesFactId = if (upd.has("updatesFactId") && !upd.isNull("updatesFactId")) upd.optString("updatesFactId").trim() else null

            val action = upd.optString("action", "").lowercase()
            val isRemoval = action == "remove" || action == "delete" || action == "deactivate" || action == "lost"
            val lossKeywords = Regex("""\b(lost|dropped|confiscated|shattered|broken|destroyed|consumed|used up|surrendered|taken away)\b""", RegexOption.IGNORE_CASE)
            val isExplicitLoss = isRemoval || lossKeywords.containsMatchIn(rawFact)
            val isActive = if (isRemoval) false else if (upd.has("active")) upd.optBoolean("active", !isExplicitLoss) else !isExplicitLoss

            val formattedText = if (rawFact.contains(":")) rawFact else "$subject: $rawFact"
            val normText = normalizeFactText(formattedText)

            // If updating an existing fact ID
            if (!updatesFactId.isNullOrBlank()) {
                val cleanId = updatesFactId.replace(Regex("""[\[\]]"""), "").trim()
                val existing = continuity.facts.firstOrNull { it.id == cleanId || it.id.trim() == cleanId }
                if (existing != null) {
                    val idx = continuity.facts.indexOf(existing)
                    val updated = existing.copy(
                        category = if (category.isNotBlank()) category else existing.category,
                        subject = if (subject.isNotBlank()) subject else existing.subject,
                        text = if (rawFact.isNotBlank()) formattedText else existing.text,
                        establishedTurn = turnNumber,
                        active = isActive
                    )
                    continuity.facts[idx] = updated
                    newlyAdded.add(updated)
                    continue
                }
            }

            // Location update: replace active location fact
            if (category == "location") {
                val activeLoc = continuity.facts.firstOrNull { it.active && it.category == "location" }
                if (activeLoc != null) {
                    val idx = continuity.facts.indexOf(activeLoc)
                    val updatedLoc = activeLoc.copy(text = formattedText, establishedTurn = turnNumber)
                    continuity.facts[idx] = updatedLoc
                    newlyAdded.add(updatedLoc)
                    continue
                }
            }

            // Possession updates: check if deactivating an existing item by name
            if (category == "possession" && !isActive) {
                val itemCandidate = rawFact.replace(Regex("""\b(lost|dropped|confiscated|shattered|broken|destroyed|consumed|used up|surrendered|taken away)\b""", RegexOption.IGNORE_CASE), "").trim()
                val normCandidate = normalizeFactText(itemCandidate)
                val existingActive = continuity.facts.firstOrNull { it.active && it.category == "possession" && (normalizeFactText(it.text).contains(normCandidate) || normCandidate.contains(normalizeFactText(it.text))) }
                if (existingActive != null) {
                    val idx = continuity.facts.indexOf(existingActive)
                    val updated = existingActive.copy(active = false, text = formattedText, establishedTurn = turnNumber)
                    continuity.facts[idx] = updated
                    newlyAdded.add(updated)
                    continue
                }
            }

            // Idempotency check: don't duplicate identical active fact
            val alreadyExists = continuity.facts.any { it.active && it.category == category && normalizeFactText(it.text) == normText }
            if (alreadyExists) continue

            // Add new fact
            val newFact = ContinuityFact(
                id = "fact_t${turnNumber}_${System.currentTimeMillis()}_${(100..999).random()}",
                category = category,
                subject = subject,
                text = formattedText,
                establishedTurn = turnNumber,
                active = isActive
            )
            continuity.facts.add(newFact)
            newlyAdded.add(newFact)
        }

        // 4. Capability checks from digit loss
        val rightSevered = continuity.facts.filter { it.active && it.text.contains("right hand", ignoreCase = true) && it.text.contains("severed", ignoreCase = true) }
        val leftSevered = continuity.facts.filter { it.active && it.text.contains("left hand", ignoreCase = true) && it.text.contains("severed", ignoreCase = true) }

        if (rightSevered.size >= 4 && continuity.facts.none { it.active && it.text.contains("Right hand has lost all five digits", ignoreCase = true) }) {
            val capFact = ContinuityFact(
                id = "fact_cap_right_${System.currentTimeMillis()}",
                category = "capability",
                subject = playerName,
                text = "$playerName: Right hand has lost all five digits; completely incapable of grasping or holding objects with right hand.",
                establishedTurn = turnNumber,
                active = true
            )
            continuity.facts.add(capFact)
            newlyAdded.add(capFact)
        }

        if (leftSevered.size >= 4 && continuity.facts.none { it.active && it.text.contains("Left hand has lost all five digits", ignoreCase = true) }) {
            val capFact = ContinuityFact(
                id = "fact_cap_left_${System.currentTimeMillis()}",
                category = "capability",
                subject = playerName,
                text = "$playerName: Left hand has lost all five digits; completely incapable of grasping or holding objects with left hand.",
                establishedTurn = turnNumber,
                active = true
            )
            continuity.facts.add(capFact)
            newlyAdded.add(capFact)
        }

        continuity.lastUpdatedTurn = turnNumber
        return newlyAdded
    }

    fun formatContinuityRecord(campaign: Campaign): String {
        val activeFacts = campaign.continuity.facts.filter { it.active }
        if (activeFacts.isEmpty()) {
            return "No permanent physical injuries or special constraints established."
        }

        val sections = listOf(
            "PHYSICAL CONDITION & CAPABILITIES (injuries, burns, motor/sensory status)" to
                    activeFacts.filter { it.category == "condition" || it.category == "capability" },
            "RESTRAINTS & POSITIONING (bonds, physical stance, confining elements)" to
                    activeFacts.filter { it.category == "restraint" },
            "POSSESSIONS & INVENTORY (held, confiscated, or accessible gear)" to
                    activeFacts.filter { it.category == "possession" },
            "LOCATION & IMMEDIATE ENVIRONMENT" to
                    activeFacts.filter { it.category == "location" },
            "DISCOVERIES & RECORDED EVIDENCE" to
                    activeFacts.filter { it.category == "discovery" || it.category == "commitment" }
        )

        val sb = StringBuilder()
        for ((title, list) in sections) {
            if (list.isNotEmpty()) {
                sb.append(title).append(":\n")
                list.forEach { sb.append("• [").append(it.id).append("] ").append(it.text).append("\n") }
                sb.append("\n")
            }
        }

        return sb.toString().trim()
    }
}
