package com.example.beforethebell.engine

import com.example.beforethebell.model.Campaign
import com.example.beforethebell.model.CharacterCard
import com.example.beforethebell.model.PromptModule
import org.json.JSONArray
import org.json.JSONObject

data class TransmittedMessage(
    val role: String,
    val content: String
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("role", role)
            put("content", content)
        }
    }
}

object PromptBuilder {

    fun substitutePlaceholders(text: String, charName: String, userName: String): String {
        if (text.isBlank()) return ""
        return text
            .replace(Regex("""\{\{char\}\}""", RegexOption.IGNORE_CASE), charName)
            .replace(Regex("""\{\{user\}\}""", RegexOption.IGNORE_CASE), userName)
    }

    fun assemblePromptMessages(
        scope: String, // "opening" | "continuation"
        campaign: Campaign,
        character: CharacterCard?,
        playerText: String = "",
        actionIntent: String? = null,
        compressionEnabled: Boolean = true
    ): List<TransmittedMessage> {
        val charName = campaign.npc.name
        val userName = campaign.player.name.ifBlank { "the player character" }

        // Active enabled modules for this scope
        val activeModules = campaign.activeModules.filter {
            it.enabled && (it.scope == "both" || it.scope == scope)
        }

        // Seed-driven sessions: the AI authors a hidden scenario sheet in the opening call
        val seeds = campaign.seeds
        val authoringOpening = scope == "opening" && seeds != null
        val isOpenEnded = ObjectiveManager.isOpenEnded(campaign)
        val playerIdentityOpen = campaign.player.source == "story" || (campaign.player.role.isBlank() && campaign.player.name.isBlank())
        val openingSheetSchema = if (authoringOpening) {
            val identityFields = if (playerIdentityOpen) {
                """
    "playerName": "Assigned name for the player character (if open)",
    "playerRole": "Assigned role or calling for the player character (if open)","""
            } else ""
            val objectiveSchemaField = if (isOpenEnded) {
                ""
            } else {
                """,
    "objective": {
      "title": "Short concrete objective as $userName would understand it once established",
      "stakes": "What is at stake in this situation",
      "success": "What demonstrably achieving the objective looks like based on observable evidence",
      "failure": "What would make the objective definitively unreachable within this scenario"
    }"""
            }
            """
  "hiddenScenario": {
    "situation": "2-4 sentences: the actual situation, location context, and relevant player circumstances (private author notes)",
    "npcAgenda": "1-2 sentences: what $charName wants, fears, or is pursuing, consistent with their character card",
    "secrets": ["0 to 3 optional discoverable facts that could be uncovered in play (empty list [] is completely valid)"],$identityFields
    "startTime": "HH:MM approximate in-world time of day"$objectiveSchemaField
  },"""
        } else ""

        // 1. Application Protocol (System Instruction)
        val protocol = if (scope == "opening") {
            """You are the master roleplay narrator and author for "Before the Bell", setting the opening scene for a personal interactive fiction.

APPLICATION PROTOCOL:
- Output valid JSON only according to the specified schema.
- JSON ENCODING RULE: All internal speech, quotes, and punctuation within string values MUST use escaped quotes (\") or single quotes ('). Ensure the final payload is strictly valid RFC 8259 JSON.
- Do not invent voluntary speech, choices, or inner life for $userName.
- Maintain physical consistency and believable sensory presence for $charName and the environment.
- The story passage may contain dialogue, action, description, or silence in any natural combination. Spoken dialogue is not required if the scene begins in silence.
- In 'situation', provide 2–3 brief sentences describing only the starting circumstances $userName knows: where they are, what brought them there if known, and the immediate situation. Preserve mystery: never expose private author notes, hidden motives, secrets, unrevealed objective criteria, or future events. Unknown details remain unknown without inventing amnesia.
- In addition to the opening story, provide 2–4 Action options and 2–4 Speech options for $userName's opening turn.
  - Action options: Physical, nonverbal, or observational choices grounded in the current scene:
    - 'label': Concise description of a contextual action the player could take.
    - 'prose': The same action expressed in third-person italic roleplay prose using $userName's actual name, without assuming a contested outcome (e.g. "*$userName reaches for the item.*"). Preserve the selected intent without adding thoughts, dialogue, or extra actions; for contested outcomes, describe the attempt rather than claiming success.
    - Offer distinct physical intentions the player could pursue next, grounded in their character, capabilities, and current situation. Include opportunities to initiate a meaningful development when available, rather than offering only reactions to the NPC. A small gesture is appropriate when it communicates something consequential; several interchangeable gestures do not constitute distinct choices. Consider what each option could change, reveal, obtain, interrupt, or relinquish. Preserve uncertainty about contested outcomes and never assume the NPC's response.
  - Speech options: Exact, complete lines $userName could say (enclosed in quotation marks).
  - Offer meaningfully different approaches (testing claims, cooperating, concealing, disengaging, learning). Do not reveal hidden motives or outcomes. Respect known boundaries.

RESPONSE FORMAT (JSON ONLY):
{$openingSheetSchema
  "locationName": "Short 2-4 word location title",
  "situation": "2-3 brief sentences describing only the starting circumstances $userName knows: where they are, what brought them there if known, and the immediate situation. Preserve mystery: do not reveal hidden motives, secrets, unrevealed objectives, or future events. Unknown details remain unknown without inventing amnesia.",
  "story": "Opening story prose passage establishing the scene. Dialogue is enclosed in quotes inside the story body.",
  "startingPossessions": [
    "Array of concrete items and clothing $userName actually starts with in this scene, grounded in their character card and the scenario circumstances (e.g. weapons confiscated if captive, tools carried if accessible, attire worn)"
  ],
  "startingConstraints": [
    "Array of initial physical restraints or conditions established by the opening scenario (e.g. wrists bound with rope, locked in stone cell), or empty list []"
  ],
  "actionSuggestions": [
    {
      "label": "Concise description of a contextual action the player could take.",
      "prose": "The same action expressed in third-person italic roleplay prose using the player's name, without assuming a contested outcome."
    }
  ],
  "speechSuggestions": [
    "\"2 to 4 exact, complete spoken lines $userName could say next (enclosed in quotes)\""
  ]
}"""
        } else {
            """You are the master roleplay narrator and author for "Before the Bell", continuing an ongoing fiction with the depth and immersion of literary roleplay.

APPLICATION PROTOCOL:
- Output valid JSON only according to the specified schema.
- JSON ENCODING RULE: All internal speech, quotes, and punctuation within string values MUST use escaped quotes (\") or single quotes ('). Ensure the final payload is strictly valid RFC 8259 JSON.
- $userName owns their character's voluntary speech, choices, and thoughts.
- Portray $charName, other characters, and the world with physical continuity.
- Treat asserted attacks or external outcomes as attempts where possible. Resolve them against established equipment, restraints, position, and opposition (neither automatic success nor automatic defeat).
- Keep bookkeeping, instructions, and journal entries out of the story prose.
- Acknowledge mechanical shifts through natural fiction when relevant. Normal unchanged metadata (0 delta, null impression/consequence/audio) is completely valid; do not manufacture change every turn.
- NARRATIVE PROGRESSION: Resolved actions must have consequences that subsequent turns acknowledge. Characters and circumstances may develop according to their motives and established situation without waiting for the player to invent every development. Slow pacing must not mean replaying an unchanged interaction.
- ACTION & SPEECH CHOICES: Alongside the story, generate 2–4 Action options and 2–4 Speech options for $userName:
  - Choices should reflect the resulting situation and offer distinct intentions. Repeating an action remains valid when circumstances or its purpose have changed. Ground each option in the current scene, player's knowledge and capabilities, and established character and relationship.
  - Action options: Physical, nonverbal, or positional choices:
    - 'label': Concise description of a contextual action the player could take.
    - 'prose': The same action expressed in third-person italic roleplay prose using $userName's actual name, without assuming a contested outcome (e.g. "*$userName reaches for the item.*"). Preserve the selected intent without adding thoughts, dialogue, or extra actions; for contested outcomes, describe the attempt rather than claiming success.
    - Offer distinct physical intentions the player could pursue next, grounded in their character, capabilities, and current situation. Include opportunities to initiate a meaningful development when available, rather than offering only reactions to the NPC. A small gesture is appropriate when it communicates something consequential; several interchangeable gestures do not constitute distinct choices. Consider what each option could change, reveal, obtain, interrupt, or relinquish. Preserve uncertainty about contested outcomes and never assume the NPC's response.
  - Speech options: Exact, complete lines $userName could say (enclosed in quotation marks). Display the full line. Consider different purposes: learning something, making an offer, testing a claim, expressing a feeling, cooperating, concealing something, or disengaging.
  - Avoid repeated comply/resist menus, several paraphrases of one intention, obvious good/neutral/bad answers, and one universally superior choice. Prefer two meaningful options over four padded ones.
  - Make the attempted action or spoken meaning clear. Do not reveal hidden outcomes, secret motives, success percentages, or a 'correct' answer.
  - Do not invent possessions, abilities, memories, evidence, or private knowledge. Respect known injuries, restraints, and boundaries. Allow kindness, humor, cooperation, and quiet moments.

RESPONSE FORMAT (JSON ONLY):
{
  "story": "Single complete prose passage continuing the fiction. Dialogue occurs naturally inside quotes. Markdown paragraphs are preserved. Stop before deciding $userName's next voluntary action.",
  "objectiveStatus": "ongoing", // In open-ended mode, always "ongoing". Otherwise "ongoing" | "success" | "failure" (setbacks and failed attempts are "ongoing").
  "objectiveResolutionReason": null, // If "success" or "failure", concise 1-2 sentence description explaining the outcome; otherwise null.
  "trustDelta": 0, // Integer (-2 to +2) reflecting genuine shift based on evidence and conduct (0 if unchanged).
  "dispositionDelta": 0, // Integer (-2 to +2) reflecting emotional warmth/hostility (-2 to +2, 0 if unchanged).
  "timeSpentMinutes": 1, // Number based on physical exertion and conversational elapsed time. Brief utterances or quick lines are 0-1 min.
  "startTime": ${if (RulesEngine.isClockUnknown(campaign.clock)) "\"In-world 24-hour clock time (e.g. \\\"08:30\\\" or \\\"20:15\\\") establishing the current scene time\"," else "null,"}
  "impressionUpdate": "A compact evolving 2–4 sentence cumulative summary of what $charName has learned, believes, or feels about $userName across the encounter (distinguishing subjective beliefs from facts, preserving mystery without hidden plot solutions or secret plans), retaining still-relevant earlier impressions while revising them when events warrant; or null if there is no meaningful change.",
  "consequenceNote": null, // ONLY provide a short 1-sentence record for Journal -> Notable Events when an event worth revisiting occurred (a discovered clue, significant item transfer, promise, or meaningful relationship development). Record what happened without revealing hidden motives or solving clues. For routine gestures, repeated observations, proximity changes, atmosphere, and paraphrases, STRICTLY output null.
  "continuityUpdates": [
    // Structured ID-based updates for meaningful physical/factual changes that actually happened in this turn (injuries, restraints, possessions, discoveries).
    // To update, alter, lose, or remove an established fact: specify its bracketed ID in updatesFactId and set active: false (or action: "remove").
    // To add a new fact: { "category": "condition"|"capability"|"restraint"|"possession"|"location"|"discovery", "subject": string, "fact": string, "updatesFactId": null, "active": true }
  ],
  "suggestedAudioCue": null,
  "actionSuggestions": [
    {
      "label": "Concise description of a contextual action the player could take.",
      "prose": "The same action expressed in third-person italic roleplay prose using the player's name, without assuming a contested outcome."
    }
  ],
  "speechSuggestions": [
    "\"2 to 4 exact, complete spoken lines $userName could say next (enclosed in quotes)\""
  ]
}"""
        }

        // 2. System-role modules positioned BEFORE history
        val systemBeforeModules = activeModules.filter {
            it.placement == "before_history" && it.role == "system"
        }
        val beforeInstructionsText = if (systemBeforeModules.isNotEmpty()) {
            val rendered = systemBeforeModules
                .sortedBy { it.order }
                .joinToString("\n\n") { m ->
                    val substituted = substitutePlaceholders(m.content, charName, userName)
                    "### ${m.name}\n${substituted.trim()}"
                }
            "\n\nWRITING INSTRUCTIONS:\n$rendered"
        } else ""

        val systemPrompt = "$protocol$beforeInstructionsText".trim()

        // 3. User Context
        val contextParts = mutableListOf<String>()

        if (!character?.worldFoundations.isNullOrBlank()) {
            contextParts.add("WORLD FOUNDATIONS & LORE:\n${character.worldFoundations}")
        }

        fun seedLabel(v: String) = v.trim().ifBlank { "open (you decide)" }
        val notCaptured = "Not recorded; judge from the established premise and keep it consistent."

        if (seeds != null) {
            val cardScenarioText = character?.scenario?.let { substitutePlaceholders(it, charName, userName).trim() } ?: ""

            if (authoringOpening) {
                val seedLines = StringBuilder()
                seedLines.append("SCENARIO SETUP CHOICES (chosen by the player; \"Let the story decide\" means you invent it):\n")
                seedLines.append("- Genre / Mood: ${seedLabel(seeds.genre)}\n")
                val relDesc = if (seeds.relationship.isNotBlank()) {
                    if (seeds.relationship == "Captor & captive") {
                        if (seeds.directionalRole == "player_captor") "Captor & captive ($userName is Captor, $charName is Captive)"
                        else "Captor & captive ($charName is Captor, $userName is Captive)"
                    } else seeds.relationship
                } else "Let the story decide"
                seedLines.append("- Relationship / Dynamic: $relDesc\n")
                seedLines.append("- Objective: ${if (isOpenEnded) "None — Open-ended" else seedLabel(seeds.objective)}\n")
                seedLines.append("- Setting / Place: ${seedLabel(seeds.setting)}")
                if (seeds.directions.isNotBlank()) {
                    seedLines.append("\n- Player's Directions (Preferences guide generation flexibly; explicit exclusions must be respected): ${seeds.directions.trim()}")
                }
                contextParts.add(seedLines.toString())

                if (cardScenarioText.isNotBlank()) {
                    contextParts.add("""CARD STARTING LORE & SCENARIO REFERENCE:
$cardScenarioText
(Card background and worldbuilding inform the fiction. Explicit session choices above override any conflicting starting scenario in the card.)""")
                }

                val objectiveAuthoringDirective = if (isOpenEnded) {
                    "4. Open-ended play: Do NOT create a player objective, success/failure criteria, or deadline. The scene continues organically without victory or defeat conditions. Let $charName's goals, continuity, and consequences develop normally."
                } else {
                    "4. A concrete, pursuable objective with evidence-based success and definitive failure criteria (defeat requires the objective becoming genuinely unreachable, not temporary rejection or slow progress)."
                }

                contextParts.add("""SCENARIO AUTHORING:
- Invent the concrete situation yourself based on the setup choices above. Any scenario you want to make is valid—from interrogation to romance, adventure to slice-of-life.
- The hidden scenario must establish:
  1. The actual situation and relevant player circumstances.
  2. $charName's starting agenda, consistent with their character card.
  3. Optional discoverable secrets (0 is completely valid; do not force secrets if unneeded).
  $objectiveAuthoringDirective
- Starting facts and motives: These are starting facts and motives, not a predetermined plot. Established truths must remain consistent; intentions and relationships evolve naturally through events.
- Card synergy: Use $charName's identity, background, and compatible worldbuilding. Explicit session choices override conflicting card starting scenarios.

SURPRISE & PLAYER KNOWLEDGE:
- Reveal the situation through play with enough grounded information to act. Do not dump the private scenario into an expository briefing.
- Distinguish information unknown to the human player from what $userName obviously knows in-character: do NOT force amnesia, do NOT withhold $userName's own basic identity, and do NOT portray unexplained ignorance of an established friend or known companion.
- Mystery & Tone: Use the configured Mystery slider (${campaign.cockpit.mystery}/100) for how openly the situation and clues are presented, and Twists (${campaign.cockpit.twists}/100) for plausible complications.
- Prevent spoilers: Action and Speech suggestions must NEVER leak unrevealed secrets or hidden NPC motives. Ground options in $userName's established in-fiction knowledge.
- Never quote or summarize hiddenScenario directly in the story prose.""")
            } else {
                val hs = campaign.hiddenScenario
                if (hs != null) {
                    val secretsText = if (hs.secrets.isNotEmpty()) hs.secrets.joinToString("\n") { "  • $it" } else "  • (None)"
                    contextParts.add("""HIDDEN SCENARIO (Author's private reference; unrevealed to the player unless discovered):
- Situation: ${hs.situation.ifBlank { notCaptured }}
- $charName's Agenda: ${hs.npcAgenda.ifBlank { notCaptured }}
- Discoverable Secrets:
$secretsText

CONTINUITY & KNOWLEDGE RULES:
- Established facts remain consistent. Intentions, trust, and dynamics evolve naturally through events.
- Never leak unrevealed secrets or hidden NPC motives in Action or Speech suggestions. Suggestions must only reflect $userName's established in-fiction knowledge and reasonable intent.
${if (isOpenEnded) "- In open-ended mode, skip objective-based victory/defeat evaluation: play continues until the player chooses to stop, while NPC goals, continuity, and consequences develop normally." else "- Evaluate objective progress strictly against observable evidence. Setbacks, rejection, and failed attempts are 'ongoing'. Defeat requires the objective becoming definitively unreachable."}""")
                } else if (campaign.approvedPremise.isNotBlank()) {
                    contextParts.add("SCENARIO PREMISE:\n${campaign.approvedPremise}")
                }
                val seedDetails = listOfNotNull(
                    seeds.genre.ifBlank { null }?.let { "Genre: $it" },
                    seeds.relationship.ifBlank { null }?.let { "Relationship: $it" },
                    seeds.directions.ifBlank { null }?.let { "Directions: $it" }
                )
                if (seedDetails.isNotEmpty()) contextParts.add("SCENARIO SETUP: ${seedDetails.joinToString(" | ")}")
                contextParts.add("SETTING: ${campaign.settingName}")
            }
        } else {
            val premiseContent = if (isOpenEnded) "Open-ended exploration." else campaign.approvedPremise.ifBlank { campaign.objective }
            contextParts.add("SCENARIO PREMISE:\n$premiseContent")
            contextParts.add("SETTING: ${campaign.settingName}")
        }

        if (!authoringOpening) {
            if (isOpenEnded) {
                contextParts.add("""SCENARIO MODE: Open-Ended (No Player Objective)
- There is no assigned player objective, success/failure criteria, or deadline.
- Skip objective-based victory/defeat evaluation and automatic endings. Keep 'objectiveStatus' as 'ongoing'.
- Let $charName's goals, physical continuity, and somatic consequences develop normally (open-ended does not mean invulnerability).
- Continue play until the player chooses to stop.""")
            } else {
                val objRec = campaign.objectiveRecord
                contextParts.add("""SCENARIO OBJECTIVE & RESOLUTION CRITERIA:
- Objective: ${objRec.title.ifBlank { notCaptured }}
- Focus & Stakes: ${objRec.detail.ifBlank { notCaptured }}
- Meaning of Success: ${objRec.successCondition.ifBlank { notCaptured }}
- Definitive Unreachability (Failure): ${objRec.failureCondition.ifBlank { notCaptured }}
- Current Status: ${objRec.status}

OBJECTIVE EVALUATION RULES:
- Evaluate objective progress based strictly on established events up to this moment.
- 'ongoing': Default active state. Includes setbacks, physical wounds, rejection, failed attempts, resistance, slow progress, and unresolved tension. If outcome is in any way uncertain, continue play as 'ongoing'.
- 'success': Use ONLY when the objective has demonstrably and concretely been achieved.
- 'failure': Use ONLY when the objective has become definitively unreachable within this scenario (e.g., irreversible catastrophe, fatal trauma, or permanent barrier).
- Do NOT treat temporary hostility, low relationship scores, or a single refusal as automatic defeat.
- Respect explicit boundaries without turning them into obstacles the player must wear down.
- For survival objectives, do NOT force an ending because turns have elapsed; assess whether an achievable endpoint (such as reaching safety or outlasting the established danger) has demonstrably occurred.
- Do NOT invent arbitrary failure triggers, move the goalposts, or force an ending because the story has lasted many turns.""")
            }
        }

        if (!authoringOpening) {
            if (RulesEngine.isClockUnknown(campaign.clock)) {
                contextParts.add("CURRENT STORY TIME: Not yet established in world. Determine an appropriate initial story clock time (e.g. \"09:00\", \"14:30\", \"21:00\") matching the scene atmosphere and specify it in 'startTime'.")
            } else {
                contextParts.add("CURRENT IN-WORLD TIME: ${campaign.clock.formattedTime} (Advance appropriately via timeSpentMinutes)")
            }
        }

        val mesEx = character?.mesExample?.let { substitutePlaceholders(it, charName, userName) } ?: ""
        val voiceSnippet = if (mesEx.isNotBlank()) "\n  Voice & Tone Reference (Stylistic only):\n  ${mesEx.take(500)}" else ""

        val genderStr = if (!campaign.player.customGender.isNullOrBlank()) "${campaign.player.gender} (${campaign.player.customGender})" else campaign.player.gender
        val pronounsStr = if (campaign.player.pronouns.isNotBlank()) " | Pronouns: ${campaign.player.pronouns}" else ""

        // Resolve Player Persona details with {{char}} referring to userName
        fun resolvePlayerPlaceholders(raw: String): String {
            if (raw.isBlank()) return ""
            return raw
                .replace(Regex("""\{\{char\}\}""", RegexOption.IGNORE_CASE), userName)
                .replace(Regex("""\{\{user\}\}""", RegexOption.IGNORE_CASE), charName)
        }

        val player = campaign.player
        val playerDetailsSb = StringBuilder()
        playerDetailsSb.append("- $userName (Player Character):\n")
        playerDetailsSb.append("  Gender: $genderStr$pronounsStr\n")
        val roleText = when {
            player.role.isNotBlank() -> player.role
            authoringOpening -> "Left to the story: assign a fitting identity in hiddenScenario and reveal it naturally inside the opening."
            else -> "Established in play"
        }
        playerDetailsSb.append("  Role: $roleText")

        if (player.source == "card" || player.description.isNotBlank() || player.personality.isNotBlank()) {
            if (player.title.isNotBlank() && player.title != player.role) {
                playerDetailsSb.append("\n  Title / Calling: ${player.title}")
            }
            if (player.description.isNotBlank()) {
                val resolvedDesc = resolvePlayerPlaceholders(player.description)
                playerDetailsSb.append("\n  Appearance & Background:\n  ${resolvedDesc.take(800)}")
            }
            if (player.personality.isNotBlank()) {
                val resolvedPers = resolvePlayerPlaceholders(player.personality)
                playerDetailsSb.append("\n  Temperament, Skills & Voice Traits:\n  ${resolvedPers.take(600)}")
            }
            if (player.mesExample.isNotBlank()) {
                val resolvedVoice = resolvePlayerPlaceholders(player.mesExample)
                playerDetailsSb.append("\n  Voice Style Reference (Informs suggestions only):\n  ${resolvedVoice.take(400)}")
            }
        }
        if (player.background.isNotBlank() && player.background.trim() != player.description.trim()) {
            playerDetailsSb.append("\n  Concealed History / Background (Private to player unless revealed in play): ${player.background}")
        }

        val playerRulesSnippet = """
PLAYER PERSONA RULES:
- $userName is exclusively controlled by the player. NEVER invent voluntary actions, spoken dialogue, decisions, or internal feelings for $userName.
- The player persona's skills, knowledge, and temperament inform contextual Action and Speech suggestions and plausible opportunities in the fiction. Skills create plausible opportunities, not guaranteed success.
- NPCs know only what they could reasonably observe or learn from their position, not $userName's private thoughts, hidden secrets, or unvoiced history.
- Starting circumstances and scene tension are governed by the session settings, scenario, and objective; ignore any scenario or opening directives embedded in the player's own card."""

        contextParts.add("""PARTICIPANTS:
- $charName (${campaign.npc.title}):
  Personality: ${campaign.npc.personality}
  Duty / Agenda: ${campaign.npc.duty.ifBlank { "Personal duty" }} | ${campaign.npc.agenda.ifBlank { "Pursue agenda" }}
  Current Trust: ${campaign.npc.trust}/10 | Disposition: ${campaign.npc.disposition} (-10 to 10)$voiceSnippet
$playerDetailsSb
$playerRulesSnippet""")

        val continuityText = ContinuityManager.formatContinuityRecord(campaign)
        contextParts.add("""CURRENT CONTINUITY RECORD (CONSEQUENTIAL PHYSICAL FACTS):
$continuityText

PRIORITY OF ESTABLISHED CIRCUMSTANCES:
- The facts above represent established in-world reality. They strictly supersede generic character card lore, opening scenery, or prior intact states.
- Characters must not describe lost capabilities (e.g. severed digits, missing limbs, blinded eyes) as present or usable without an established in-fiction healing event.
- Historical references to earlier events and injuries are valid, but present actions must conform strictly to these physical realities.
- When an established condition, stance, or location changes, specify its bracketed ID in continuityUpdates (updatesFactId) rather than duplicating it.""")

        if (campaign.journal.currentImpression.isNotBlank()) {
            contextParts.add("""CUMULATIVE NPC MEMORY OF PLAYER (What $charName has learned, believes, or feels about $userName across the encounter so far):
"${campaign.journal.currentImpression}"
Note: If this turn meaningfully shifts or adds to $charName's perspective, provide an updated compact cumulative 2–4 sentence summary in 'impressionUpdate', retaining still-relevant impressions and reasons rather than erasing earlier developments. If unchanged, return null.""")
        } else {
            contextParts.add("""CUMULATIVE NPC MEMORY OF PLAYER:
No established impression yet. If this interaction establishes a clear impression, summarize what $charName has learned, believes, or feels about $userName in 'impressionUpdate' (2–4 concise sentences, describing subjective beliefs as beliefs without hidden plot solutions or secret plans), or null if neutral/uncertain.""")
        }

        // Rolling Story Memory (Compressed prior context; only injected when compression is enabled)
        val storyMem = campaign.storyMemory
        if (compressionEnabled && storyMem != null && storyMem.summary.isNotBlank()) {
            contextParts.add("""STORY MEMORY (CONSEQUENTIAL PRIOR EVENTS UP TO TURN ${storyMem.coveredTurnNumber}):
${storyMem.summary.trim()}
(Note: The events above represent consequential prior history already resolved. Recent scene exchanges follow below verbatim.)""")
        }

        // Pacing & Challenge
        val ratio = campaign.cockpit.dialogueActionRatio
        val dialogueGuidance = if (ratio >= 65) "Dialogue-favored (spoken exchange carries the interaction when characters would speak; ground with brief physical beats without mandatory speech every turn)" else if (ratio <= 35) "Narration-favored (prioritize physical grounding, movement, atmosphere, and action over dialogue when characters are reserved)" else "Balanced mix of dialogue and narrative action"
        val paceVal = campaign.cockpit.pacing
        val paceGuidance = if (paceVal <= 35) "Linger (develop the immediate beat, micro-interactions, and physical atmosphere with patience before moving to new developments; slow pacing must not mean replaying an unchanged interaction)" else if (paceVal >= 65) "Advance (progress narrative beats briskly; resolve transitional moments and introduce new development without lingering)" else "Balanced progression of narrative beats"
        contextParts.add("PROSE & PACING:\n- Dialogue Ratio: $dialogueGuidance\n- Scene Pace: $paceGuidance")

        val initiative = if (campaign.cockpit.npcInitiative >= 65) "Proactive (the character actively advances their agenda, presses inquiries, and creates openings within established motives)" else if (campaign.cockpit.npcInitiative <= 35) "Reactive (the character primarily responds to player initiatives and questions within established motives)" else "Measured balance between reaction and initiative"
        val twists = if (campaign.cockpit.twists >= 65) "Eventful (introduce plausible situational complications, interruptions, or shifts grounded in the world rather than arbitrary reversals)" else if (campaign.cockpit.twists <= 35) "Steady (let events evolve naturally from established factors without abrupt external turns)" else "Grounded situational developments"
        val conseq = if (campaign.cockpit.consequences >= 65) "Severe (failure carries acute, lasting, and dangerous fallout)" else if (campaign.cockpit.consequences <= 35) "Gentle (failure is easily recoverable and forgiving)" else "Measured (failure produces proportionate, plausible setbacks)"
        val mysteryVal = campaign.cockpit.mystery
        val mysteryGuidance = if (mysteryVal <= 35) "Clear (reveal motives, evidence, and clues transparently as they arise without artificial vagueness)" else if (mysteryVal >= 65) "Enigmatic (preserve subtext, ambiguous clues, and layered secrets; let truth emerge indirectly)" else "Balanced clarity and intrigue"

        val fixedChallengeGuidance = """Success requires plausible actions, attention, and earned leverage. NPCs act competently within their established abilities, knowledge, motives, and limitations. They can misjudge, be deceived, or lose. Resolve uncertain actions from established circumstances; neither player success nor player failure is automatic.

Provide observable clues and opportunities sufficient for informed decisions, without revealing every secret or solution. Honor preparation, discoveries, and clever approaches. Never invent retroactive obstacles, grant NPCs inaccessible knowledge, or move the goalposts to defeat a sound plan.

Consequences follow causally and proportionately, respecting the configured Consequences setting ($conseq). Difficulty does not require constant hostility, physical punishment, or escalation. Allow earned victories, quiet exchanges, and recovery."""

        contextParts.add("""CHALLENGE & FAIR PLAY:
$fixedChallengeGuidance

NARRATIVE BEHAVIOR & STAKES:
- NPC Initiative: $initiative
- Complications: $twists
- Consequences: $conseq
- Mystery: $mysteryGuidance
- Input Mode: ${campaign.inputMode}""")

        val userContext = contextParts.joinToString("\n\n")

        // 4. Base conversational sequence
        val baseConversational = mutableListOf<TransmittedMessage>()

        if (scope == "continuation") {
            // When compression is enabled, turns covered by storyMemory are excluded from history.
            // When compression is disabled, full history is restored verbatim.
            val coveredTurn = if (compressionEnabled) (campaign.storyMemory?.coveredTurnNumber ?: -1) else -1
            for (turn in campaign.turnHistory) {
                if (turn.turnNumber <= coveredTurn) continue
                if (turn.turnNumber > 0 && turn.playerInput.isNotBlank()) {
                    val pastContent = if (!turn.playerIntent.isNullOrBlank() && turn.playerIntent.trim() != turn.playerInput.trim()) {
                        "${turn.playerInput.trim()}\n[Player Intent: ${turn.playerIntent.trim()}]"
                    } else {
                        turn.playerInput.trim()
                    }
                    baseConversational.add(TransmittedMessage(role = "user", content = pastContent))
                }
                if (turn.storyText.isNotBlank()) {
                    baseConversational.add(TransmittedMessage(role = "assistant", content = turn.storyText.trim()))
                }
            }

            if (playerText.isNotBlank()) {
                val currentContent = if (!actionIntent.isNullOrBlank() && actionIntent.trim() != playerText.trim()) {
                    "${playerText.trim()}\n[Player Intent: ${actionIntent.trim()}]"
                } else {
                    playerText.trim()
                }
                baseConversational.add(TransmittedMessage(role = "user", content = currentContent))
            }
        }

        val N = baseConversational.size

        // 5. Injected module slots
        class PreparedModule(
            val module: PromptModule,
            val renderedContent: String,
            val originalIndex: Int
        )

        val slotMap = mutableMapOf<Int, MutableList<PreparedModule>>()
        for (i in 0..N) {
            slotMap[i] = mutableListOf()
        }

        activeModules.forEachIndexed { idx, m ->
            // Skip before_history system modules (already in system prompt)
            if (m.placement == "before_history" && m.role == "system") return@forEachIndexed

            val substituted = substitutePlaceholders(m.content, charName, userName).trim()
            val slot = when (m.placement) {
                "before_history" -> 0
                "after_history" -> N
                "in_history" -> {
                    val d = m.depth.coerceAtLeast(0)
                    if (d == 0) N else (N - d).coerceAtLeast(0)
                }
                else -> 0
            }

            slotMap[slot]?.add(PreparedModule(module = m, renderedContent = substituted, originalIndex = idx))
        }

        // Sort modules in each slot by order ascending, then original index
        for (i in 0..N) {
            slotMap[i]?.sortWith(compareBy({ it.module.order }, { it.originalIndex }))
        }

        // Assemble conversational sequence
        val conversationalSequence = mutableListOf<TransmittedMessage>()
        for (k in 0..N) {
            slotMap[k]?.forEach { prep ->
                conversationalSequence.add(TransmittedMessage(role = prep.module.role, content = prep.renderedContent))
            }
            if (k < N) {
                conversationalSequence.add(baseConversational[k])
            }
        }

        // Final ordered messages
        val finalMessages = mutableListOf<TransmittedMessage>()
        finalMessages.add(TransmittedMessage(role = "system", content = systemPrompt))
        finalMessages.add(TransmittedMessage(role = "user", content = userContext))
        finalMessages.addAll(conversationalSequence)

        return finalMessages
    }
}
