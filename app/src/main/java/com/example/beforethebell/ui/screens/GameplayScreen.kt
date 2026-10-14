package com.example.beforethebell.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beforethebell.data.StorageManager
import com.example.beforethebell.engine.ContextCompressionManager
import com.example.beforethebell.engine.ContinuityManager
import com.example.beforethebell.engine.ObjectiveManager
import com.example.beforethebell.engine.PromptBuilder
import com.example.beforethebell.engine.RulesEngine
import com.example.beforethebell.model.*
import com.example.beforethebell.network.ApiErrorDetail
import com.example.beforethebell.network.OpenRouterService
import com.example.beforethebell.theme.*
import com.example.beforethebell.ui.components.CharacterPortraitView
import com.example.beforethebell.ui.components.CharacterSheetDialog
import com.example.beforethebell.ui.components.InventorySheetDialog
import kotlinx.coroutines.launch

/** Parses "HH:MM" (24h) into minutes since midnight; null if unparseable. */
private fun parseClockTime(raw: String?): Int? {
    if (raw.isNullOrBlank()) return null
    val m = Regex("""(\d{1,2})\s*:\s*(\d{2})""").find(raw) ?: return null
    val h = m.groupValues[1].toIntOrNull() ?: return null
    val min = m.groupValues[2].toIntOrNull() ?: return null
    if (h !in 0..23 || min !in 0..59) return null
    return h * 60 + min
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameplayScreen(
    campaignId: String,
    storage: StorageManager,
    openRouterService: OpenRouterService,
    onBackToHome: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenFacts: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var campaign by remember { mutableStateOf(storage.getCampaign(campaignId)) }
    if (campaign == null) {
        Box(modifier = Modifier.fillMaxSize().background(NearBlack), contentAlignment = Alignment.Center) {
            Text("Campaign not found.", color = TextWarm)
        }
        return
    }

    val curCampaign = campaign!!
    var composerText by remember { mutableStateOf(curCampaign.draftInput) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var errorDetail by remember { mutableStateOf<ApiErrorDetail?>(null) }
    var isErrorExpanded by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Menu state: null (buttons visible), "action", "speech", or "keyboard"
    var activeMenu by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    // Lazy migration for existing sessions: ensure established possessions are seeded if missing
    LaunchedEffect(curCampaign.campaignId) {
        if (curCampaign.turnHistory.isNotEmpty() && curCampaign.continuity.facts.none { it.category.equals("possession", ignoreCase = true) }) {
            val playerCard = curCampaign.player.cardId?.let { storage.getCharacter(it) }
            val seeded = ContinuityManager.seedStartingPossessions(curCampaign, playerCard)
            if (seeded.isNotEmpty()) {
                storage.saveCampaign(curCampaign)
                campaign = curCampaign.copy()
            }
        }
    }

// Trigger Opening Scene generation if brand new campaign (turnHistory empty)
    var openingGenerationTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(curCampaign.campaignId, openingGenerationTrigger) {
        if (curCampaign.turnHistory.isEmpty() && !isLoading) {
            val settings = storage.loadSettings()
            val isKeyConfigured = settings.activeApiKey.isNotBlank() || (settings.provider == "custom" && settings.customBaseUrl.isNotBlank())
            if (!isKeyConfigured) {
                val providerName = when (settings.provider) {
                    "vertex" -> "Google Vertex"
                    "gemini" -> "Google Gemini"
                    "openrouter" -> "OpenRouter"
                    "custom" -> "Custom OpenAI"
                    else -> settings.provider
                }
                errorMessage = "$providerName API Key or Base URL not configured. Please add your credentials in Settings to play."
                return@LaunchedEffect
            }

            isLoading = true
            errorMessage = null
            coroutineScope.launch {
                val characterCard = storage.getCharacter(curCampaign.npc.id)
                val messages = PromptBuilder.assemblePromptMessages(
                    scope = "opening",
                    campaign = curCampaign,
                    character = characterCard
                )

                val generatedResult = openRouterService.generateCompletion(
                    settings = settings,
                    messages = messages,
                    diagnosticMetadata = com.example.beforethebell.network.DiagnosticMetadata(
                        scope = "opening",
                        modules = curCampaign.activeModules.toList()
                    )
                )

                if (generatedResult.isSuccess) {
                    val turnResp = generatedResult.getOrNull()!!
                    val playerCard = curCampaign.player.cardId?.let { storage.getCharacter(it) }

                    // Establish starting possessions and constraints from opening response (with player card fallback)
                    ContinuityManager.establishOpeningPossessions(
                        campaign = curCampaign,
                        startingPossessions = turnResp.startingPossessions,
                        startingConstraints = turnResp.startingConstraints,
                        fallbackCard = playerCard
                    )

                    // Apply any additional continuity updates from turnResp
                    if (turnResp.continuityUpdates.isNotEmpty()) {
                        ContinuityManager.applyContinuityUpdates(
                            campaign = curCampaign,
                            updates = turnResp.continuityUpdates,
                            turnNumber = 0,
                            story = turnResp.story
                        )
                    }

                    val openingTurn = TurnLog(
                        turnNumber = 0,
                        playerInput = "",
                        storyText = turnResp.story,
                        speech = turnResp.speech,
                        internalThoughts = turnResp.impressionUpdate,
                        actionSuggestions = turnResp.actionSuggestions,
                        speechSuggestions = turnResp.speechSuggestions,
                        consequenceNote = null,
                        timestamp = "Opening Scene",
                        trustDelta = turnResp.trustDelta,
                        dispositionDelta = turnResp.dispositionDelta,
                        timeSpentMinutes = turnResp.timeSpentMinutes,
                        continuityUpdates = turnResp.continuityUpdates
                    )

                    val openingImp = turnResp.impressionUpdate?.trim()
                    val isMeaningfulOpeningImp = !openingImp.isNullOrBlank() &&
                        !openingImp.equals("none", ignoreCase = true) &&
                        !openingImp.equals("n/a", ignoreCase = true) &&
                        !openingImp.equals("no change", ignoreCase = true) &&
                        !openingImp.equals("unchanged", ignoreCase = true)

                    val baseJournal = if (isMeaningfulOpeningImp) {
                        curCampaign.journal.copy(
                            currentImpression = openingImp!!,
                            impressionHistory = (curCampaign.journal.impressionHistory + openingImp).toMutableList()
                        )
                    } else curCampaign.journal

                    val seeds = curCampaign.seeds
                    val updatedCamp = if (seeds != null) {
                        // Seed-driven session: adopt the AI-authored hidden scenario sheet
                        val p = curCampaign.player
                        val newPlayer = p.copy(
                            name = p.name.ifBlank { turnResp.assignedPlayerName ?: "Stranger" },
                            role = p.role.ifBlank { turnResp.assignedPlayerRole ?: "" }
                        )
                        val isOpenEnded = ObjectiveManager.isOpenEnded(curCampaign)
                        val newObjective = if (isOpenEnded) {
                            curCampaign.objectiveRecord
                        } else {
                            ObjectiveManager.applyAuthoredObjective(
                                curCampaign.objectiveRecord,
                                turnResp.authoredObjective
                            )
                        }
                        val hs = turnResp.hiddenScenario
                        val parsedMinutes = RulesEngine.parseClockTime(turnResp.startTime)
                            ?: RulesEngine.estimateMinutesFromStory(curCampaign)
                        val newClock = parsedMinutes?.let { m ->
                            GameClock(startMinutes = m, currentMinutes = m, formattedTime = String.format("%02d:%02d", m / 60, m % 60))
                        } ?: curCampaign.clock
                        val location = turnResp.locationName?.trim()?.ifBlank { null } ?: curCampaign.settingName
                        val campTitle = if (isOpenEnded) {
                            "${curCampaign.npc.name}: Open-ended"
                        } else {
                            "${curCampaign.npc.name}: $location"
                        }
                        val openingSituation = turnResp.situation?.trim() ?: curCampaign.approvedPremise.trim()
                        val journal = baseJournal.copy(
                            startingPremise = openingSituation.ifBlank { curCampaign.approvedPremise.ifBlank { "The situation begins in $location." } },
                            situation = openingSituation,
                            playerRole = newPlayer.role.ifBlank { curCampaign.journal.playerRole },
                            relationshipDynamic = curCampaign.relationshipDynamic,
                            objective = if (isOpenEnded) ObjectiveManager.OPEN_ENDED_LABEL else newObjective.title
                        )
                        curCampaign.copy(
                            title = campTitle,
                            player = newPlayer,
                            settingName = location,
                            clock = newClock,
                            journal = journal,
                            objectiveRecord = newObjective,
                            hiddenScenario = hs,
                            turnHistory = mutableListOf(openingTurn)
                        )
                    } else {
                        val parsedM = RulesEngine.parseClockTime(turnResp.startTime)
                            ?: RulesEngine.estimateMinutesFromStory(curCampaign)
                        val newClock = if (parsedM != null && RulesEngine.isClockUnknown(curCampaign.clock)) {
                            GameClock(startMinutes = parsedM, currentMinutes = parsedM, formattedTime = String.format("%02d:%02d", parsedM / 60, parsedM % 60))
                        } else curCampaign.clock
                        val openingSituation = turnResp.situation?.trim() ?: curCampaign.approvedPremise.trim()
                        val legacyJournal = if (openingSituation.isNotBlank()) {
                            baseJournal.copy(
                                startingPremise = openingSituation.ifBlank { curCampaign.approvedPremise },
                                situation = openingSituation
                            )
                        } else baseJournal
                        curCampaign.copy(
                            clock = newClock,
                            journal = legacyJournal,
                            turnHistory = mutableListOf(openingTurn)
                        )
                    }
                    storage.saveCampaign(updatedCamp)
                    campaign = updatedCamp
                } else {
                    val ex = generatedResult.exceptionOrNull()
                    if (ex is ApiErrorDetail) {
                        errorDetail = ex
                        errorMessage = ex.summary
                    } else {
                        errorDetail = null
                        errorMessage = ex?.message ?: "Failed to generate opening scene."
                    }
                    isErrorExpanded = false
                }
                isLoading = false
            }
        }
    }

    // Scroll to bottom when new turns are added
    LaunchedEffect(curCampaign.turnHistory.size) {
        if (curCampaign.turnHistory.isNotEmpty()) {
            listState.animateScrollToItem(curCampaign.turnHistory.size - 1)
        }
    }

    // When choices open (action or speech), smoothly scroll to the end of the latest message
    LaunchedEffect(activeMenu) {
        if (activeMenu == "action" || activeMenu == "speech") {
            if (curCampaign.turnHistory.isNotEmpty()) {
                // Allow layout measure pass after panel opens
                kotlinx.coroutines.delay(60)
                listState.animateScrollToItem(curCampaign.turnHistory.size - 1)
            }
        }
    }

    // Execution routine for a player turn
    fun executePlayerTurn(playerText: String, actionIntent: String? = null) {
        if (playerText.isBlank() || isLoading) return
        val currentInput = playerText.trim()
        composerText = ""

        isLoading = true
        errorMessage = null

        coroutineScope.launch {
            val settings = storage.loadSettings()
            val characterCard = storage.getCharacter(curCampaign.npc.id)
            val currentTurnNumber = curCampaign.turnHistory.size

            val isKeyConfigured = if (settings.provider == "vertex") {
                settings.vertexApiKey.isNotBlank()
            } else {
                settings.openRouterApiKey.isNotBlank()
            }
            if (!isKeyConfigured) {
                val providerName = if (settings.provider == "vertex") "Google Vertex" else "OpenRouter"
                errorMessage = "$providerName API Key not configured. Please add your API key in Settings."
                composerText = currentInput
                isLoading = false
                return@launch
            }

            // Create rollback snapshot capturing state before this turn executes
            val snapshot = TurnRollbackSnapshot(
                clockMinutes = curCampaign.clock.currentMinutes,
                formattedTime = curCampaign.clock.formattedTime,
                trust = curCampaign.npc.trust,
                disposition = curCampaign.npc.disposition,
                facts = curCampaign.continuity.facts.map { it.copy() },
                currentImpression = curCampaign.journal.currentImpression,
                impressionHistory = curCampaign.journal.impressionHistory.toList(),
                objectiveRecord = curCampaign.objectiveRecord.copy(),
                notableEvents = curCampaign.journal.notableEvents.toList(),
                storyMemorySnapshot = curCampaign.storyMemory?.copy()
            )

            // Check and run context compression if request threshold is reached
            val compressionResult = ContextCompressionManager.checkAndCompressIfNeeded(
                campaign = curCampaign,
                character = characterCard,
                playerText = currentInput,
                actionIntent = actionIntent,
                settings = settings,
                openRouterService = openRouterService,
                storage = storage
            )

            if (compressionResult.isFailure) {
                val ex = compressionResult.exceptionOrNull()
                if (ex is ApiErrorDetail) {
                    errorDetail = ex
                    errorMessage = ex.summary
                } else {
                    errorDetail = null
                    errorMessage = ex?.message ?: "Context budget exceeded."
                }
                isErrorExpanded = false
                composerText = currentInput
                isLoading = false
                return@launch
            }

            val messages = PromptBuilder.assemblePromptMessages(
                scope = "continuation",
                campaign = curCampaign,
                character = characterCard,
                playerText = currentInput,
                actionIntent = actionIntent,
                compressionEnabled = settings.contextCompressionEnabled
            )

            val generatedResult = openRouterService.generateCompletion(
                settings = settings,
                messages = messages,
                diagnosticMetadata = com.example.beforethebell.network.DiagnosticMetadata(
                    scope = "continuation",
                    modules = curCampaign.activeModules.toList()
                )
            )

            if (generatedResult.isSuccess) {
                val turnResp = generatedResult.getOrNull()!!

                // Advance clock; if clock was unestablished/unknown, establish it now
                if (RulesEngine.isClockUnknown(curCampaign.clock)) {
                    val establishedMinutes = RulesEngine.parseClockTime(turnResp.startTime)
                        ?: RulesEngine.estimateMinutesFromStory(curCampaign)
                    if (establishedMinutes != null) {
                        curCampaign.clock = GameClock(
                            startMinutes = establishedMinutes,
                            currentMinutes = establishedMinutes,
                            formattedTime = String.format("%02d:%02d", establishedMinutes / 60, establishedMinutes % 60)
                        )
                    }
                }
                if (!RulesEngine.isClockUnknown(curCampaign.clock)) {
                    RulesEngine.advanceClock(curCampaign.clock, turnResp.timeSpentMinutes)
                }

                // Update NPC Trust and Disposition
                curCampaign.npc.trust = (curCampaign.npc.trust + turnResp.trustDelta).coerceIn(0, 10)
                curCampaign.npc.disposition = (curCampaign.npc.disposition + turnResp.dispositionDelta).coerceIn(-10, 10)

                // Apply continuity updates
                ContinuityManager.applyContinuityUpdates(
                    campaign = curCampaign,
                    updates = turnResp.continuityUpdates,
                    turnNumber = currentTurnNumber,
                    story = turnResp.story,
                    playerInput = currentInput
                )

                // Update Journal impression only when an interaction meaningfully changes it
                val newImpression = turnResp.impressionUpdate?.trim()
                if (!newImpression.isNullOrBlank() &&
                    !newImpression.equals("none", ignoreCase = true) &&
                    !newImpression.equals("n/a", ignoreCase = true) &&
                    !newImpression.equals("no change", ignoreCase = true) &&
                    !newImpression.equals("unchanged", ignoreCase = true) &&
                    newImpression != curCampaign.journal.currentImpression
                ) {
                    curCampaign.journal.currentImpression = newImpression
                    curCampaign.journal.impressionHistory.add(newImpression)
                }

                // Update Objective Record if model reports status
                ObjectiveManager.updateObjectiveFromTurn(
                    campaign = curCampaign,
                    objectiveStatus = turnResp.objectiveStatus,
                    resolutionReason = turnResp.objectiveResolutionReason
                )

                // Add to Journal -> Notable Events if a meaningful consequence/event occurred
                val note = turnResp.consequenceNote?.trim()
                val isNotable = !note.isNullOrBlank() &&
                    !note.equals("none", ignoreCase = true) &&
                    !note.equals("n/a", ignoreCase = true) &&
                    !note.equals("no change", ignoreCase = true) &&
                    !note.equals("unchanged", ignoreCase = true) &&
                    !note.equals("nothing changed", ignoreCase = true) &&
                    !note.equals("no consequences", ignoreCase = true)

                if (isNotable) {
                    curCampaign.journal.notableEvents.add(note!!)
                }

                val newTurnLog = TurnLog(
                    turnNumber = currentTurnNumber,
                    playerInput = currentInput,
                    playerIntent = actionIntent,
                    storyText = turnResp.story,
                    speech = turnResp.speech,
                    internalThoughts = turnResp.impressionUpdate,
                    actionSuggestions = turnResp.actionSuggestions,
                    speechSuggestions = turnResp.speechSuggestions,
                    consequenceNote = turnResp.consequenceNote,
                    journalUpdated = isNotable,
                    timestamp = curCampaign.clock.formattedTime,
                    trustDelta = turnResp.trustDelta,
                    dispositionDelta = turnResp.dispositionDelta,
                    timeSpentMinutes = turnResp.timeSpentMinutes,
                    continuityUpdates = turnResp.continuityUpdates,
                    rollbackSnapshot = snapshot
                )

                curCampaign.turnHistory.add(newTurnLog)

                // Check endings
                val activeEnding = RulesEngine.evaluateEndingPredicates(curCampaign)
                curCampaign.activeEnding = activeEnding?.first
                curCampaign.activeEndingText = activeEnding?.second

                curCampaign.draftInput = ""
                storage.saveCampaign(curCampaign)
                campaign = curCampaign.copy()
                activeMenu = null
            } else {
                val ex = generatedResult.exceptionOrNull()
                if (ex is ApiErrorDetail) {
                    errorDetail = ex
                    errorMessage = ex.summary
                } else {
                    errorDetail = null
                    errorMessage = ex?.message ?: "Turn generation failed."
                }
                isErrorExpanded = false
                composerText = currentInput
                curCampaign.draftInput = currentInput
                storage.saveCampaign(curCampaign)
            }
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlack)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. TOP HEADER (NPC NAME, ROLE, OPTIONAL TIME-OF-DAY, AND 3-DOT MENU)
            Surface(
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Back arrow to Home
                    IconButton(
                        onClick = {
                            curCampaign.draftInput = composerText
                            storage.saveCampaign(curCampaign)
                            onBackToHome()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Home", tint = AntiqueGold)
                    }

                    // NPC Name & Established Role
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = curCampaign.npc.name,
                            color = TextWarm,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )
                        val establishedRole = remember(curCampaign.npc.id, curCampaign.npc.title) {
                            curCampaign.npc.title.trim().ifBlank {
                                storage.getCharacter(curCampaign.npc.id)?.title?.trim() ?: ""
                            }
                        }
                        if (establishedRole.isNotBlank()) {
                            Text(
                                text = establishedRole,
                                color = TextMuted,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Serif
                            )
                        }
                    }

                    // In-world Clock (omitted if unknown; no countdown, no hardcoded 02:00)
                    if (!RulesEngine.isClockUnknown(curCampaign.clock)) {
                        Text(
                            text = curCampaign.clock.formattedTime,
                            color = AntiqueGold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    // Three-dot Overflow Menu
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = !showOverflowMenu },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = AntiqueGold)
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            modifier = Modifier.background(DarkSurface).border(1.dp, MetalBorder)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Journal", color = TextWarm, fontFamily = FontFamily.Serif) },
                                leadingIcon = { Icon(Icons.Default.Book, contentDescription = null, tint = AntiqueGold) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenJournal()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rewind Turn", color = TextWarm, fontFamily = FontFamily.Serif) },
                                leadingIcon = { Icon(Icons.Default.Undo, contentDescription = null, tint = AntiqueGold) },
                                onClick = {
                                    showOverflowMenu = false
                                    val success = RulesEngine.rewindTurn(curCampaign)
                                    if (success) {
                                        storage.saveCampaign(curCampaign)
                                        campaign = curCampaign.copy(
                                            turnHistory = curCampaign.turnHistory.toMutableList(),
                                            lastUpdated = System.currentTimeMillis()
                                        )
                                        Toast.makeText(context, "Turn rewound", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Cannot rewind further", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Leave Session", color = TextWarm, fontFamily = FontFamily.Serif) },
                                leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = AntiqueGold) },
                                onClick = {
                                    showOverflowMenu = false
                                    curCampaign.draftInput = composerText
                                    storage.saveCampaign(curCampaign)
                                    onBackToHome()
                                }
                            )
                        }
                    }
                }
            }

            // Error Notice Banner if any
            if (errorMessage != null) {
                Surface(
                    color = Color(0xFF381515),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF6A2222))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = Color(0xFFE89898),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (curCampaign.turnHistory.isEmpty() && !isLoading) {
                                TextButton(
                                    onClick = {
                                        errorMessage = null
                                        errorDetail = null
                                        isErrorExpanded = false
                                        openingGenerationTrigger++
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Retry", color = AntiqueGoldBright, fontSize = 12.sp)
                                }
                            }
                            if (errorDetail != null) {
                                TextButton(
                                    onClick = { isErrorExpanded = !isErrorExpanded },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isErrorExpanded) "Hide details" else "Details",
                                        color = AntiqueGold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Serif
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = if (isErrorExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isErrorExpanded) "Collapse error details" else "Expand error details",
                                        tint = AntiqueGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    errorMessage = null
                                    errorDetail = null
                                    isErrorExpanded = false
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFFE89898), modifier = Modifier.size(16.dp))
                            }
                        }

                        // Expandable technical diagnostic details
                        if (isErrorExpanded && errorDetail != null) {
                            val detail = errorDetail!!
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = Color(0xFF220D0D),
                                shape = RoundedCornerShape(2.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF5A1E1E)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(text = "Request Type:", color = AntiqueGoldDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(text = detail.requestType, color = TextWarm, fontSize = 11.sp)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(text = "Model ID:", color = AntiqueGoldDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(text = detail.modelId, color = TextWarm, fontSize = 11.sp)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(text = "Endpoint Region:", color = AntiqueGoldDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(text = detail.endpointRegion, color = TextWarm, fontSize = 11.sp)
                                    }
                                    if (detail.httpStatusCode != null) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(text = "HTTP Status:", color = AntiqueGoldDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(text = "${detail.httpStatusCode}", color = TextWarm, fontSize = 11.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = "Provider Error Message:", color = AntiqueGoldDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = detail.rawProviderError.ifBlank { "(No error message body returned by provider)" },
                                        color = Color(0xFFF2C2C2),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. CONVERSATION STORY AREA (LAZY COLUMN)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(
                    items = curCampaign.turnHistory,
                    key = { index, turn -> "${turn.turnNumber}_$index" }
                ) { index, turn ->
                    TurnLogItemView(
                        turn = turn,
                        playerName = curCampaign.player.name,
                        npcName = curCampaign.npc.name,
                        onBranchFromHere = {
                            val branchResult = storage.branchCampaignAtTurn(curCampaign, index)
                            branchResult.onSuccess { branched ->
                                campaign = branched
                                activeMenu = null
                                composerText = ""
                                Toast.makeText(context, "Branched into: ${branched.title}", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(context, err.message ?: "Failed to branch from turn.", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }

                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = AntiqueGold,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${curCampaign.npc.name} considers...",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }

                // Ending Resolution Card
                if (curCampaign.activeEnding != null) {
                    item {
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AntiqueGold),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "RESOLUTION: ${curCampaign.activeEnding}",
                                    color = AntiqueGoldBright,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                if (!curCampaign.activeEndingText.isNullOrBlank()) {
                                    FormattedStoryText(text = curCampaign.activeEndingText ?: "")
                                }
                            }
                        }
                    }
                }
            }

            // 3. ACTION / SPEECH / KEYBOARD CONTROLS & MENUS
            val latestTurn = curCampaign.turnHistory.lastOrNull()
            val actions = latestTurn?.actionSuggestions ?: emptyList()
            val speeches = latestTurn?.speechSuggestions ?: emptyList()

            if (curCampaign.activeEnding == null && !isLoading) {
                Surface(
                    color = DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Choices list panel (visible when activeMenu == "action" or "speech")
                        if (activeMenu == "action" || activeMenu == "speech") {
                            val isAction = activeMenu == "action"
                            val currentChoicesTitle = if (isAction) "ACTION SUGGESTIONS" else "SPEECH SUGGESTIONS"
                            val panelScrollState = rememberScrollState()

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .background(DarkSurfaceVariant)
                                    .border(1.dp, MetalBorder)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                // Panel subheader
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentChoicesTitle,
                                        color = AntiqueGold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    IconButton(
                                        onClick = { activeMenu = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Collapse",
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Scrollable choices list
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(panelScrollState),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isAction) {
                                        if (actions.isEmpty()) {
                                            Text(
                                                text = "No pre-set actions for this moment. Use keyboard to type a custom action.",
                                                color = TextMuted,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Serif,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        } else {
                                            actions.forEach { act ->
                                                val pName = curCampaign.player.name.ifBlank { "Player" }
                                                val rawProse = if (act.prose.isNotBlank()) act.prose.trim() else "$pName ${act.label.replaceFirstChar { it.lowercase() }}"
                                                val formattedAction = if (rawProse.startsWith("*") && rawProse.endsWith("*") && rawProse.length > 2) {
                                                    rawProse
                                                } else {
                                                    "*${rawProse.removeSurrounding("*").trim()}*"
                                                }
                                                val displayLabel = act.label

                                                Surface(
                                                    color = DarkSurface,
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { executePlayerTurn(playerText = formattedAction, actionIntent = act.label) }
                                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Text(
                                                            text = displayLabel,
                                                            color = TextWarm,
                                                            fontSize = 12.5.sp,
                                                            fontFamily = FontFamily.Serif,
                                                            lineHeight = 17.sp,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        if (speeches.isEmpty()) {
                                            Text(
                                                text = "No pre-set spoken lines for this moment. Use keyboard to type your words.",
                                                color = TextMuted,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Serif,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        } else {
                                            speeches.forEach { speechLine ->
                                                val trimmed = speechLine.trim()
                                                val formattedSpeech = if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                                                    (trimmed.startsWith("“") && trimmed.endsWith("”")) ||
                                                    (trimmed.startsWith("«") && trimmed.endsWith("»"))) {
                                                    trimmed
                                                } else {
                                                    "\"${trimmed.removeSurrounding("\"").removeSurrounding("“", "”").removeSurrounding("«", "»")}\""
                                                }
                                                val displaySpeech = formattedSpeech

                                                Surface(
                                                    color = DarkSurface,
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { executePlayerTurn(playerText = formattedSpeech) }
                                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Text(
                                                            text = displaySpeech,
                                                            color = DialogueText,
                                                            fontSize = 12.5.sp,
                                                            fontFamily = FontFamily.Serif,
                                                            lineHeight = 17.sp,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Freeform composer panel (when activeMenu == "keyboard")
                        if (activeMenu == "keyboard") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSurfaceVariant)
                                    .border(1.dp, MetalBorder)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "FREEFORM INPUT",
                                        color = AntiqueGold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    IconButton(
                                        onClick = { activeMenu = null },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close",
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = composerText,
                                        onValueChange = {
                                            composerText = it
                                            curCampaign.draftInput = it
                                        },
                                        placeholder = {
                                            Text(
                                                text = "Enter actions or spoken words...",
                                                color = TextMuted,
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Serif
                                            )
                                        },
                                        enabled = !isLoading,
                                        maxLines = 5,
                                        minLines = 2,
                                        shape = RoundedCornerShape(4.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = DarkSurface,
                                            unfocusedContainerColor = DarkSurface,
                                            focusedBorderColor = AntiqueGold,
                                            unfocusedBorderColor = MetalBorder,
                                            focusedTextColor = TextWarm,
                                            unfocusedTextColor = TextWarm,
                                            cursorColor = AntiqueGold
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = { executePlayerTurn(composerText) },
                                        enabled = !isLoading && composerText.isNotBlank(),
                                        modifier = Modifier
                                            .size(46.dp)
                                            .background(
                                                if (composerText.isNotBlank() && !isLoading) AntiqueGoldDark else DarkSurfaceVariant,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .border(1.dp, MetalBorder, RoundedCornerShape(4.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "Send",
                                            tint = if (composerText.isNotBlank() && !isLoading) AntiqueGoldBright else TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Persistent Controls Bar: Action, Speech, Keyboard Shortcut
                        // Always visible below choices or in idle state
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isActionActive = activeMenu == "action"
                            val isSpeechActive = activeMenu == "speech"
                            val isKeyboardActive = activeMenu == "keyboard"

                            // Action Button
                            Button(
                                onClick = {
                                    activeMenu = if (isActionActive) null else "action"
                                },
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isActionActive) AntiqueGoldDark else DarkSurfaceVariant,
                                    contentColor = if (isActionActive) NearBlack else TextWarm
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isActionActive) AntiqueGoldBright else AntiqueGold
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Text(
                                    text = "Action",
                                    fontSize = 13.5.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = if (isActionActive) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (isActionActive) NearBlack else AntiqueGold
                                )
                            }

                            // Speech Button
                            Button(
                                onClick = {
                                    activeMenu = if (isSpeechActive) null else "speech"
                                },
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSpeechActive) AntiqueGoldDark else DarkSurfaceVariant,
                                    contentColor = if (isSpeechActive) NearBlack else TextWarm
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSpeechActive) AntiqueGoldBright else AntiqueGold
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Text(
                                    text = "Speech",
                                    fontSize = 13.5.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = if (isSpeechActive) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (isSpeechActive) NearBlack else AntiqueGold
                                )
                            }

                            // Keyboard Shortcut Button
                            IconButton(
                                onClick = {
                                    activeMenu = if (isKeyboardActive) null else "keyboard"
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        if (isKeyboardActive) AntiqueGoldDark else DarkSurfaceVariant,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isKeyboardActive) AntiqueGoldBright else MetalBorder,
                                        RoundedCornerShape(4.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Keyboard,
                                    contentDescription = "Keyboard",
                                    tint = if (isKeyboardActive) NearBlack else AntiqueGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }


    }
}

@Composable
private fun TurnLogItemView(
    turn: TurnLog,
    playerName: String,
    npcName: String,
    onBranchFromHere: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Player Input (for turn > 0)
        if (turn.turnNumber > 0 && turn.playerInput.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = playerName.uppercase(),
                    color = AntiqueGoldDark,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                FormattedStoryText(text = turn.playerInput)
            }
        }

        // Story Response & Dialogue (Rendered directly on NearBlack background)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Small subtle scene / speaker label with Turn Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (turn.turnNumber == 0) "OPENING SCENE" else npcName.uppercase(),
                    color = AntiqueGoldDark,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )

                // Turn Options Menu
                var showTurnMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showTurnMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Turn Options",
                            tint = AntiqueGoldDark.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showTurnMenu,
                        onDismissRequest = { showTurnMenu = false },
                        modifier = Modifier
                            .background(DarkSurface)
                            .border(1.dp, MetalBorder)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Branch from here",
                                    color = TextWarm,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Serif
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AltRoute,
                                    contentDescription = null,
                                    tint = AntiqueGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showTurnMenu = false
                                onBranchFromHere()
                            }
                        )
                    }
                }
            }

            // Story Text
            FormattedStoryText(text = turn.storyText)

            // Subtle note beneath response when a notable entry occurred
            val eventNote = turn.consequenceNote?.trim()
            if (!eventNote.isNullOrBlank() && turn.journalUpdated) {
                Text(
                    text = eventNote,
                    color = TextMuted,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}



@Composable
private fun FormattedStoryText(text: String) {
    val paragraphs = remember(text) {
        text.split(Regex("\n{2,}"))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) continue

            // Build AnnotatedString to style quoted dialogue distinctly in warm amber-orange
            // while keeping narration ivory (TextWarm) and preserving markdown italics (*...* or _..._)
            val annotated = androidx.compose.ui.text.buildAnnotatedString {
                // Regex matches dialogue enclosed in straight quotes "...", curly quotes “...”, or Russian guillemets «...»
                // Note: single quotes / apostrophes are not matched
                val quoteRegex = Regex("""("[^"]*+"|“[^”]*+”|«[^»]*+»|["“«][^"”»]*+["”»])""")
                var lastIdx = 0
                val matches = quoteRegex.findAll(trimmed)

                // Helper lambda to append a substring with italics parsed (*...* or _..._)
                fun appendWithItalics(segment: String, baseColor: Color) {
                    val italicRegex = Regex("""(\*[^*]+\*|_[^_]+_)""")
                    var segIdx = 0
                    val italicMatches = italicRegex.findAll(segment)
                    for (im in italicMatches) {
                        if (im.range.first > segIdx) {
                            pushStyle(
                                androidx.compose.ui.text.SpanStyle(
                                    color = baseColor,
                                    fontStyle = FontStyle.Normal
                                )
                            )
                            append(segment.substring(segIdx, im.range.first))
                            pop()
                        }
                        // Content without delimiters
                        val innerText = im.value.substring(1, im.value.length - 1)
                        pushStyle(
                            androidx.compose.ui.text.SpanStyle(
                                color = baseColor,
                                fontStyle = FontStyle.Italic
                            )
                        )
                        append(innerText)
                        pop()
                        segIdx = im.range.last + 1
                    }
                    if (segIdx < segment.length) {
                        pushStyle(
                            androidx.compose.ui.text.SpanStyle(
                                color = baseColor,
                                fontStyle = FontStyle.Normal
                            )
                        )
                        append(segment.substring(segIdx))
                        pop()
                    }
                }

                for (match in matches) {
                    // Narration before dialogue (ivory / TextWarm)
                    if (match.range.first > lastIdx) {
                        val narrationSegment = trimmed.substring(lastIdx, match.range.first)
                        appendWithItalics(narrationSegment, TextWarm)
                    }

                    // Spoken dialogue in warm amber-orange (DialogueText #F0B656)
                    val dialogueSegment = match.value
                    appendWithItalics(dialogueSegment, DialogueText)

                    lastIdx = match.range.last + 1
                }

                // Remaining narration after last dialogue
                if (lastIdx < trimmed.length) {
                    val remainingNarration = trimmed.substring(lastIdx)
                    appendWithItalics(remainingNarration, TextWarm)
                }
            }

            Text(
                text = annotated,
                fontSize = 14.5.sp,
                fontFamily = FontFamily.Serif,
                lineHeight = 22.sp
            )
        }
    }
}
