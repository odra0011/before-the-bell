package com.example.beforethebell.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beforethebell.engine.RulesEngine
import com.example.beforethebell.model.Campaign
import com.example.beforethebell.theme.*

/**
 * Extracts in-world established story time (e.g. Dawn, Morning, Afternoon, Dusk, Night)
 * from continuity facts, setting, or recent narrative.
 * Returns null if unknown, omitting any hardcoded timer/clock.
 */
fun extractEstablishedStoryTime(campaign: Campaign): String? {
    val keywords = listOf(
        "early morning" to "Early morning",
        "late morning" to "Morning",
        "morning" to "Morning",
        "dawn" to "Dawn",
        "daybreak" to "Daybreak",
        "sunrise" to "Sunrise",
        "midday" to "Midday",
        "noon" to "Noon",
        "afternoon" to "Afternoon",
        "late afternoon" to "Late afternoon",
        "twilight" to "Twilight",
        "dusk" to "Dusk",
        "sunset" to "Sunset",
        "evening" to "Evening",
        "midnight" to "Midnight",
        "dead of night" to "Night",
        "night" to "Night"
    )

    // 1. Check active continuity facts
    for (fact in campaign.continuity.facts.filter { it.active }) {
        val lowerText = fact.text.lowercase()
        for ((pattern, label) in keywords) {
            val wordBoundary = Regex("""\b${Regex.escape(pattern)}\b""")
            if (wordBoundary.containsMatchIn(lowerText)) {
                return label
            }
        }
    }

    // 2. Check setting name
    val lowerSetting = campaign.settingName.lowercase()
    for ((pattern, label) in keywords) {
        val wordBoundary = Regex("""\b${Regex.escape(pattern)}\b""")
        if (wordBoundary.containsMatchIn(lowerSetting)) {
            return label
        }
    }

    // 3. Check latest turn story text
    val latestStory = campaign.turnHistory.lastOrNull()?.storyText?.lowercase()
    if (!latestStory.isNullOrBlank()) {
        for ((pattern, label) in keywords) {
            val wordBoundary = Regex("""\b${Regex.escape(pattern)}\b""")
            if (wordBoundary.containsMatchIn(latestStory)) {
                return label
            }
        }
    }

    return null
}

@Composable
fun JournalPanel(
    campaign: Campaign,
    onBackToStory: () -> Unit,
    onToggleShowPrivate: ((Boolean) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val npc = campaign.npc

    // Established story time (in-world; matches header clock, omitted if unknown)
    val establishedTime = if (!RulesEngine.isClockUnknown(campaign.clock)) {
        campaign.clock.formattedTime
    } else {
        extractEstablishedStoryTime(campaign)
    }

    // Established location (grounded and restrained; omitted if unestablished)
    val rawSetting = campaign.settingName.trim()
    val establishedLocation = if (rawSetting.isNotBlank() &&
        !rawSetting.equals("Setting to be discovered", ignoreCase = true) &&
        !rawSetting.equals("To be discovered", ignoreCase = true)
    ) {
        rawSetting
    } else {
        campaign.continuity.facts.firstOrNull { it.active && it.category.equals("location", ignoreCase = true) }?.text?.trim()
    }

    val knownRole = npc.title.trim()
    val impression = campaign.journal.currentImpression.trim()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ParchmentBg)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Bar with Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackToStory) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Story",
                        tint = ParchmentTextMuted
                    )
                }

                Text(
                    text = "JOURNAL",
                    color = ParchmentText,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.size(48.dp))
            }

            HorizontalDivider(color = ParchmentBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 0: SITUATION (compact starting circumstances the player character knows)
                val situationText = when {
                    campaign.journal.situation.isNotBlank() -> campaign.journal.situation.trim()
                    campaign.approvedPremise.isNotBlank() -> campaign.approvedPremise.trim()
                    campaign.journal.startingPremise.isNotBlank() &&
                        !campaign.journal.startingPremise.startsWith("The situation begins in ") -> campaign.journal.startingPremise.trim()
                    else -> null
                }

                if (!situationText.isNullOrBlank()) {
                    ParchmentCard(title = "SITUATION") {
                        Text(
                            text = situationText,
                            color = ParchmentText,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Serif,
                            lineHeight = 18.5.sp
                        )
                    }
                }

                // Section 1: HERE (current story time and location, when established)
                if (establishedLocation != null || establishedTime != null) {
                    ParchmentCard(title = "HERE") {
                        if (establishedLocation != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Location:",
                                    color = ParchmentTextMuted,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Serif
                                )
                                Text(
                                    text = establishedLocation,
                                    color = ParchmentText,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        if (establishedLocation != null && establishedTime != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        if (establishedTime != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Time:",
                                    color = ParchmentTextMuted,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Serif
                                )
                                Text(
                                    text = establishedTime,
                                    color = ParchmentText,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Section 2: CHARACTER (name, known role/position, and brief impression of player)
                ParchmentCard(title = "CHARACTER") {
                    Text(
                        text = npc.name,
                        color = ParchmentText,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (knownRole.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = knownRole,
                            color = ParchmentTextMuted,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }
                    if (impression.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "“$impression”",
                            color = ParchmentText,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Serif,
                            lineHeight = 19.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }

                // Section 3: NOTABLE EVENTS (discovered clues, significant item transfers, promises, meaningful relationship developments)
                if (campaign.journal.notableEvents.isNotEmpty()) {
                    ParchmentCard(title = "NOTABLE EVENTS") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (event in campaign.journal.notableEvents) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "•",
                                        color = AntiqueGoldDark,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = event,
                                        color = ParchmentText,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Serif,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun FactsPanel(
    campaign: Campaign,
    onBackToStory: () -> Unit
) {
    val scrollState = rememberScrollState()
    val continuity = campaign.continuity

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ParchmentBg)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Bar with Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackToStory) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Story",
                        tint = ParchmentTextMuted
                    )
                }

                Text(
                    text = "ESTABLISHED FACTS",
                    color = ParchmentText,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.size(48.dp))
            }

            HorizontalDivider(color = ParchmentBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // Scrollable Facts List
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val allFacts = continuity.facts.filter { it.active }
                if (allFacts.isEmpty()) {
                    ParchmentCard(title = "CONTINUITY RECORD") {
                        Text(
                            text = "No immutable conditions or possessions recorded yet. Progress turns to observe consequences.",
                            color = ParchmentTextMuted,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }
                } else {
                    val conditions = allFacts.filter { it.category == "condition" || it.category == "capability" }
                    if (conditions.isNotEmpty()) {
                        ParchmentCard(title = "PHYSICAL STATE & CONDITIONS") {
                            conditions.forEach { fact ->
                                FactEntryView(fact.text, fact.establishedTurn)
                            }
                        }
                    }

                    val inventory = allFacts.filter { it.category == "possession" }
                    if (inventory.isNotEmpty()) {
                        ParchmentCard(title = "POSSESSIONS & INVENTORY") {
                            inventory.forEach { fact ->
                                FactEntryView(fact.text, fact.establishedTurn)
                            }
                        }
                    }

                    val discoveries = allFacts.filter { it.category == "discovery" || it.category == "commitment" }
                    if (discoveries.isNotEmpty()) {
                        ParchmentCard(title = "REVEALED TRUTHS & SECRETS") {
                            discoveries.forEach { fact ->
                                FactEntryView(fact.text, fact.establishedTurn)
                            }
                        }
                    }

                    val environment = allFacts.filter { it.category == "location" || it.category == "restraint" }
                    if (environment.isNotEmpty()) {
                        ParchmentCard(title = "SURROUNDINGS & RESTRAINTS") {
                            environment.forEach { fact ->
                                FactEntryView(fact.text, fact.establishedTurn)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun FactEntryView(fact: String, turn: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            color = AntiqueGoldDark,
            fontSize = 14.sp
        )
        Text(
            text = fact,
            color = ParchmentText,
            fontSize = 13.sp,
            fontFamily = FontFamily.Serif,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
        if (turn > 0) {
            Text(
                text = "T$turn",
                color = ParchmentTextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Serif
            )
        }
    }
}

@Composable
private fun ParchmentCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ParchmentBorder, RoundedCornerShape(4.dp))
            .background(DarkSurfaceVariant, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Text(
            text = title,
            color = AntiqueGoldDark,
            fontSize = 11.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        content()
    }
}
