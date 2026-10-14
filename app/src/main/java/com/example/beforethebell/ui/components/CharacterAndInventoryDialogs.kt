package com.example.beforethebell.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.beforethebell.model.Campaign
import com.example.beforethebell.model.CharacterCard
import com.example.beforethebell.theme.*

/**
 * Readable Character Sheet dialog displaying the NPC's imported card details.
 * Strictly presents selected character card information (appearance, personality, lore),
 * NEVER exposing hidden scenarios, secret agendas, or undiscovered twists.
 */
@Composable
fun CharacterSheetDialog(
    character: CharacterCard?,
    npcName: String,
    npcTitle: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(6.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header with title and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CHARACTER RECORD",
                        color = AntiqueGold,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = AntiqueGold
                        )
                    }
                }

                HorizontalDivider(
                    color = MetalBorder,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Large, Complete Portrait above description (Preserving original aspect ratio)
                    val avatarStr = character?.avatarBase64
                    val decodedBitmap = remember(avatarStr) {
                        if (!avatarStr.isNullOrBlank()) {
                            try {
                                val cleanBase64 = if (avatarStr.contains(",")) avatarStr.substringAfter(",") else avatarStr
                                val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                    }

                    if (decodedBitmap != null) {
                        val imgWidth = decodedBitmap.width
                        val imgHeight = decodedBitmap.height
                        val aspectRatio = if (imgWidth > 0 && imgHeight > 0) imgWidth.toFloat() / imgHeight.toFloat() else (2f / 3f)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CharacterPortraitView(
                                name = character?.name ?: npcName,
                                avatarBase64 = avatarStr,
                                size = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                modifier = Modifier
                                    .widthIn(max = 320.dp)
                                    .heightIn(max = 360.dp)
                                    .aspectRatio(aspectRatio)
                            )
                        }
                    }

                    // Identity Banner (Name & Title)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant, RoundedCornerShape(4.dp))
                            .border(1.dp, MetalBorder, RoundedCornerShape(4.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = character?.name?.ifBlank { npcName } ?: npcName,
                            color = TextWarm,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )
                        val displayTitle = character?.title?.ifBlank { npcTitle } ?: npcTitle
                        if (displayTitle.isNotBlank()) {
                            Text(
                                text = displayTitle,
                                color = AntiqueGoldDark,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Serif,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    // Description / Appearance
                    val descText = character?.description?.trim() ?: ""
                    if (descText.isNotBlank()) {
                        CharacterSectionCard(title = "APPEARANCE & DESCRIPTION") {
                            Text(
                                text = descText,
                                color = TextWarm,
                                fontSize = 13.5.sp,
                                fontFamily = FontFamily.Serif,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    // Personality
                    val personalityText = character?.personality?.trim() ?: ""
                    if (personalityText.isNotBlank()) {
                        CharacterSectionCard(title = "PERSONALITY & DEMEANOR") {
                            Text(
                                text = personalityText,
                                color = TextWarm,
                                fontSize = 13.5.sp,
                                fontFamily = FontFamily.Serif,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    // World Foundations / Background Lore
                    val worldLore = character?.worldFoundations?.trim() ?: ""
                    if (worldLore.isNotBlank()) {
                        CharacterSectionCard(title = "WORLD BACKGROUND & LORE") {
                            Text(
                                text = worldLore,
                                color = TextWarm,
                                fontSize = 13.5.sp,
                                fontFamily = FontFamily.Serif,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    // If all card fields are blank
                    if (descText.isBlank() && personalityText.isBlank() && worldLore.isBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No detailed biographical notes recorded on this card.",
                                color = TextMuted,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Serif
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simple, read-only Inventory view of the PLAYER's established possessions.
 * Grouped into Carried and Worn, showing relevant conditions.
 * Grounded strictly in authoritative continuity and session state.
 */
@Composable
fun InventorySheetDialog(
    campaign: Campaign,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(6.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header with title and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "POSSESSIONS & INVENTORY",
                            color = AntiqueGold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${campaign.player.name.ifBlank { "Player" }}'s gear",
                            color = TextMuted,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = AntiqueGold
                        )
                    }
                }

                HorizontalDivider(
                    color = MetalBorder,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                val scrollState = rememberScrollState()

                // Extract possessions from continuity facts (strictly read-only)
                val activePossessions = remember(campaign.continuity, campaign.continuity.facts.size) {
                    campaign.continuity.facts.filter { it.active && it.category.equals("possession", ignoreCase = true) }
                }

                // Split into Worn vs Carried
                val wornRegex = remember {
                    Regex("""\b(worn|wearing|wears|clothes|garb|boots|cloak|armor|armour|ring|amulet|tunic|robe|hood|gloves|gauntlet|belt|attire|breeches|coat)\b""", RegexOption.IGNORE_CASE)
                }

                val wornItems = remember(activePossessions) {
                    activePossessions.filter {
                        it.text.contains("worn", ignoreCase = true) || wornRegex.containsMatchIn(it.text)
                    }
                }

                val carriedItems = remember(activePossessions, wornItems) {
                    activePossessions.filter { it !in wornItems }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (activePossessions.isEmpty()) {
                        // Empty or early session fallback
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant, RoundedCornerShape(4.dp))
                                .border(1.dp, MetalBorder, RoundedCornerShape(4.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No distinct items or possessions currently established in the continuity record.\n\nPossessions are established or altered as events occur in the story.",
                                color = TextMuted,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Serif,
                                lineHeight = 19.sp
                            )
                        }
                    } else {
                        // Worn items
                        CharacterSectionCard(title = "WORN & ATTIRE") {
                            if (wornItems.isEmpty()) {
                                Text(
                                    text = "No distinct apparel or worn articles recorded.",
                                    color = TextMuted,
                                    fontSize = 12.5.sp,
                                    fontFamily = FontFamily.Serif
                                )
                            } else {
                                wornItems.forEach { item ->
                                    val cleanText = item.text.substringAfter(":").trim().removeSuffix("(worn)").trim()
                                    InventoryItemRow(itemText = cleanText, turn = item.establishedTurn)
                                }
                            }
                        }

                        // Carried items
                        CharacterSectionCard(title = "CARRIED & HELD") {
                            if (carriedItems.isEmpty()) {
                                Text(
                                    text = "No handheld gear, tools, or packed belongings recorded.",
                                    color = TextMuted,
                                    fontSize = 12.5.sp,
                                    fontFamily = FontFamily.Serif
                                )
                            } else {
                                carriedItems.forEach { item ->
                                    val cleanText = item.text.substringAfter(":").trim().removeSuffix("(worn)").trim()
                                    InventoryItemRow(itemText = cleanText, turn = item.establishedTurn)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(4.dp))
            .border(1.dp, MetalBorder, RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            color = AntiqueGoldDark,
            fontSize = 11.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        content()
    }
}

@Composable
private fun InventoryItemRow(
    itemText: String,
    turn: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            color = AntiqueGold,
            fontSize = 14.sp
        )
        Text(
            text = itemText,
            color = TextWarm,
            fontSize = 13.sp,
            fontFamily = FontFamily.Serif,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
        if (turn > 0) {
            Text(
                text = "T$turn",
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Serif
            )
        }
    }
}
