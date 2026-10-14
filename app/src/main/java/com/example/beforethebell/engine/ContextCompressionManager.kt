package com.example.beforethebell.engine

import com.example.beforethebell.data.StorageManager
import com.example.beforethebell.model.AppSettings
import com.example.beforethebell.model.Campaign
import com.example.beforethebell.model.CharacterCard
import com.example.beforethebell.model.StoryMemory
import com.example.beforethebell.network.ApiErrorDetail
import com.example.beforethebell.network.OpenRouterService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

object ContextCompressionManager {

    private val compressionMutex = Mutex()

    /**
     * Minimum number of newly eligible turns required before triggering a subsequent compression cycle.
     * Prevents compressing single-turn increments repeatedly when prompts remain near or above trigger.
     */
    const val MIN_NEW_TURNS_BATCH = 3

    /**
     * Estimates the token count of a given text string (~3.8 characters per token).
     */
    fun estimateTextTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return kotlin.math.ceil(text.length / 3.8).toInt().coerceAtLeast(1)
    }

    /**
     * Estimates the total token count of an assembled list of messages,
     * including message and role framing overhead.
     */
    fun estimateRequestTokens(messages: List<TransmittedMessage>): Int {
        var total = 0
        for (m in messages) {
            total += estimateTextTokens(m.content) + 4
        }
        return total + 3
    }

    /**
     * Checks if context compression is needed based on the assembled request size and settings.
     * If needed, triggers a single rolling summarization request to compress older turns,
     * persists the StoryMemory and its coverage boundary together successfully before excluding covered turns.
     */
    suspend fun checkAndCompressIfNeeded(
        campaign: Campaign,
        character: CharacterCard?,
        playerText: String,
        actionIntent: String?,
        settings: AppSettings,
        openRouterService: OpenRouterService,
        storage: StorageManager
    ): Result<Unit> = compressionMutex.withLock {
        if (!settings.contextCompressionEnabled) {
            return Result.success(Unit)
        }

        // 1. Assemble current messages to estimate total request size
        val initialMessages = PromptBuilder.assemblePromptMessages(
            scope = "continuation",
            campaign = campaign,
            character = character,
            playerText = playerText,
            actionIntent = actionIntent,
            compressionEnabled = settings.contextCompressionEnabled
        )
        val estimatedTokens = estimateRequestTokens(initialMessages)

        val budget = settings.inputTokenBudget
        val trigger = settings.compressionTriggerTokens

        // If below trigger threshold, no compression needed
        if (estimatedTokens < trigger) {
            return Result.success(Unit)
        }

        // 2. Identify eligible turns for compression.
        // Invariant: Always keep at least the latest 12 messages verbatim.
        // Current turn in progress = 1 message.
        // The last 6 completed turns provide 12 messages (each turn = 1 user + 1 assistant message).
        // Total verbatim window = 13 messages (>= 12).
        val verbatimStartTurn = (campaign.turnHistory.size - 6).coerceAtLeast(0)
        val previousCoveredTurn = campaign.storyMemory?.coveredTurnNumber ?: -1

        val turnsToAbsorb = campaign.turnHistory.filter { turn ->
            turn.turnNumber > previousCoveredTurn && turn.turnNumber < verbatimStartTurn
        }

        // If compression cannot bring the request below the trigger, do not start summarizing every subsequent turn.
        // Require a meaningful batch of newly eligible history before another attempt (at least 3 turns),
        // while still reporting an unavoidable budget overrun.
        val minBatchSize = if (previousCoveredTurn >= 0) MIN_NEW_TURNS_BATCH else 1
        if (turnsToAbsorb.size < minBatchSize) {
            if (estimatedTokens > budget) {
                return Result.failure(
                    IllegalStateException(
                        "Input token budget exceeded (~$estimatedTokens tokens > $budget budget). " +
                        "The permanent prompts and recent 12-message history alone exceed the budget. " +
                        "Please increase the Input Token Budget in Settings."
                    )
                )
            }
            return Result.success(Unit)
        }

        // 3. Perform a single separate summarization request
        val charName = campaign.npc.name
        val userName = campaign.player.name.ifBlank { "the player" }
        val prevMemory = campaign.storyMemory?.summary?.trim() ?: ""

        val systemPrompt = """You are the master narrative continuity archivist for "Before the Bell", an interactive fiction roleplay.
Your task is to update the single hidden rolling Story Memory for this session.

STORY MEMORY REQUIREMENTS:
- Integrate the newly elapsed turns with the previous Story Memory into one continuous, cohesive narrative record.
- Preserve consequential events and their immediate causes.
- Preserve explicit commitments, bargains, promises, and established obligations.
- Preserve discoveries, revealed secrets, acquired or surrendered possessions, physical wounds, and restraints.
- Preserve relationship developments and shifts in dynamic between $charName and $userName.
- Preserve unresolved plot threads, open investigations, and looming dangers.
- Epistemic accuracy: Carefully distinguish established observable facts from subjective suspicions or beliefs. Note who knows what (e.g. what $userName knows vs what $charName knows vs mutual knowledge).
- CEILING: Maximum 1,000 words. This is a strict ceiling, not a target—keep short histories short.
- Plain text only: Do not include meta commentary, conversational filler, markdown headings, or conversational pleasantries."""

        val transcriptSb = StringBuilder()
        for (turn in turnsToAbsorb) {
            transcriptSb.append("--- TURN ${turn.turnNumber} ---\n")
            if (turn.playerInput.isNotBlank()) {
                val intent = if (!turn.playerIntent.isNullOrBlank() && turn.playerIntent.trim() != turn.playerInput.trim()) {
                    " [Intent: ${turn.playerIntent.trim()}]"
                } else ""
                transcriptSb.append("$userName: ${turn.playerInput.trim()}$intent\n")
            }
            if (turn.storyText.isNotBlank()) {
                transcriptSb.append("$charName: ${turn.storyText.trim()}\n")
            }
            transcriptSb.append("\n")
        }

        val userPromptSb = StringBuilder()
        userPromptSb.append("SESSION CONTEXT:\n")
        userPromptSb.append("Participants: $userName (Player Character) & $charName (${campaign.npc.title})\n")
        userPromptSb.append("Objective: ${campaign.objectiveRecord.title}\n")
        if (campaign.settingName.isNotBlank()) {
            userPromptSb.append("Setting: ${campaign.settingName}\n")
        }
        userPromptSb.append("\n")

        if (prevMemory.isNotBlank()) {
            userPromptSb.append("EXISTING ROLLING STORY MEMORY (Covering turns up to Turn $previousCoveredTurn):\n")
            userPromptSb.append(prevMemory)
            userPromptSb.append("\n\n")
        } else {
            userPromptSb.append("EXISTING ROLLING STORY MEMORY: (None - initial compression)\n\n")
        }

        userPromptSb.append("NEWLY ELAPSED TURNS TO ABSORB (Turns ${turnsToAbsorb.first().turnNumber} to ${turnsToAbsorb.last().turnNumber}):\n")
        userPromptSb.append(transcriptSb.toString().trim())
        userPromptSb.append("\n\n")
        userPromptSb.append("INSTRUCTION: Integrate the newly elapsed turns into the rolling Story Memory. Output only the updated summary (maximum 1,000 words):")

        val summarizationMessages = listOf(
            TransmittedMessage(role = "system", content = systemPrompt),
            TransmittedMessage(role = "user", content = userPromptSb.toString())
        )

        val summaryResult = openRouterService.generateText(
            settings = settings,
            messages = summarizationMessages,
            maxTokens = 1500,
            temperature = 0.3f,
            requestType = "compression"
        )

        if (summaryResult.isSuccess) {
            val newSummary = summaryResult.getOrNull()?.trim() ?: ""
            if (newSummary.isNotBlank()) {
                val newCoveredTurn = turnsToAbsorb.last().turnNumber
                val updatedMemory = StoryMemory(
                    summary = newSummary,
                    coveredTurnNumber = newCoveredTurn,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )

                // Persist StoryMemory and its coverage boundary together successfully before excluding covered turns.
                // A failed save must leave the previous valid context intact.
                val previousMemory = campaign.storyMemory?.copy()
                try {
                    val campaignToSave = campaign.copy(storyMemory = updatedMemory)
                    storage.saveCampaign(campaignToSave)
                    campaign.storyMemory = updatedMemory
                } catch (e: Exception) {
                    campaign.storyMemory = previousMemory
                    return Result.failure(
                        IOException(
                            "Failed to persist compressed StoryMemory (${e.message ?: "Storage error"}). " +
                            "Leaving previous valid context intact.", e
                        )
                    )
                }

                // Verify whether request now stays within budget; report unavoidable budget overrun if not
                val postCompressionMessages = PromptBuilder.assemblePromptMessages(
                    scope = "continuation",
                    campaign = campaign,
                    character = character,
                    playerText = playerText,
                    actionIntent = actionIntent,
                    compressionEnabled = settings.contextCompressionEnabled
                )
                val postEstimatedTokens = estimateRequestTokens(postCompressionMessages)
                if (postEstimatedTokens > budget) {
                    return Result.failure(
                        IllegalStateException(
                            "Input token budget exceeded (~$postEstimatedTokens tokens > $budget budget). " +
                            "Even after compression, permanent prompt modules and recent 12-message history exceed the configured budget. " +
                            "Please increase the Input Token Budget in Settings."
                        )
                    )
                }
            }
            return Result.success(Unit)
        } else {
            // On failure, retain the previous valid memory and uncovered messages.
            // Do not silently discard context or retry in an endless loop.
            val err = summaryResult.exceptionOrNull()
            if (estimatedTokens > budget) {
                if (err is ApiErrorDetail) {
                    return Result.failure(err)
                }
                return Result.failure(
                    IllegalStateException(
                        "Context compression failed (${err?.message ?: "Network error"}) and total prompt (~$estimatedTokens tokens) exceeds budget ($budget). " +
                        "Retaining full history. Please increase the Input Token Budget in Settings or verify your network connection."
                    )
                )
            }
            return Result.success(Unit)
        }
    }
}
