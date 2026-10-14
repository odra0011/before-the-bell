package com.example.beforethebell.engine

import com.example.beforethebell.model.Campaign
import com.example.beforethebell.model.GameClock

object RulesEngine {

    fun isClockUnknown(clock: GameClock): Boolean {
        val t = clock.formattedTime.trim()
        if (t.isBlank()) return true
        if (t == "02:00" && (clock.startMinutes == 120 || clock.startMinutes == 0)) return true
        return false
    }

    fun parseClockTime(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val matchAmPm = Regex("""(\d{1,2})\s*:\s*(\d{2})\s*(am|pm)""", RegexOption.IGNORE_CASE).find(raw)
        if (matchAmPm != null) {
            var h = matchAmPm.groupValues[1].toIntOrNull() ?: return null
            val min = matchAmPm.groupValues[2].toIntOrNull() ?: return null
            val ampm = matchAmPm.groupValues[3].lowercase()
            if (h !in 1..12 || min !in 0..59) return null
            if (ampm == "pm" && h < 12) h += 12
            if (ampm == "am" && h == 12) h = 0
            return h * 60 + min
        }
        val m = Regex("""(\d{1,2})\s*:\s*(\d{2})""").find(raw) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return h * 60 + min
    }

    fun estimateMinutesFromStory(campaign: Campaign): Int? {
        val keywords = listOf(
            "dawn" to 360,
            "daybreak" to 360,
            "sunrise" to 360,
            "early morning" to 420,
            "morning" to 540,
            "midday" to 720,
            "noon" to 720,
            "early afternoon" to 810,
            "afternoon" to 870,
            "late afternoon" to 990,
            "dusk" to 1110,
            "sunset" to 1110,
            "twilight" to 1110,
            "evening" to 1200,
            "night" to 1320,
            "dead of night" to 1380,
            "midnight" to 0
        )

        for (fact in campaign.continuity.facts.filter { it.active }) {
            val lower = fact.text.lowercase()
            for ((pattern, minutes) in keywords) {
                if (Regex("""\b${Regex.escape(pattern)}\b""").containsMatchIn(lower)) {
                    return minutes
                }
            }
        }

        val setting = campaign.settingName.lowercase()
        for ((pattern, minutes) in keywords) {
            if (Regex("""\b${Regex.escape(pattern)}\b""").containsMatchIn(setting)) {
                return minutes
            }
        }

        val latestStory = campaign.turnHistory.lastOrNull()?.storyText?.lowercase()
        if (!latestStory.isNullOrBlank()) {
            for ((pattern, minutes) in keywords) {
                if (Regex("""\b${Regex.escape(pattern)}\b""").containsMatchIn(latestStory)) {
                    return minutes
                }
            }
        }

        return null
    }

    fun advanceClock(clock: GameClock, minutes: Int) {
        val delta = minutes.coerceAtLeast(0)
        clock.currentMinutes += delta

        val hours = (clock.currentMinutes / 60) % 24
        val mins = clock.currentMinutes % 60
        clock.formattedTime = String.format("%02d:%02d", hours, mins)
    }

    fun evaluateEndingPredicates(
        campaign: Campaign,
        fatalReason: String? = null
    ): Pair<String, String>? {
        // 1. Mortal Trauma Sustained
        if (!fatalReason.isNullOrBlank()) {
            return Pair(
                "Mortal Trauma Sustained",
                fatalReason
            )
        }

        // Open-ended mode: skip objective-based victory/defeat evaluation and automatic endings.
        // Continue until the player chooses to stop.
        if (ObjectiveManager.isOpenEnded(campaign)) {
            return null
        }

        // 2. Scenario Objective Resolution
        if (campaign.objectiveRecord.status.equals("success", ignoreCase = true)) {
            val title = "Objective Achieved: ${campaign.objectiveRecord.title}"
            val narrative = campaign.objectiveRecord.resolutionReason
                ?: "Your objective has been demonstrably achieved."
            return Pair(title, narrative)
        }

        if (campaign.objectiveRecord.status.equals("failure", ignoreCase = true)) {
            val title = "Objective Failed: ${campaign.objectiveRecord.title}"
            val narrative = campaign.objectiveRecord.resolutionReason
                ?: "The objective has become definitively unreachable within this scenario."
            return Pair(title, narrative)
        }

        return null
    }

    fun rewindTurn(campaign: Campaign): Boolean {
        // Only player turns (turnNumber > 0) can be undone.
        // Turn 0 is the opening scene and establishes the persistent scenario.
        if (campaign.turnHistory.size <= 1) return false

        // Remove the last turn
        val lastTurn = campaign.turnHistory.removeLastOrNull() ?: return false
        val snapshot = lastTurn.rollbackSnapshot

        if (snapshot != null) {
            campaign.clock.currentMinutes = snapshot.clockMinutes
            campaign.clock.formattedTime = snapshot.formattedTime
            campaign.npc.trust = snapshot.trust
            campaign.npc.disposition = snapshot.disposition

            campaign.continuity.facts.clear()
            campaign.continuity.facts.addAll(snapshot.facts)
            campaign.continuity.lastUpdatedTurn = campaign.turnHistory.size

            campaign.journal.currentImpression = snapshot.currentImpression ?: ""
            campaign.journal.impressionHistory.clear()
            campaign.journal.impressionHistory.addAll(snapshot.impressionHistory)
            campaign.journal.notableEvents.clear()
            campaign.journal.notableEvents.addAll(snapshot.notableEvents)

            if (snapshot.objectiveRecord != null) {
                campaign.objectiveRecord = snapshot.objectiveRecord
            } else {
                campaign.objectiveRecord = campaign.objectiveRecord.copy(status = "ongoing", resolutionReason = null)
            }

            campaign.storyMemory = snapshot.storyMemorySnapshot?.copy()
        }

        // Invariant: StoryMemory must never cover turns that no longer exist
        val maxAllowedCoveredTurn = campaign.turnHistory.size - 1
        if ((campaign.storyMemory?.coveredTurnNumber ?: -1) > maxAllowedCoveredTurn) {
            var restoredMemory: com.example.beforethebell.model.StoryMemory? = null
            for (i in campaign.turnHistory.indices.reversed()) {
                val snap = campaign.turnHistory[i].rollbackSnapshot?.storyMemorySnapshot
                if (snap != null && snap.coveredTurnNumber <= maxAllowedCoveredTurn) {
                    restoredMemory = snap.copy()
                    break
                }
            }
            campaign.storyMemory = restoredMemory
        }

        campaign.activeEnding = null
        campaign.activeEndingText = null

        return true
    }
}
