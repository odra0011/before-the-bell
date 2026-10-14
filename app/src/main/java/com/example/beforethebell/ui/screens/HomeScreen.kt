package com.example.beforethebell.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beforethebell.model.Campaign
import com.example.beforethebell.theme.*

@Composable
fun HomeScreen(
    latestCampaign: Campaign?,
    onContinueGame: () -> Unit,
    onNewGame: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenPrompts: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlack)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title Header
            Text(
                text = "BEFORE THE BELL",
                color = AntiqueGold,
                fontSize = 26.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Text(
                text = "An Interactive Inquest",
                color = TextMuted,
                fontSize = 14.sp,
                fontFamily = FontFamily.Serif,
                letterSpacing = 1.sp
            )

            // Antique decorative separator
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MetalBorder)
                Text(
                    text = " ❖ ",
                    color = AntiqueGoldDark,
                    fontSize = 12.sp
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = MetalBorder)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Continue Button (if existing session)
            if (latestCampaign != null) {
                val continueSubtitle = buildList {
                    add("Turn ${latestCampaign.turnHistory.size}")
                    val loc = latestCampaign.settingName.trim()
                    if (loc.isNotBlank() && !loc.equals("Setting to be discovered", ignoreCase = true) && !loc.equals("To be discovered", ignoreCase = true)) {
                        add(loc)
                    }
                }.joinToString(" · ")

                MenuButton(
                    title = "Continue: ${latestCampaign.npc.name}",
                    subtitle = continueSubtitle,
                    icon = Icons.Default.PlayArrow,
                    isPrimary = true,
                    onClick = onContinueGame
                )
            }

            // New Game
            MenuButton(
                title = "New Game",
                subtitle = "Select character, setting & objective",
                icon = Icons.Default.Add,
                isPrimary = latestCampaign == null,
                onClick = onNewGame
            )

            // Sessions
            MenuButton(
                title = "Sessions",
                subtitle = "Manage campaigns and transcripts",
                icon = Icons.Default.Folder,
                onClick = onOpenSessions
            )

            // Prompts
            MenuButton(
                title = "Prompts",
                subtitle = "Writing presets and module controls",
                icon = Icons.Default.EditNote,
                onClick = onOpenPrompts
            )

            // Settings
            MenuButton(
                title = "Settings",
                subtitle = "OpenRouter key, models & generation",
                icon = Icons.Default.Settings,
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun MenuButton(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(4.dp)
    val borderColor = if (isPrimary) AntiqueGold else MetalBorder
    val bgColor = if (isPrimary) DarkSurfaceVariant else DarkSurface

    Surface(
        onClick = onClick,
        shape = shape,
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = borderColor, shape = shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPrimary) AntiqueGoldBright else AntiqueGoldDark,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isPrimary) TextWarm else TextWarm.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}
