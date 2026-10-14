package com.example.beforethebell.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beforethebell.data.StorageManager
import com.example.beforethebell.model.AppSettings
import com.example.beforethebell.network.OpenRouterService
import com.example.beforethebell.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    storage: StorageManager,
    openRouterService: OpenRouterService,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var settings by remember { mutableStateOf(storage.loadSettings()) }
    var selectedProvider by remember { mutableStateOf(settings.provider) }

    // Provider-specific credentials and models
    var vertexApiKey by remember { mutableStateOf(settings.vertexApiKey) }
    var vertexModelId by remember { mutableStateOf(settings.vertexModelId) }

    var geminiApiKey by remember { mutableStateOf(settings.geminiApiKey) }
    var geminiModelId by remember { mutableStateOf(settings.geminiModelId) }

    var openRouterApiKey by remember { mutableStateOf(settings.openRouterApiKey) }
    var openRouterModelId by remember { mutableStateOf(settings.openRouterModelId) }

    var customApiKey by remember { mutableStateOf(settings.customApiKey) }
    var customBaseUrl by remember { mutableStateOf(settings.customBaseUrl) }
    var customModelId by remember { mutableStateOf(settings.customModelId) }

    var showApiKey by remember { mutableStateOf(false) }

    // Hyperparameters
    var temperature by remember { mutableFloatStateOf(settings.temperature) }
    var topP by remember { mutableFloatStateOf(settings.topP) }
    var maxTokens by remember { mutableIntStateOf(settings.maxTokens) }
    var contextCompressionEnabled by remember { mutableStateOf(settings.contextCompressionEnabled) }
    var inputTokenBudget by remember { mutableIntStateOf(settings.inputTokenBudget) }

    // Connection testing
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf<String?>(null) }
    var isConnectionSuccess by remember { mutableStateOf(false) }

    fun buildCurrentSettings(): AppSettings {
        return AppSettings(
            provider = selectedProvider,
            vertexApiKey = vertexApiKey.trim(),
            vertexModelId = vertexModelId.trim().ifBlank { "gemini-4-argon" },
            geminiApiKey = geminiApiKey.trim(),
            geminiModelId = geminiModelId.trim().ifBlank { "gemini-2.5-flash" },
            openRouterApiKey = openRouterApiKey.trim(),
            openRouterModelId = openRouterModelId.trim().ifBlank { "google/gemini-2.5-flash" },
            customApiKey = customApiKey.trim(),
            customBaseUrl = customBaseUrl.trim().ifBlank { "https://api.openai.com/v1" },
            customModelId = customModelId.trim().ifBlank { "gpt-4o" },
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            contextCompressionEnabled = contextCompressionEnabled,
            inputTokenBudget = inputTokenBudget
        )
    }

    fun saveCurrentSettings() {
        val updated = buildCurrentSettings()
        storage.saveSettings(updated)
        settings = updated
    }

    val isCurrentKeyConfigured = when (selectedProvider) {
        "vertex" -> vertexApiKey.isNotBlank()
        "gemini" -> geminiApiKey.isNotBlank()
        "openrouter" -> openRouterApiKey.isNotBlank()
        "custom" -> customBaseUrl.isNotBlank()
        else -> false
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
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    saveCurrentSettings()
                    onBack()
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AntiqueGold)
                }

                Text(
                    text = "SETTINGS & ENGINE",
                    color = AntiqueGold,
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                IconButton(onClick = {
                    saveCurrentSettings()
                    Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.Check, contentDescription = "Save", tint = AntiqueGoldBright)
                }
            }

            HorizontalDivider(color = MetalBorder, thickness = 1.dp)

            // Scrollable Settings
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Provider Selection
                Text(
                    text = "API PROVIDER",
                    color = AntiqueGoldDark,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                val providerOptions = listOf(
                    "vertex" to "Google Vertex AI",
                    "gemini" to "Gemini API (AI Studio)",
                    "openrouter" to "OpenRouter",
                    "custom" to "Custom OpenAI"
                )

                // 2x2 grid for 4 providers
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in providerOptions.chunked(2)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { (provKey, provLabel) ->
                                val isSelected = selectedProvider == provKey
                                Surface(
                                    onClick = {
                                        if (selectedProvider != provKey) {
                                            selectedProvider = provKey
                                            saveCurrentSettings()
                                        }
                                    },
                                    color = if (isSelected) AntiqueGoldDark.copy(alpha = 0.35f) else DarkSurface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) AntiqueGoldBright else MetalBorder
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = provLabel,
                                            color = if (isSelected) AntiqueGoldBright else TextMuted,
                                            fontSize = 12.5.sp,
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MetalBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                // -------------------------------------------------------------
                // Provider Specific Fields
                // -------------------------------------------------------------
                when (selectedProvider) {
                    "vertex" -> {
                        // Vertex API Key
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GOOGLE VERTEX API KEY",
                                color = AntiqueGoldDark,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            if (vertexApiKey.isNotBlank()) {
                                Surface(
                                    color = DarkSurfaceVariant,
                                    shape = RoundedCornerShape(2.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AntiqueGoldDark)
                                ) {
                                    Text(
                                        text = "Saved",
                                        color = AntiqueGold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Serif,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = vertexApiKey,
                            onValueChange = {
                                vertexApiKey = it
                                saveCurrentSettings()
                            },
                            placeholder = { Text("Enter Google Vertex API key (AQ.Ab8...)", color = TextMuted, fontSize = 13.sp) },
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Visibility",
                                        tint = AntiqueGold
                                    )
                                }
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
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Vertex Model ID (Editable)
                        Text(
                            text = "MODEL ID",
                            color = AntiqueGoldDark,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        OutlinedTextField(
                            value = vertexModelId,
                            onValueChange = {
                                vertexModelId = it
                                saveCurrentSettings()
                            },
                            placeholder = { Text("e.g. gemini-4-argon or gemini-3.8-flash", color = TextMuted, fontSize = 13.sp) },
                            shape = RoundedCornerShape(4.dp),
                            singleLine = true,
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

                        Text(
                            text = "Uses Google Vertex AI endpoint. Keys and model IDs are stored privately on device.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }

                    "gemini" -> {
                        // Gemini API Key
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GOOGLE GEMINI API KEY",
                                color = AntiqueGoldDark,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            if (geminiApiKey.isNotBlank()) {
                                Surface(
                                    color = DarkSurfaceVariant,
                                    shape = RoundedCornerShape(2.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AntiqueGoldDark)
                                ) {
                                    Text(
                                        text = "Saved",
                                        color = AntiqueGold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Serif,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = geminiApiKey,
                            onValueChange = {
                                geminiApiKey = it
                                saveCurrentSettings()
                            },
                            placeholder = { Text("Enter Gemini API key from Google AI Studio (AIzaSy...)", color = TextMuted, fontSize = 13.sp) },
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Visibility",
                                        tint = AntiqueGold
                                    )
                                }
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
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Gemini Model ID (Editable)
                        Text(
                            text = "MODEL ID",
                            color = AntiqueGoldDark,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        OutlinedTextField(
                            value = geminiModelId,
                            onValueChange = {
                                geminiModelId = it
                                saveCurrentSettings()
                            },
                            placeholder = { Text("e.g. gemini-2.5-flash or gemini-2.5-pro", color = TextMuted, fontSize = 13.sp) },
                            shape = RoundedCornerShape(4.dp),
                            singleLine = true,
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

                        Text(
                            text = "Uses Google Gemini API via Google AI Studio. Stored privately on device; usage billed by Google.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }

                    "openrouter" -> {
                        // OpenRouter API Key
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "OPENROUTER API KEY",
                                color = AntiqueGoldDark,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            if (openRouterApiKey.isNotBlank()) {
                                Surface(
                                    color = DarkSurfaceVariant,
                                    shape = RoundedCornerShape(2.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AntiqueGoldDark)
                                ) {
                                    Text(
                                        text = "Saved",
                                        color = AntiqueGold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Serif,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = openRouterApiKey,
                            onValueChange = {
                                openRouterApiKey = it
                                saveCurrentSettings()
                            },
                            placeholder = { Text("Enter OpenRouter API key (sk-or-v1-...)", color = TextMuted, fontSize = 13.sp) },
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Visibility",
                                        tint = AntiqueGold
                                    )
                                }
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
                            modifier = Modifier.fillMaxWidth()
                        )

                        // OpenRouter Model ID (Editable)
                        Text(
                            text = "MODEL ID",
                            color = AntiqueGoldDark,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        OutlinedTextField(
                            value = openRouterModelId,
                            onValueChange = {
                                openRouterModelId = it
                                saveCurrentSettings()
                            },
                            placeholder = { Text("e.g. google/gemini-2.5-flash or anthropic/claude-3.5-sonnet", color = TextMuted, fontSize = 13.sp) },
                            shape = RoundedCornerShape(4.dp),
                            singleLine = true,
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

                        Text(
                            text = "Uses OpenRouter chat completions. Stored securely on device; never included in exports or presets.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }

                    "custom" -> {
                        // Custom Base URL
                        Text(
                            text = "BASE URL",
                            color = AntiqueGoldDark,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        OutlinedTextField(
                            value = customBaseUrl,
                            onValueChange = {
                                customBaseUrl = it
                                saveCurrentSettings()
                            },
                            placeholder = { Text("https://api.openai.com/v1 or http://10.0.2.2:11434/v1", color = TextMuted, fontSize = 13.sp) },
                            shape = RoundedCornerShape(4.dp),
                            singleLine = true,
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

                        // Custom API Key (Optional)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "API KEY (OPTIONAL)",
                                color = AntiqueGoldDark,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            if (customApiKey.isNotBlank()) {
                                Surface(
                                    color = DarkSurfaceVariant,
                                    shape = RoundedCornerShape(2.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AntiqueGoldDark)
                                ) {
                                    Text(
                                        text = "Saved",
                                        color = AntiqueGold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Serif,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = customApiKey,
                            onValueChange = {
                                customApiKey = it
                                saveCurrentSettings()
                            },
                            placeholder = { Text("Optional Bearer token for authentication", color = TextMuted, fontSize = 13.sp) },
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Visibility",
                                        tint = AntiqueGold
                                    )
                                }
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
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Custom Model ID (Editable)
                        Text(
                            text = "MODEL ID",
                            color = AntiqueGoldDark,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        OutlinedTextField(
                            value = customModelId,
                            onValueChange = {
                                customModelId = it
                                saveCurrentSettings()
                            },
                            placeholder = { Text("e.g. gpt-4o, llama3, mistral-large, etc.", color = TextMuted, fontSize = 13.sp) },
                            shape = RoundedCornerShape(4.dp),
                            singleLine = true,
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

                        Text(
                            text = "Compatible with any OpenAI-compatible server (vLLM, Ollama, LM Studio, Aphrodite, Together, Groq, etc.).",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }
                }

                // Connection Test Button
                OutlinedButton(
                    onClick = {
                        isTestingConnection = true
                        connectionTestResult = null
                        saveCurrentSettings()
                        coroutineScope.launch {
                            val curSettings = buildCurrentSettings()
                            val result = openRouterService.testConnection(curSettings)
                            if (result.isSuccess) {
                                isConnectionSuccess = true
                                connectionTestResult = result.getOrNull()
                            } else {
                                isConnectionSuccess = false
                                connectionTestResult = result.exceptionOrNull()?.message ?: "Connection test failed."
                            }
                            isTestingConnection = false
                        }
                    },
                    enabled = !isTestingConnection && isCurrentKeyConfigured,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AntiqueGold),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(color = AntiqueGold, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing...", color = TextWarm, fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = AntiqueGoldBright, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Connection", color = AntiqueGold, fontSize = 13.sp, fontFamily = FontFamily.Serif)
                    }
                }

                if (connectionTestResult != null) {
                    Surface(
                        color = if (isConnectionSuccess) Color(0xFF132B1A) else Color(0xFF381515),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isConnectionSuccess) Color(0xFF2E6B3D) else Color(0xFF6A2222)),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = connectionTestResult ?: "",
                            color = if (isConnectionSuccess) Color(0xFFA6E3B8) else Color(0xFFE89898),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                HorizontalDivider(color = MetalBorder, thickness = 1.dp)

                // Generation Hyperparameters
                Text(
                    text = "GENERATION PARAMETERS",
                    color = AntiqueGoldDark,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Temperature
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Temperature", color = TextWarm, fontSize = 13.sp, fontFamily = FontFamily.Serif)
                        Text(text = String.format("%.2f", temperature), color = AntiqueGold, fontSize = 13.sp)
                    }
                    Slider(
                        value = temperature,
                        onValueChange = {
                            temperature = it
                            saveCurrentSettings()
                        },
                        valueRange = 0.1f..1.5f,
                        colors = SliderDefaults.colors(thumbColor = AntiqueGold, activeTrackColor = AntiqueGoldDark, inactiveTrackColor = MetalBorder)
                    )
                }

                // Top-P
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Top P", color = TextWarm, fontSize = 13.sp, fontFamily = FontFamily.Serif)
                        Text(text = String.format("%.2f", topP), color = AntiqueGold, fontSize = 13.sp)
                    }
                    Slider(
                        value = topP,
                        onValueChange = {
                            topP = it
                            saveCurrentSettings()
                        },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = AntiqueGold, activeTrackColor = AntiqueGoldDark, inactiveTrackColor = MetalBorder)
                    )
                }

                // Max Tokens
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Max Tokens", color = TextWarm, fontSize = 13.sp, fontFamily = FontFamily.Serif)
                        Text(text = "$maxTokens", color = AntiqueGold, fontSize = 13.sp)
                    }
                    Slider(
                        value = maxTokens.toFloat(),
                        onValueChange = {
                            maxTokens = it.toInt()
                            saveCurrentSettings()
                        },
                        valueRange = 512f..4096f,
                        steps = 6,
                        colors = SliderDefaults.colors(thumbColor = AntiqueGold, activeTrackColor = AntiqueGoldDark, inactiveTrackColor = MetalBorder)
                    )
                }

                // Context & Compression
                Text(
                    text = "CONTEXT & COMPRESSION",
                    color = AntiqueGoldDark,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Toggle: Context Compression
                Surface(
                    color = DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Context Compression",
                                color = TextWarm,
                                fontSize = 13.5.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Automatically summarize older dialogue when approaching the input token limit.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Serif
                            )
                        }
                        Switch(
                            checked = contextCompressionEnabled,
                            onCheckedChange = {
                                contextCompressionEnabled = it
                                saveCurrentSettings()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AntiqueGoldBright,
                                checkedTrackColor = AntiqueGoldDark.copy(alpha = 0.5f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = NearBlack
                            )
                        )
                    }
                }

                // Input Token Budget Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(text = "Input Token Budget", color = TextWarm, fontSize = 13.sp, fontFamily = FontFamily.Serif)
                            Text(
                                text = "Compression triggers at ~${(inputTokenBudget * 0.75f).toInt()} tokens",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Serif
                            )
                        }
                        Text(text = "$inputTokenBudget tokens", color = AntiqueGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = inputTokenBudget.toFloat(),
                        onValueChange = {
                            inputTokenBudget = ((it / 1000).toInt() * 1000).coerceIn(8000, 64000)
                            saveCurrentSettings()
                        },
                        valueRange = 8000f..64000f,
                        enabled = contextCompressionEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = AntiqueGold,
                            activeTrackColor = AntiqueGoldDark,
                            inactiveTrackColor = MetalBorder
                        )
                    )
                }

                HorizontalDivider(color = MetalBorder, thickness = 1.dp)

                // Diagnostics Section
                Text(
                    text = "DIAGNOSTICS",
                    color = AntiqueGoldDark,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                val lastReq = OpenRouterService.lastGameplayRequest
                var exportStatusMessage by remember { mutableStateOf<String?>(null) }

                fun buildDiagnosticJson(): String {
                    if (lastReq == null) return "{}"
                    val root = JSONObject().apply {
                        put("timestamp", lastReq.timestamp)
                        put("provider", lastReq.provider)
                        put("endpoint", lastReq.endpoint)
                        put("modelId", lastReq.modelId)
                        put("scope", lastReq.scope)
                        put("generationSettings", JSONObject(lastReq.generationSettings))
                        val modsArr = JSONArray()
                        lastReq.modulesMetadata.forEach { modsArr.put(JSONObject(it)) }
                        put("modulesMetadata", modsArr)
                        put("serializedRequest", lastReq.serializedRequest)
                    }
                    return root.toString(2)
                }

                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri: Uri? ->
                    if (uri != null) {
                        try {
                            context.contentResolver.openOutputStream(uri)?.use { os ->
                                os.write(buildDiagnosticJson().toByteArray())
                            }
                            exportStatusMessage = "Diagnostic request exported successfully."
                            Toast.makeText(context, "Exported successfully", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            exportStatusMessage = "Export failed: ${e.message}"
                            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                Surface(
                    color = DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MetalBorder),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Export Last Request",
                            color = TextWarm,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (lastReq != null) {
                                "Captured last gameplay request (${lastReq.scope}) for ${lastReq.provider} / ${lastReq.modelId} immediately before transmission."
                            } else {
                                "No gameplay request captured yet in this session. Generate an opening or continuation turn in gameplay first."
                            },
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif
                        )

                        OutlinedButton(
                            onClick = {
                                if (lastReq != null) {
                                    val fileName = "btb_last_request_${lastReq.provider}_${lastReq.scope}_${System.currentTimeMillis()}.json"
                                    exportLauncher.launch(fileName)
                                }
                            },
                            enabled = lastReq != null,
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurfaceVariant),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (lastReq != null) AntiqueGold else MetalBorder),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = if (lastReq != null) AntiqueGoldBright else TextMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Export Last Request (JSON)",
                                color = if (lastReq != null) AntiqueGold else TextMuted,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Serif
                            )
                        }

                        if (exportStatusMessage != null) {
                            Text(
                                text = exportStatusMessage ?: "",
                                color = AntiqueGold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Serif
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
