package com.example.beforethebell.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beforethebell.data.CharacterCardParser
import com.example.beforethebell.data.DefaultData
import com.example.beforethebell.data.StorageManager
import com.example.beforethebell.engine.ContinuityManager
import com.example.beforethebell.engine.ObjectiveManager
import com.example.beforethebell.model.*
import com.example.beforethebell.theme.*
import com.example.beforethebell.ui.components.CharacterPortraitView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGameScreen(
    storage: StorageManager,
    onBack: () -> Unit,
    onStartCampaign: (Campaign) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var characters by remember { mutableStateOf(storage.listCharacters()) }
    var selectedCharacter by remember { mutableStateOf(characters.firstOrNull()) }

    // Character selector dropdown/modal state
    var showCharacterPicker by remember { mutableStateOf(false) }
    var isStartingCampaign by rememberSaveable { mutableStateOf(false) }

    // Player Persona Mode: "card" (default) or "custom"
    var playerPersonaMode by remember { mutableStateOf("card") }
    // Separate selected card for player character (default to first character or a different card if available)
    var selectedPlayerCard by remember {
        mutableStateOf(
            if (characters.size > 1 && characters[1].id != selectedCharacter?.id) characters[1]
            else characters.firstOrNull()
        )
    }
    var showPlayerCardPicker by remember { mutableStateOf(false) }

    // Player Persona (Manual / Custom inputs, preserved across switches)
    var playerName by remember { mutableStateOf("") }
    var playerGender by remember { mutableStateOf("unspecified") } // male, female, other, unspecified
    var playerBackground by remember { mutableStateOf("") }

    // Custom Role options
    val roleOptions = listOf("Independent traveler", "Diplomat", "Scholar", "Sellsword", "Merchant", "Custom…")
    var selectedRoleOption by remember { mutableStateOf("Independent traveler") }
    var customRoleText by remember { mutableStateOf("") }

    // 1. Genre / Mood Dropdown
    val genreOptions = listOf("Let the story decide", "Romance", "Mystery", "Thriller", "Adventure", "Drama", "Horror", "Comedy", "Slice of life", "Custom…")
    var selectedGenreOption by remember { mutableStateOf("Let the story decide") }
    var customGenreText by remember { mutableStateOf("") }

    // 2. Relationship Dropdown
    val relationshipOptions = listOf("Let the story decide", "Strangers", "Friends", "Allies", "Rivals", "Captor & captive", "Custom…")
    var selectedRelationshipOption by remember { mutableStateOf("Let the story decide") }
    var customRelationshipText by remember { mutableStateOf("") }
    var captiveWhoIsWho by remember { mutableStateOf("npc_captor") } // "npc_captor" or "player_captor"

    // 3. Objective Dropdown
    val objectiveOptions = listOf("None — Open-ended", "Let the story decide", "Discover the truth", "Win cooperation", "Survive", "Escape", "Protect someone", "Build a connection", "Custom…")
    var selectedObjectiveOption by remember { mutableStateOf("Let the story decide") }
    var customObjectiveText by remember { mutableStateOf("") }

    // 4. Setting / Place Dropdown
    val settingOptions = listOf("Let the story decide", "From character card lore", "Custom…")
    var selectedSettingOption by remember { mutableStateOf("Let the story decide") }
    var customSettingText by remember { mutableStateOf("") }

    // Collapsed Optional Direction Field (Preferences & Exclusions)
    var optionalDirectionsExpanded by remember { mutableStateOf(false) }
    var optionalDirectionsText by remember { mutableStateOf("") }

    // Expandable Advanced Controls
    var advancedExpanded by remember { mutableStateOf(false) }
    var consequences by remember { mutableFloatStateOf(50f) }
    var npcInitiative by remember { mutableFloatStateOf(50f) }
    var pacing by remember { mutableFloatStateOf(50f) }
    var dialogueActionRatio by remember { mutableFloatStateOf(50f) }
    var mystery by remember { mutableFloatStateOf(50f) }
    var twists by remember { mutableFloatStateOf(50f) }

    // File Import launcher for Character Cards (NPC)
    val cardImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val bytes = inputStream.readBytes()
                    val fileName = uri.lastPathSegment ?: "card"
                    val parsedCard = try {
                        if (fileName.endsWith(".png", ignoreCase = true) || bytes.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) {
                            CharacterCardParser.parsePng(bytes)
                        } else {
                            CharacterCardParser.parseJson(String(bytes, Charsets.UTF_8))
                        }
                    } catch (e: Exception) {
                        null
                    }
                    if (parsedCard != null) {
                        storage.saveCharacter(parsedCard)
                        characters = storage.listCharacters()
                        selectedCharacter = parsedCard
                    }
                }
            } catch (e: Exception) {
                // Ignore parse errors gracefully
            }
        }
    }

    // File Import launcher for Player Persona Character Cards
    val playerCardImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val bytes = inputStream.readBytes()
                    val fileName = uri.lastPathSegment ?: "card"
                    val parsedCard = try {
                        if (fileName.endsWith(".png", ignoreCase = true) || bytes.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) {
                            CharacterCardParser.parsePng(bytes)
                        } else {
                            CharacterCardParser.parseJson(String(bytes, Charsets.UTF_8))
                        }
                    } catch (e: Exception) {
                        null
                    }
                    if (parsedCard != null) {
                        storage.saveCharacter(parsedCard)
                        characters = storage.listCharacters()
                        selectedPlayerCard = parsedCard
                    }
                }
            } catch (e: Exception) {
                // Ignore parse errors gracefully
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlack)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = AntiqueGold
                    )
                }
                Text(
                    text = "NEW SCENARIO",
                    color = AntiqueGold,
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.size(48.dp)) // balance layout
            }

            HorizontalDivider(color = MetalBorder, thickness = 1.dp)

            // 1. CHARACTER SECTION
            SectionLabel("CHARACTER")

            val curCard = selectedCharacter
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CharacterPortraitView(
                        name = curCard?.name ?: "Unknown",
                        avatarBase64 = curCard?.avatarBase64,
                        size = 52.dp
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = curCard?.name ?: "No Character Selected",
                            color = TextWarm,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = curCard?.title?.ifBlank { "Subject" } ?: "",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(onClick = { showCharacterPicker = true }) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Change Character",
                                tint = AntiqueGold
                            )
                        }
                        IconButton(onClick = { cardImportLauncher.launch("*/*") }) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = "Import Character",
                                tint = AntiqueGoldBright
                            )
                        }
                    }
                }
            }

            // 2. PLAYER PERSONA
            SectionLabel("PLAYER PERSONA")

            // Mode Selector: Character Card (default), Custom, or Let Story Decide
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val modes = listOf(
                    "card" to "Character Card",
                    "custom" to "Custom…",
                    "story" to "Let Story Decide"
                )
                modes.forEach { (mode, label) ->
                    val isSel = playerPersonaMode == mode
                    OutlinedButton(
                        onClick = { playerPersonaMode = mode },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSel) DarkSurfaceVariant else DarkSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSel) AntiqueGold else MetalBorder
                        ),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) AntiqueGold else TextWarm,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }

            when (playerPersonaMode) {
                "card" -> {
                    // Compact Player Character Card preview
                    val pCard = selectedPlayerCard
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CharacterPortraitView(
                                name = pCard?.name ?: "Player",
                                avatarBase64 = pCard?.avatarBase64,
                                size = 52.dp
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pCard?.name ?: "No Card Selected",
                                    color = TextWarm,
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = pCard?.title?.ifBlank { "Player Character" } ?: "Player Character",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Serif
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = { showPlayerCardPicker = true }) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Change Player Card",
                                        tint = AntiqueGold
                                    )
                                }
                                IconButton(onClick = { playerCardImportLauncher.launch("*/*") }) {
                                    Icon(
                                        imageVector = Icons.Default.FileOpen,
                                        contentDescription = "Import Player Card",
                                        tint = AntiqueGoldBright
                                    )
                                }
                            }
                        }
                    }
                }
                "custom" -> {
                    // Custom manual persona fields (Preserved across switches)
                    MedievalTextField(
                        label = "Name",
                        value = playerName,
                        placeholder = "The Protagonist",
                        onValueChange = { playerName = it }
                    )

                    // Gender selector: Male / Female / Other / Unspecified
                    Text(text = "Gender / Presentation", color = TextMuted, fontSize = 11.5.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "unspecified" to "Neutral",
                            "male" to "Male",
                            "female" to "Female",
                            "other" to "Other"
                        ).forEach { (id, label) ->
                            val isSel = playerGender == id
                            OutlinedButton(
                                onClick = { playerGender = id },
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) DarkSurfaceVariant else DarkSurface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSel) AntiqueGold else MetalBorder
                                ),
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) AntiqueGold else TextWarm,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // Player Role Dropdown with Custom... reveal
                    CompactDropdownSelector(
                        label = "Role / Persona",
                        options = roleOptions,
                        selectedOption = selectedRoleOption,
                        onOptionSelected = { selectedRoleOption = it }
                    )
                    if (selectedRoleOption == "Custom…") {
                        MedievalTextField(
                            label = "Custom Role",
                            value = customRoleText,
                            placeholder = "e.g. Wandering sellsword, Inquisitorial clerk",
                            onValueChange = { customRoleText = it }
                        )
                    }

                    MedievalTextField(
                        label = "Background / Notes (Optional)",
                        value = playerBackground,
                        placeholder = "Personal background, concealed marks, or history...",
                        onValueChange = { playerBackground = it },
                        singleLine = false,
                        minLines = 2
                    )
                }
                "story" -> {
                    Surface(
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "The AI will introduce your identity and calling naturally in the opening scene. You retain full control over your voluntary actions and dialogue.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // 3. SCENARIO SETUP
            SectionLabel("SCENARIO SETUP")

            // Genre / Mood
            CompactDropdownSelector(
                label = "Genre / Mood",
                options = genreOptions,
                selectedOption = selectedGenreOption,
                onOptionSelected = { selectedGenreOption = it }
            )
            if (selectedGenreOption == "Custom…") {
                MedievalTextField(
                    label = "Custom Genre / Mood",
                    value = customGenreText,
                    placeholder = "e.g. Gothic horror, political intrigue, courtroom drama",
                    onValueChange = { customGenreText = it }
                )
            }

            // Relationship
            CompactDropdownSelector(
                label = "Relationship",
                options = relationshipOptions,
                selectedOption = selectedRelationshipOption,
                onOptionSelected = { selectedRelationshipOption = it }
            )
            if (selectedRelationshipOption == "Captor & captive") {
                val effectiveCharName = curCard?.name?.split(" ")?.firstOrNull() ?: "NPC"
                val effectivePlayerName = if (playerPersonaMode == "card") {
                    selectedPlayerCard?.name?.split(" ")?.firstOrNull() ?: "Player"
                } else if (playerName.isNotBlank()) {
                    playerName.trim()
                } else {
                    "Player"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isNpcCaptor = captiveWhoIsWho == "npc_captor"
                    OutlinedButton(
                        onClick = { captiveWhoIsWho = "npc_captor" },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isNpcCaptor) DarkSurfaceVariant else DarkSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isNpcCaptor) AntiqueGold else MetalBorder
                        ),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "$effectiveCharName is Captor",
                            color = if (isNpcCaptor) AntiqueGold else TextWarm,
                            fontSize = 11.5.sp,
                            fontWeight = if (isNpcCaptor) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    val isPlayerCaptor = captiveWhoIsWho == "player_captor"
                    OutlinedButton(
                        onClick = { captiveWhoIsWho = "player_captor" },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isPlayerCaptor) DarkSurfaceVariant else DarkSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isPlayerCaptor) AntiqueGold else MetalBorder
                        ),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "$effectivePlayerName is Captor",
                            color = if (isPlayerCaptor) AntiqueGold else TextWarm,
                            fontSize = 11.5.sp,
                            fontWeight = if (isPlayerCaptor) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            } else if (selectedRelationshipOption == "Custom…") {
                MedievalTextField(
                    label = "Custom Relationship Dynamic",
                    value = customRelationshipText,
                    placeholder = "Describe relationship dynamic...",
                    onValueChange = { customRelationshipText = it }
                )
            }

            // Objective
            CompactDropdownSelector(
                label = "Objective",
                options = objectiveOptions,
                selectedOption = selectedObjectiveOption,
                onOptionSelected = { selectedObjectiveOption = it }
            )
            if (selectedObjectiveOption == "Custom…") {
                MedievalTextField(
                    label = "Custom Objective",
                    value = customObjectiveText,
                    placeholder = "Describe custom objective...",
                    onValueChange = { customObjectiveText = it }
                )
            }

            // Setting / Place
            CompactDropdownSelector(
                label = "Setting / Place",
                options = settingOptions,
                selectedOption = selectedSettingOption,
                onOptionSelected = { selectedSettingOption = it }
            )
            if (selectedSettingOption == "Custom…") {
                MedievalTextField(
                    label = "Custom Setting",
                    value = customSettingText,
                    placeholder = "e.g. Ruined watchtower during a blizzard",
                    onValueChange = { customSettingText = it }
                )
            }

            // Collapsed Optional Direction Field (Preferences & Exclusions)
            Surface(
                onClick = { optionalDirectionsExpanded = !optionalDirectionsExpanded },
                color = DarkSurface,
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Additional Directions (Optional)",
                        color = TextWarm,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif
                    )
                    Icon(
                        imageVector = if (optionalDirectionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = AntiqueGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = optionalDirectionsExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant, RoundedCornerShape(4.dp))
                        .border(1.dp, MetalBorder, RoundedCornerShape(4.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MedievalTextField(
                        label = "Preferences & Exclusions",
                        value = optionalDirectionsText,
                        placeholder = "Preferences guide generation flexibly; explicit exclusions are strictly respected...",
                        onValueChange = { optionalDirectionsText = it },
                        singleLine = false,
                        minLines = 3
                    )
                }
            }

            // 6. NARRATIVE COCKPIT CONTROLS (EXPANDABLE)
            Surface(
                onClick = { advancedExpanded = !advancedExpanded },
                color = DarkSurface,
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Narrative Cockpit Controls",
                        color = AntiqueGold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif
                    )
                    Icon(
                        imageVector = if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = AntiqueGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = advancedExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant, RoundedCornerShape(4.dp))
                        .border(1.dp, MetalBorder, RoundedCornerShape(4.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CockpitSlider(
                        label = "NPC Initiative",
                        value = npcInitiative,
                        onValueChange = { npcInitiative = it },
                        leftLabel = "Reactive",
                        rightLabel = "Proactive"
                    )
                    CockpitSlider(
                        label = "Twists",
                        value = twists,
                        onValueChange = { twists = it },
                        leftLabel = "Steady",
                        rightLabel = "Eventful"
                    )
                    CockpitSlider(
                        label = "Consequences",
                        value = consequences,
                        onValueChange = { consequences = it },
                        leftLabel = "Gentle",
                        rightLabel = "Severe"
                    )
                    CockpitSlider(
                        label = "Dialogue Ratio",
                        value = dialogueActionRatio,
                        onValueChange = { dialogueActionRatio = it },
                        leftLabel = "Narration",
                        rightLabel = "Dialogue"
                    )
                    CockpitSlider(
                        label = "Scene Pace",
                        value = pacing,
                        onValueChange = { pacing = it },
                        leftLabel = "Linger",
                        rightLabel = "Advance"
                    )
                    CockpitSlider(
                        label = "Mystery",
                        value = mystery,
                        onValueChange = { mystery = it },
                        leftLabel = "Clear",
                        rightLabel = "Enigmatic"
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 7. PLAY BUTTON
            Button(
                onClick = {
                    if (isStartingCampaign) return@Button
                    val card = selectedCharacter ?: return@Button
                    isStartingCampaign = true
                    val activePreset = storage.getActivePreset()

                    // Resolve seeds
                    val effectiveGenre = if (selectedGenreOption == "Custom…") {
                        customGenreText.trim()
                    } else if (selectedGenreOption == "Let the story decide") {
                        ""
                    } else {
                        selectedGenreOption
                    }

                    val effectiveRelationship = if (selectedRelationshipOption == "Custom…") {
                        customRelationshipText.trim()
                    } else if (selectedRelationshipOption == "Let the story decide") {
                        ""
                    } else {
                        selectedRelationshipOption
                    }

                    val effectiveDirectionalRole = if (selectedRelationshipOption == "Captor & captive") {
                        captiveWhoIsWho
                    } else {
                        ""
                    }

                    val effectiveObjective = if (selectedObjectiveOption == "Custom…") {
                        customObjectiveText.trim()
                    } else if (selectedObjectiveOption == "Let the story decide") {
                        ""
                    } else {
                        selectedObjectiveOption
                    }

                    val isOpenEnded = ObjectiveManager.isOpenEnded(effectiveObjective)

                    val effectiveSetting = if (selectedSettingOption == "Custom…") {
                        customSettingText.trim()
                    } else if (selectedSettingOption == "From character card lore") {
                        "From character card lore"
                    } else if (selectedSettingOption == "Let the story decide") {
                        ""
                    } else {
                        selectedSettingOption
                    }

                    val seeds = ScenarioSeeds(
                        genre = effectiveGenre,
                        relationship = effectiveRelationship,
                        directionalRole = effectiveDirectionalRole,
                        objective = effectiveObjective,
                        setting = effectiveSetting,
                        directions = optionalDirectionsText.trim()
                    )

                    val initialObjectiveTitle = if (isOpenEnded) ObjectiveManager.OPEN_ENDED_LABEL else effectiveObjective.ifBlank { "Discover the truth" }
                    val initialObjRecord = if (isOpenEnded) {
                        ObjectiveManager.createOpenEndedObjectiveRecord()
                    } else {
                        ObjectiveManager.ensureObjectiveRecord(
                            objective = initialObjectiveTitle,
                            premise = "",
                            titleOverride = initialObjectiveTitle
                        )
                    }

                    // Resolve Player Persona
                    val playerPersona = when (playerPersonaMode) {
                        "card" -> {
                            val pCard = selectedPlayerCard
                            if (pCard != null) {
                                PlayerPersona(
                                    name = pCard.name.trim().ifBlank { "The Protagonist" },
                                    gender = playerGender,
                                    role = pCard.title.ifBlank { "Independent actor" },
                                    background = pCard.creatorNotes.trim(),
                                    source = "card",
                                    cardId = pCard.id,
                                    title = pCard.title,
                                    description = pCard.description,
                                    personality = pCard.personality,
                                    mesExample = pCard.mesExample,
                                    avatarBase64 = pCard.avatarBase64
                                )
                            } else {
                                val role = if (selectedRoleOption == "Custom…") customRoleText.trim().ifBlank { "Independent actor" } else selectedRoleOption
                                PlayerPersona(
                                    name = playerName.trim().ifBlank { "The Protagonist" },
                                    gender = playerGender,
                                    role = role,
                                    background = playerBackground.trim(),
                                    source = "custom"
                                )
                            }
                        }
                        "custom" -> {
                            val role = if (selectedRoleOption == "Custom…") customRoleText.trim().ifBlank { "Independent actor" } else selectedRoleOption
                            PlayerPersona(
                                name = playerName.trim().ifBlank { "The Protagonist" },
                                gender = playerGender,
                                role = role,
                                background = playerBackground.trim(),
                                source = "custom"
                            )
                        }
                        else -> { // "story"
                            PlayerPersona(
                                name = "",
                                gender = "unspecified",
                                role = "",
                                background = "",
                                source = "story"
                            )
                        }
                    }

                    val settingLabel = if (effectiveSetting.isNotBlank()) effectiveSetting else "Setting to be discovered"
                    val relationshipLabel = if (effectiveRelationship.isNotBlank()) {
                        if (effectiveRelationship == "Captor & captive") {
                            if (captiveWhoIsWho == "player_captor") "Captor & captive (player is captor)"
                            else "Captor & captive (NPC is captor)"
                        } else effectiveRelationship
                    } else "To be discovered"

                    val campaignTitle = if (isOpenEnded) "${card.name}: Open-ended" else "${card.name}: $initialObjectiveTitle"
                    val newCampaign = Campaign(
                        campaignId = "camp_${System.currentTimeMillis()}",
                        title = campaignTitle,
                        inputMode = "action_speech",
                        approvedPremise = "",
                        objective = initialObjectiveTitle,
                        settingName = settingLabel,
                        relationshipDynamic = relationshipLabel,
                        cockpit = ScenarioCockpit(
                            npcInitiative = npcInitiative.toInt(),
                            twists = twists.toInt(),
                            consequences = consequences.toInt(),
                            dialogueActionRatio = dialogueActionRatio.toInt(),
                            pacing = pacing.toInt(),
                            mystery = mystery.toInt()
                        ),
                        player = playerPersona,
                        npc = NpcState(
                            id = card.id,
                            name = card.name,
                            title = card.title,
                            portraitUrl = card.avatarBase64,
                            personality = card.personality,
                            disposition = 0,
                            trust = 3
                        ),
                        clock = GameClock(startMinutes = 0, currentMinutes = 0, formattedTime = ""),
                        continuity = ContinuityRecord(),
                        journal = JournalState(
                            startingPremise = "",
                            situation = "",
                            playerRole = playerPersona.role,
                            relationshipDynamic = relationshipLabel,
                            objective = initialObjectiveTitle,
                            currentImpression = "",
                            impressionHistory = mutableListOf()
                        ),
                        objectiveRecord = initialObjRecord,
                        activeModules = (activePreset?.modules ?: DefaultData.PRESET_BEFORE_THE_BELL.modules)
                            .map { it.copy() }.toMutableList(),
                        seeds = seeds,
                        hiddenScenario = null
                    )

                    ContinuityManager.seedStartingPossessions(newCampaign, selectedPlayerCard)
                    storage.saveCampaign(newCampaign)
                    onStartCampaign(newCampaign)
                },
                enabled = selectedCharacter != null && !isStartingCampaign,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AntiqueGoldDark,
                    contentColor = TextWarm
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, AntiqueGold, RoundedCornerShape(4.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = AntiqueGoldBright,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PLAY",
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Modal / Dialog for Character Selection & Deletion
    if (showCharacterPicker) {
        AlertDialog(
            onDismissRequest = { showCharacterPicker = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "SELECT CHARACTER",
                    color = AntiqueGold,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (c in characters) {
                        val isSel = c.id == selectedCharacter?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSel) DarkSurfaceVariant else NearBlack,
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSel) AntiqueGold else MetalBorder,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    selectedCharacter = c
                                    showCharacterPicker = false
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CharacterPortraitView(
                                name = c.name,
                                avatarBase64 = c.avatarBase64,
                                size = 42.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = c.name,
                                    color = if (isSel) AntiqueGold else TextWarm,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = c.title.ifBlank { "Subject" },
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            if (characters.size > 1) {
                                IconButton(
                                    onClick = {
                                        storage.deleteCharacter(c.id)
                                        characters = storage.listCharacters()
                                        if (selectedCharacter?.id == c.id) {
                                            selectedCharacter = characters.firstOrNull()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Character",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCharacterPicker = false }) {
                    Text("Close", color = AntiqueGold)
                }
            }
        )
    }

    // Modal / Dialog for Player Persona Character Selection
    if (showPlayerCardPicker) {
        AlertDialog(
            onDismissRequest = { showPlayerCardPicker = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "SELECT PLAYER PERSONA",
                    color = AntiqueGold,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (c in characters) {
                        val isSel = c.id == selectedPlayerCard?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSel) DarkSurfaceVariant else NearBlack,
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSel) AntiqueGold else MetalBorder,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    selectedPlayerCard = c
                                    showPlayerCardPicker = false
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CharacterPortraitView(
                                name = c.name,
                                avatarBase64 = c.avatarBase64,
                                size = 42.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = c.name,
                                    color = if (isSel) AntiqueGold else TextWarm,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = c.title.ifBlank { "Player Character" },
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlayerCardPicker = false }) {
                    Text("Close", color = AntiqueGold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPlayerCardPicker = false
                    playerCardImportLauncher.launch("*/*")
                }) {
                    Text("Import Card", color = AntiqueGoldBright)
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        color = AntiqueGoldDark,
        fontSize = 12.sp,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun MedievalTextField(
    label: String,
    value: String,
    placeholder: String = "",
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Serif,
            modifier = Modifier.padding(bottom = 3.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = if (placeholder.isNotBlank()) {
                { Text(placeholder, color = TextDim, fontSize = 13.sp, fontFamily = FontFamily.Serif) }
            } else null,
            singleLine = singleLine,
            minLines = minLines,
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
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactDropdownSelector(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Serif,
            modifier = Modifier.padding(bottom = 3.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedOption,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
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
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DarkSurface).border(1.dp, MetalBorder)
            ) {
                options.forEach { option ->
                    val isSelected = option == selectedOption
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                color = if (isSelected) AntiqueGold else TextWarm,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CockpitSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    leftLabel: String,
    rightLabel: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = TextWarm, fontSize = 12.sp, fontFamily = FontFamily.Serif)
            Text(text = "${value.toInt()}%", color = AntiqueGold, fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = AntiqueGold,
                activeTrackColor = AntiqueGoldDark,
                inactiveTrackColor = MetalBorder
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = leftLabel, color = TextMuted, fontSize = 10.sp)
            Text(text = rightLabel, color = TextMuted, fontSize = 10.sp)
        }
    }
}
