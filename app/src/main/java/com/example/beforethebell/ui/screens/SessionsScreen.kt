package com.example.beforethebell.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beforethebell.data.StorageManager
import com.example.beforethebell.model.Campaign
import com.example.beforethebell.theme.*
import com.example.beforethebell.ui.components.CharacterPortraitView

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider

@Composable
fun SessionsScreen(
    storage: StorageManager,
    onResumeCampaign: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var campaigns by remember { mutableStateOf(storage.listCampaigns()) }
    var campaignToDelete by remember { mutableStateOf<Campaign?>(null) }

    // JSON session import launcher
    val sessionImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val content = String(inputStream.readBytes(), Charsets.UTF_8)
                    val imported = storage.importCampaignFromJson(content)
                    if (imported != null) {
                        campaigns = storage.listCampaigns()
                        Toast.makeText(context, "Session '${imported.title}' imported", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Invalid session JSON file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to import session: ${e.message}", Toast.LENGTH_LONG).show()
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AntiqueGold)
                }

                Text(
                    text = "SAVED SESSIONS",
                    color = AntiqueGold,
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Import Session button in header
                IconButton(onClick = { sessionImportLauncher.launch("application/json") }) {
                    Icon(Icons.Default.FileOpen, contentDescription = "Import Session", tint = AntiqueGold)
                }
            }

            HorizontalDivider(color = MetalBorder, thickness = 1.dp)

            if (campaigns.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recorded sessions found.",
                        color = TextMuted,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Serif
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(campaigns) { camp ->
                        SessionItemCard(
                            campaign = camp,
                            onResume = { onResumeCampaign(camp.campaignId) },
                            onExportJson = {
                                try {
                                    val file = storage.exportCampaignJsonFile(camp)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        type = "application/json"
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Export Session JSON File")
                                    context.startActivity(shareIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onExport = {
                                val transcript = storage.exportTranscript(camp)
                                // Share plain-text transcript via Intent
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, transcript)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share Session Transcript")
                                context.startActivity(shareIntent)
                            },
                            onCopy = {
                                val transcript = storage.exportTranscript(camp)
                                clipboardManager.setText(AnnotatedString(transcript))
                                Toast.makeText(context, "Transcript copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {
                                campaignToDelete = camp
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (campaignToDelete != null) {
        val camp = campaignToDelete!!
        AlertDialog(
            onDismissRequest = { campaignToDelete = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "DELETE SESSION?",
                    color = AntiqueGold,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you wish to delete the inquest records for ${camp.npc.name}? This action is irreversible.",
                    color = TextWarm,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        storage.deleteCampaign(camp.campaignId)
                        campaigns = storage.listCampaigns()
                        campaignToDelete = null
                        Toast.makeText(context, "Session deleted", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete", color = Color(0xFFE89898))
                }
            },
            dismissButton = {
                TextButton(onClick = { campaignToDelete = null }) {
                    Text("Cancel", color = AntiqueGold)
                }
            }
        )
    }
}

@Composable
private fun SessionItemCard(
    campaign: Campaign,
    onResume: () -> Unit,
    onExportJson: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CharacterPortraitView(
                    name = campaign.npc.name,
                    avatarBase64 = campaign.npc.portraitUrl,
                    size = 46.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = campaign.npc.name,
                        color = TextWarm,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold
                    )
                    val sessionSubtitle = buildList {
                        add("Turn ${campaign.turnHistory.size}")
                        val setting = campaign.settingName.trim()
                        if (setting.isNotBlank() && !setting.equals("Setting to be discovered", ignoreCase = true) && !setting.equals("To be discovered", ignoreCase = true)) {
                            add(setting)
                        }
                    }.joinToString(" · ")
                    Text(
                        text = sessionSubtitle,
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Serif
                    )
                }

                if (campaign.activeEnding != null) {
                    Box(
                        modifier = Modifier
                            .background(DarkSurfaceVariant, RoundedCornerShape(2.dp))
                            .border(0.5.dp, AntiqueGoldDark, RoundedCornerShape(2.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = campaign.activeEnding ?: "RESOLVED",
                            color = AntiqueGold,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Objective Snippet
            val objSnippet = if (com.example.beforethebell.engine.ObjectiveManager.isOpenEnded(campaign)) "None (Open-ended)" else campaign.objective
            Text(
                text = "Objective: $objSnippet",
                color = TextWarm.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
                maxLines = 2
            )

            HorizontalDivider(color = MetalBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

            // Actions row: Resume, Export JSON, Share Transcript, Copy, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onResume,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AntiqueGold),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AntiqueGoldBright, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resume", color = AntiqueGold, fontSize = 12.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onExportJson, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export JSON File", tint = AntiqueGold, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Transcript", tint = AntiqueGold, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onExport, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share Transcript", tint = AntiqueGold, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Session", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
