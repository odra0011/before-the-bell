package com.example.beforethebell.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.beforethebell.data.StorageManager
import com.example.beforethebell.model.PromptModule
import com.example.beforethebell.model.WritingPreset
import com.example.beforethebell.theme.*
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptsScreen(
    storage: StorageManager,
    currentCampaignId: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var presets by remember { mutableStateOf(storage.listPresets()) }
    var selectedPreset by remember { mutableStateOf<WritingPreset?>(storage.getActivePreset()) }
    var activeModules by remember { mutableStateOf<List<PromptModule>>(selectedPreset?.modules ?: emptyList()) }
    var showPresetDropdown by remember { mutableStateOf(false) }

    // Dialog states
    var editingModule by remember { mutableStateOf<PromptModule?>(null) }
    var isCreatingNewModule by remember { mutableStateOf(false) }
    var showDeletePresetConfirm by remember { mutableStateOf(false) }
    var showCreatePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    // When selectedPreset changes, refresh activeModules and persist active preset choice
    LaunchedEffect(selectedPreset?.id) {
        activeModules = selectedPreset?.modules ?: emptyList()
        selectedPreset?.id?.let { storage.setActivePresetId(it) }
    }

    // JSON file picker launcher for importing writing preset
    val presetImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val content = String(inputStream.readBytes(), Charsets.UTF_8)
                    val json = JSONObject(content)
                    val imported = WritingPreset.fromJsonObject(json)
                    storage.savePreset(imported)
                    presets = storage.listPresets()
                    selectedPreset = imported
                    activeModules = imported.modules.toMutableList()
                    storage.setActivePresetId(imported.id)
                    Toast.makeText(context, "Preset '${imported.name}' imported", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to import preset: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val curCampaign = remember(currentCampaignId) {
        currentCampaignId?.let { storage.getCampaign(it) }
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
                    text = "PROMPTS & MODULES",
                    color = AntiqueGold,
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Create new preset button
                IconButton(
                    onClick = {
                        newPresetName = ""
                        showCreatePresetDialog = true
                    }
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Create New Preset", tint = AntiqueGold)
                }
            }

            HorizontalDivider(color = MetalBorder, thickness = 1.dp)

            // Preset Selection Dropdown Box & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        onClick = { if (presets.isNotEmpty()) showPresetDropdown = true },
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedPreset?.name ?: if (presets.isEmpty()) "No Presets" else "Select Preset",
                                color = if (selectedPreset != null) TextWarm else TextMuted,
                                fontSize = 13.5.sp,
                                fontFamily = FontFamily.Serif,
                                maxLines = 1
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AntiqueGold)
                        }
                    }

                    if (presets.isNotEmpty()) {
                        DropdownMenu(
                            expanded = showPresetDropdown,
                            onDismissRequest = { showPresetDropdown = false },
                            modifier = Modifier.background(DarkSurface).border(1.dp, MetalBorder)
                        ) {
                            presets.forEach { preset ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = preset.name + if (preset.isDefault) " (Default)" else "",
                                            color = if (preset.id == selectedPreset?.id) AntiqueGold else TextWarm,
                                            fontFamily = FontFamily.Serif
                                        )
                                    },
                                    onClick = {
                                        selectedPreset = preset
                                        showPresetDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Delete Preset button
                IconButton(
                    onClick = { showDeletePresetConfirm = true },
                    enabled = selectedPreset != null,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete Preset",
                        tint = if (selectedPreset != null) Color(0xFFE89898) else TextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Duplicate / clone preset button
                IconButton(
                    onClick = {
                        val cur = selectedPreset ?: return@IconButton
                        val newPreset = cur.copy(
                            id = "preset_${System.currentTimeMillis()}",
                            name = "${cur.name} (Copy)",
                            isDefault = false,
                            createdAt = System.currentTimeMillis().toString()
                        )
                        storage.savePreset(newPreset)
                        presets = storage.listPresets()
                        selectedPreset = newPreset
                        Toast.makeText(context, "Preset duplicated as '${newPreset.name}'", Toast.LENGTH_SHORT).show()
                    },
                    enabled = selectedPreset != null,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Duplicate Preset",
                        tint = if (selectedPreset != null) AntiqueGold else TextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Import JSON button
                OutlinedButton(
                    onClick = { presetImportLauncher.launch("application/json") },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = "Import", tint = AntiqueGold, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import", color = TextWarm, fontSize = 11.5.sp)
                }

                // Export JSON File button
                OutlinedButton(
                    onClick = {
                        val cur = selectedPreset ?: return@OutlinedButton
                        try {
                            val file = storage.exportPresetJsonFile(cur)
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
                            val shareIntent = Intent.createChooser(sendIntent, "Export Preset JSON File")
                            context.startActivity(shareIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = selectedPreset != null,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export JSON File", tint = if (selectedPreset != null) AntiqueGold else TextMuted, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", color = if (selectedPreset != null) TextWarm else TextMuted, fontSize = 11.5.sp)
                }
            }

            // Subtitle info and Add Module button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (selectedPreset != null) "PRESET MODULES (${activeModules.size})" else "NO PRESET SELECTED",
                        color = AntiqueGold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = if (selectedPreset != null) "Edits save directly to preset" else "Create or import a preset above",
                        color = TextMuted,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Serif
                    )
                }

                if (selectedPreset != null) {
                    OutlinedButton(
                        onClick = {
                            val newMod = PromptModule(
                                id = "mod_${System.currentTimeMillis()}",
                                name = "New Module",
                                enabled = true,
                                content = "",
                                placement = "before_history",
                                position = "before_history",
                                depth = 0,
                                role = "system",
                                scope = "both",
                                order = (activeModules.size + 1) * 10,
                                isBuiltin = false
                            )
                            editingModule = newMod
                            isCreatingNewModule = true
                        },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurfaceVariant),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AntiqueGold),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = AntiqueGoldBright, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Module", color = AntiqueGold, fontSize = 11.5.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Module List or Empty State
            if (selectedPreset == null || activeModules.isEmpty()) {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (selectedPreset == null) "NO PRESETS CONFIGURED" else "PRESET HAS NO MODULES",
                                color = AntiqueGoldDark,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (selectedPreset == null) {
                                    "All presets have been removed. You can import a JSON preset or create a new preset to begin."
                                } else {
                                    "Tap '+ Module' above to add prompt instructions to this preset."
                                },
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Serif
                            )
                            if (selectedPreset == null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = { presetImportLauncher.launch("application/json") },
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AntiqueGold)
                                    ) {
                                        Icon(Icons.Default.FileOpen, contentDescription = null, tint = AntiqueGoldBright, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Import Preset", color = AntiqueGold, fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = {
                                            newPresetName = ""
                                            showCreatePresetDialog = true
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AntiqueGoldDark)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = TextWarm, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Create Preset", color = TextWarm, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activeModules) { module ->
                        ModuleItemRow(
                            module = module,
                            onToggle = { isEnabled ->
                                val updatedList = activeModules.map { m ->
                                    if (m.id == module.id) m.copy(enabled = isEnabled) else m
                                }
                                activeModules = updatedList
                                selectedPreset?.let { p ->
                                    val updatedP = p.copy(modules = updatedList)
                                    storage.savePreset(updatedP)
                                    selectedPreset = updatedP
                                }
                            },
                            onEdit = {
                                editingModule = module
                                isCreatingNewModule = false
                            }
                        )
                    }
                }
            }

            // Session Application Section (Distinguishes Preset vs Current Session)
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (curCampaign != null) "ACTIVE SESSION: ${curCampaign.title}" else "NO ACTIVE SESSION",
                            color = if (curCampaign != null) AntiqueGoldBright else TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            if (currentCampaignId != null) {
                                val camp = storage.getCampaign(currentCampaignId)
                                if (camp != null) {
                                    val updatedCamp = camp.copy(
                                        activeModules = activeModules.map { it.copy() }.toMutableList()
                                    )
                                    storage.saveCampaign(updatedCamp)
                                    Toast.makeText(context, "Preset applied to session '${camp.title}'", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "No active session loaded to apply to", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = curCampaign != null && selectedPreset != null,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AntiqueGoldDark,
                            contentColor = TextWarm,
                            disabledContainerColor = DarkSurfaceVariant,
                            disabledContentColor = TextMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .border(1.dp, if (curCampaign != null && selectedPreset != null) AntiqueGold else MetalBorder, RoundedCornerShape(4.dp))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = if (curCampaign != null && selectedPreset != null) AntiqueGoldBright else TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (curCampaign != null) "APPLY TO CURRENT SESSION" else "SESSION NOT LOADED",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }

    // Delete Preset Confirmation Dialog
    if (showDeletePresetConfirm && selectedPreset != null) {
        val cur = selectedPreset!!
        AlertDialog(
            onDismissRequest = { showDeletePresetConfirm = false },
            containerColor = DarkSurface,
            title = {
                Text("DELETE PRESET?", color = AntiqueGold, fontSize = 15.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to delete preset '${cur.name}'? Existing saved sessions will retain their current prompt configuration.",
                    color = TextWarm,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    storage.deletePreset(cur.id)
                    presets = storage.listPresets()
                    selectedPreset = presets.firstOrNull()
                    activeModules = selectedPreset?.modules ?: emptyList()
                    selectedPreset?.let { storage.setActivePresetId(it.id) }
                    showDeletePresetConfirm = false
                    Toast.makeText(context, "Preset '${cur.name}' deleted", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Delete", color = Color(0xFFE89898), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePresetConfirm = false }) {
                    Text("Cancel", color = AntiqueGold)
                }
            }
        )
    }

    // Create New Preset Dialog
    if (showCreatePresetDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePresetDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("NEW PRESET", color = AntiqueGold, fontSize = 15.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a display name for the new writing preset:", color = TextMuted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        placeholder = { Text("e.g. My Narrative Style", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWarm,
                            unfocusedTextColor = TextWarm,
                            focusedBorderColor = AntiqueGold,
                            unfocusedBorderColor = MetalBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newPresetName.trim().ifBlank { "New Preset" }
                        val newPreset = WritingPreset(
                            id = "preset_${System.currentTimeMillis()}",
                            name = name,
                            description = "Custom preset",
                            modules = emptyList(),
                            isDefault = false,
                            createdAt = System.currentTimeMillis().toString()
                        )
                        storage.savePreset(newPreset)
                        presets = storage.listPresets()
                        selectedPreset = newPreset
                        activeModules = emptyList()
                        storage.setActivePresetId(newPreset.id)
                        showCreatePresetDialog = false
                        newPresetName = ""
                        Toast.makeText(context, "Preset '$name' created", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AntiqueGoldDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AntiqueGold)
                ) {
                    Text("Create", color = TextWarm, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePresetDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Module Editor Dialog
    if (editingModule != null) {
        ModuleEditorDialog(
            module = editingModule!!,
            isNew = isCreatingNewModule,
            onDismiss = {
                editingModule = null
                isCreatingNewModule = false
            },
            onSave = { savedMod ->
                val updatedList = if (isCreatingNewModule) {
                    activeModules + savedMod
                } else {
                    activeModules.map { m -> if (m.id == savedMod.id) savedMod else m }
                }
                activeModules = updatedList
                selectedPreset?.let { p ->
                    val updatedP = p.copy(modules = updatedList)
                    storage.savePreset(updatedP)
                    selectedPreset = updatedP
                    presets = storage.listPresets()
                }
                Toast.makeText(context, "Module '${savedMod.name}' saved to preset", Toast.LENGTH_SHORT).show()
                editingModule = null
                isCreatingNewModule = false
            },
            onDelete = {
                val updatedList = activeModules.filter { it.id != editingModule!!.id }
                activeModules = updatedList
                selectedPreset?.let { p ->
                    val updatedP = p.copy(modules = updatedList)
                    storage.savePreset(updatedP)
                    selectedPreset = updatedP
                    presets = storage.listPresets()
                }
                Toast.makeText(context, "Module deleted from preset", Toast.LENGTH_SHORT).show()
                editingModule = null
                isCreatingNewModule = false
            }
        )
    }
}

@Composable
private fun ModuleItemRow(
    module: PromptModule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (module.enabled) MetalBorderHighlight else MetalBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(
                checked = module.enabled,
                onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(
                    checkedColor = AntiqueGold,
                    checkmarkColor = NearBlack,
                    uncheckedColor = MetalBorder
                )
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.name,
                    color = if (module.enabled) TextWarm else TextMuted,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium
                )

                // Badges: Role, Placement, Depth (if in_history), Scope, Order
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MinimalBadge(text = module.role.uppercase())
                    MinimalBadge(text = module.placement)
                    if (module.placement == "in_history") {
                        MinimalBadge(text = "d:${module.depth}")
                    }
                    if (module.scope != "both") {
                        MinimalBadge(text = module.scope)
                    }
                    MinimalBadge(text = "#${module.order}")
                }
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Module",
                    tint = AntiqueGoldDark,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MinimalBadge(text: String) {
    Box(
        modifier = Modifier
            .background(DarkSurfaceVariant, RoundedCornerShape(2.dp))
            .border(0.5.dp, MetalBorder, RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            color = TextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ModuleEditorDialog(
    module: PromptModule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (PromptModule) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(module.name) }
    var content by remember { mutableStateOf(module.content) }
    var enabled by remember { mutableStateOf(module.enabled) }
    var role by remember { mutableStateOf(module.role) }
    var placement by remember { mutableStateOf(module.placement) }
    var depthText by remember { mutableStateOf(module.depth.toString()) }
    var orderText by remember { mutableStateOf(module.order.toString()) }
    var scope by remember { mutableStateOf(module.scope) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = if (isNew) "NEW PROMPT MODULE" else "EDIT MODULE",
                color = AntiqueGold,
                fontSize = 16.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Module Name", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWarm,
                        unfocusedTextColor = TextWarm,
                        focusedBorderColor = AntiqueGold,
                        unfocusedBorderColor = MetalBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Enabled Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enabled", color = TextWarm, fontSize = 13.sp, fontFamily = FontFamily.Serif)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NearBlack,
                            checkedTrackColor = AntiqueGold,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )
                }

                HorizontalDivider(color = MetalBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                // Role Options: system, user, assistant
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Role", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Serif)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("system" to "System", "user" to "User", "assistant" to "Assistant").forEach { (rVal, rLabel) ->
                            val isSel = role == rVal
                            Surface(
                                onClick = { role = rVal },
                                shape = RoundedCornerShape(3.dp),
                                color = if (isSel) AntiqueGoldDark else DarkSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) AntiqueGold else MetalBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = rLabel,
                                        color = if (isSel) TextWarm else TextMuted,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // Placement Options: before_history, after_history, in_history
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Placement", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Serif)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "before_history" to "Before",
                            "after_history" to "After",
                            "in_history" to "In History"
                        ).forEach { (pVal, pLabel) ->
                            val isSel = placement == pVal
                            Surface(
                                onClick = { placement = pVal },
                                shape = RoundedCornerShape(3.dp),
                                color = if (isSel) AntiqueGoldDark else DarkSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) AntiqueGold else MetalBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = pLabel,
                                        color = if (isSel) TextWarm else TextMuted,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // Depth (shown only when placement == in_history)
                if (placement == "in_history") {
                    OutlinedTextField(
                        value = depthText,
                        onValueChange = { depthText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Depth (turns from end; 0 = latest turn)", color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWarm,
                            unfocusedTextColor = TextWarm,
                            focusedBorderColor = AntiqueGold,
                            unfocusedBorderColor = MetalBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Scope Options: both, opening, continuation
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Scope", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Serif)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "both" to "Both",
                            "opening" to "Opening",
                            "continuation" to "Continuation"
                        ).forEach { (sVal, sLabel) ->
                            val isSel = scope == sVal
                            Surface(
                                onClick = { scope = sVal },
                                shape = RoundedCornerShape(3.dp),
                                color = if (isSel) AntiqueGoldDark else DarkSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) AntiqueGold else MetalBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = sLabel,
                                        color = if (isSel) TextWarm else TextMuted,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // Order Field
                OutlinedTextField(
                    value = orderText,
                    onValueChange = { orderText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Order (evaluation priority, e.g. 10)", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWarm,
                        unfocusedTextColor = TextWarm,
                        focusedBorderColor = AntiqueGold,
                        unfocusedBorderColor = MetalBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = MetalBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                // Prompt Text (Content)
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Prompt Text (Supports {{char}} & {{user}})", color = TextMuted) },
                    minLines = 6,
                    maxLines = 12,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWarm,
                        unfocusedTextColor = TextWarm,
                        focusedBorderColor = AntiqueGold,
                        unfocusedBorderColor = MetalBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedDepth = depthText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val parsedOrder = orderText.toIntOrNull() ?: 10
                    val saved = module.copy(
                        name = name.ifBlank { "Untitled Module" },
                        content = content,
                        enabled = enabled,
                        role = role,
                        placement = placement,
                        position = placement,
                        depth = parsedDepth,
                        order = parsedOrder,
                        scope = scope
                    )
                    onSave(saved)
                },
                colors = ButtonDefaults.buttonColors(containerColor = AntiqueGoldDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, AntiqueGold)
            ) {
                Text("Save", color = TextWarm, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isNew) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("Delete", color = Color(0xFFE89898))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextMuted)
                }
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DarkSurface,
            title = {
                Text("DELETE MODULE?", color = AntiqueGold, fontSize = 15.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to delete module '${module.name}' from this preset?", color = TextWarm, fontSize = 13.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("Delete", color = Color(0xFFE89898), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = AntiqueGold)
                }
            }
        )
    }
}
