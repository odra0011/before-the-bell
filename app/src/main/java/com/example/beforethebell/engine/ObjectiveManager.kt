package com.example.beforethebell.engine

import com.example.beforethebell.model.Campaign
import com.example.beforethebell.model.ScenarioObjectiveRecord

object ObjectiveManager {

    const val OPEN_ENDED_LABEL = "None — Open-ended"

    /**
     * Returns true if the objective or label indicates an open-ended scenario.
     */
    fun isOpenEnded(objective: String?): Boolean {
        if (objective == null) return false
        val norm = objective.trim().lowercase()
        return norm == "none — open-ended" ||
               norm == "none — open ended" ||
               norm == "none - open-ended" ||
               norm == "none - open ended" ||
               norm == "none" ||
               norm == "open-ended" ||
               norm == "open ended"
    }

    /**
     * Returns true if the campaign is running in open-ended mode.
     */
    fun isOpenEnded(campaign: Campaign): Boolean {
        return isOpenEnded(campaign.objective) ||
               isOpenEnded(campaign.seeds?.objective) ||
               isOpenEnded(campaign.objectiveRecord.title)
    }

    /**
     * Creates an objective record for an open-ended session without win/loss criteria or deadlines.
     */
    fun createOpenEndedObjectiveRecord(): ScenarioObjectiveRecord {
        return ScenarioObjectiveRecord(
            title = OPEN_ENDED_LABEL,
            detail = "Open-ended scenario. No player objective, victory/defeat criteria, or deadline.",
            successCondition = "",
            failureCondition = "",
            status = "ongoing",
            resolutionReason = null
        )
    }

    /**
     * Creates or validates an objective record. Neutral and adaptable to any scenario.
     */
    fun ensureObjectiveRecord(
        objective: String,
        premise: String = "",
        titleOverride: String? = null
    ): ScenarioObjectiveRecord {
        if (isOpenEnded(objective) || isOpenEnded(titleOverride)) {
            return createOpenEndedObjectiveRecord()
        }
        val obj = objective.ifBlank { "Discover the truth" }.trim()
        val title = titleOverride?.ifBlank { obj } ?: obj
        val detail = premise.ifBlank { "Engage with the situation." }.take(180)
        return ScenarioObjectiveRecord(
            title = title,
            detail = detail,
            successCondition = "Demonstrably achieve \"$title\" through decisive inquiry and action.",
            failureCondition = "\"$title\" becomes definitively unreachable within this scenario through irreversible events.",
            status = "ongoing",
            resolutionReason = null
        )
    }

    /**
     * Placeholder record created at session start. The real objective, success and
     * failure criteria are authored by the AI in the opening response
     * (see [applyAuthoredObjective]).
     */
    fun placeholderObjectiveRecord(want: String): ScenarioObjectiveRecord {
        if (isOpenEnded(want)) {
            return createOpenEndedObjectiveRecord()
        }
        val w = want.trim().ifBlank { "Discover the truth" }
        return ScenarioObjectiveRecord(
            title = w,
            detail = "",
            successCondition = "Demonstrably achieve \"$w\" through decisive inquiry and action.",
            failureCondition = "\"$w\" becomes definitively unreachable within this scenario.",
            status = "ongoing",
            resolutionReason = null
        )
    }

    /**
     * Replaces the placeholder with the objective authored in the opening call.
     * In open-ended mode, preserves the open-ended record and ignores any authored criteria.
     */
    fun applyAuthoredObjective(
        current: ScenarioObjectiveRecord,
        authored: ScenarioObjectiveRecord?
    ): ScenarioObjectiveRecord {
        if (isOpenEnded(current.title)) {
            return current
        }
        if (authored == null) return current
        return current.copy(
            title = authored.title.ifBlank { current.title },
            detail = authored.detail.ifBlank { current.detail },
            successCondition = authored.successCondition.ifBlank { current.successCondition },
            failureCondition = authored.failureCondition.ifBlank { current.failureCondition },
            status = "ongoing",
            resolutionReason = null
        )
    }

    fun updateObjectiveFromTurn(
        campaign: Campaign,
        objectiveStatus: String?,
        resolutionReason: String?
    ) {
        if (isOpenEnded(campaign)) {
            campaign.objectiveRecord = campaign.objectiveRecord.copy(
                status = "ongoing",
                resolutionReason = null
            )
            return
        }
        val status = objectiveStatus?.lowercase()?.trim() ?: "ongoing"
        if (status == "success" || status == "failure") {
            campaign.objectiveRecord = campaign.objectiveRecord.copy(
                status = status,
                resolutionReason = resolutionReason
            )
        } else {
            campaign.objectiveRecord = campaign.objectiveRecord.copy(
                status = "ongoing",
                resolutionReason = null
            )
        }
    }
}
