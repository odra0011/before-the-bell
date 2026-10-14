package com.example.beforethebell.network

import com.example.beforethebell.engine.TransmittedMessage
import com.example.beforethebell.model.ActionOption
import com.example.beforethebell.model.AppSettings
import com.example.beforethebell.model.HiddenScenario
import com.example.beforethebell.model.ScenarioObjectiveRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.example.beforethebell.model.PromptModule

data class ApiErrorDetail(
    val summary: String,
    val requestType: String, // "gameplay", "translation", "compression"
    val modelId: String,
    val endpointRegion: String,
    val rawProviderError: String,
    val httpStatusCode: Int? = null
) : Exception(summary)

data class DiagnosticMetadata(
    val scope: String, // "opening" | "continuation"
    val modules: List<PromptModule> = emptyList()
)

data class LastGameplayRequestDiagnostic(
    val timestamp: Long = System.currentTimeMillis(),
    val provider: String,
    val endpoint: String,
    val modelId: String,
    val scope: String,
    val generationSettings: Map<String, Any>,
    val modulesMetadata: List<Map<String, Any>>,
    val serializedRequest: JSONObject
)

data class ParsedTurnResponse(
    val story: String,
    val speech: String = "",
    val situation: String? = null,
    val objectiveStatus: String = "ongoing",
    val objectiveResolutionReason: String? = null,
    val trustDelta: Int = 0,
    val dispositionDelta: Int = 0,
    val timeSpentMinutes: Int = 1,
    val impressionUpdate: String? = null,
    val consequenceNote: String? = null,
    val continuityUpdates: List<JSONObject> = emptyList(),
    val actionSuggestions: List<ActionOption> = emptyList(),
    val speechSuggestions: List<String> = emptyList(),
    val locationName: String? = null,
    // Opening-only: AI-authored hidden scenario sheet and starting equipment/constraints
    val hiddenScenario: HiddenScenario? = null,
    val authoredObjective: ScenarioObjectiveRecord? = null,
    val assignedPlayerName: String? = null,
    val assignedPlayerRole: String? = null,
    val startTime: String? = null,
    val startingPossessions: List<String> = emptyList(),
    val startingConstraints: List<String> = emptyList()
)

class OpenRouterService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        @Volatile
        var lastGameplayRequest: LastGameplayRequestDiagnostic? = null
    }

    suspend fun generateCompletion(
        settings: AppSettings,
        messages: List<TransmittedMessage>,
        diagnosticMetadata: DiagnosticMetadata? = null
    ): Result<ParsedTurnResponse> = withContext(Dispatchers.IO) {
        when (settings.provider) {
            "vertex" -> generateVertexCompletion(settings, messages, diagnosticMetadata)
            "gemini" -> generateGeminiCompletion(settings, messages, diagnosticMetadata)
            "openrouter" -> generateOpenRouterCompletion(settings, messages, diagnosticMetadata)
            "custom" -> generateCustomCompletion(settings, messages, diagnosticMetadata)
            else -> Result.failure(IllegalStateException("Unsupported provider: ${settings.provider}"))
        }
    }

    suspend fun generateText(
        settings: AppSettings,
        messages: List<TransmittedMessage>,
        maxTokens: Int = 1500,
        temperature: Float = 0.4f,
        requestType: String = "general"
    ): Result<String> = withContext(Dispatchers.IO) {
        when (settings.provider) {
            "vertex" -> generateVertexText(settings, messages, maxTokens, temperature, requestType)
            "gemini" -> generateGeminiText(settings, messages, maxTokens, temperature, requestType)
            "openrouter" -> generateOpenRouterText(settings, messages, maxTokens, temperature, requestType)
            "custom" -> generateCustomText(settings, messages, maxTokens, temperature, requestType)
            else -> Result.failure(IllegalStateException("Unsupported provider: ${settings.provider}"))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // OpenRouter Completion & Text
    // ---------------------------------------------------------------------------------------------

    private suspend fun generateOpenRouterCompletion(
        settings: AppSettings,
        messages: List<TransmittedMessage>,
        diagnosticMetadata: DiagnosticMetadata? = null
    ): Result<ParsedTurnResponse> = withContext(Dispatchers.IO) {
        val model = settings.openRouterModelId.trim().ifBlank { "google/gemini-2.5-flash" }
        if (settings.openRouterApiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("OpenRouter API key is missing. Please enter your key in Settings."))
        }

        val messagesArray = JSONArray()
        for (m in messages) {
            messagesArray.put(m.toJsonObject())
        }

        val requestJson = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", settings.temperature)
            put("top_p", settings.topP)
            put("max_tokens", settings.maxTokens)
            put("response_format", JSONObject().apply { put("type", "json_object") })
        }

        // Capture diagnostic immediately before transmission (sanitized, excluding auth headers)
        if (diagnosticMetadata != null) {
            val endpoint = "https://openrouter.ai/api/v1/chat/completions"
            val genSettings = mapOf<String, Any>(
                "temperature" to settings.temperature,
                "topP" to settings.topP,
                "maxTokens" to settings.maxTokens,
                "contextCompressionEnabled" to settings.contextCompressionEnabled,
                "inputTokenBudget" to settings.inputTokenBudget
            )
            val modsMeta = diagnosticMetadata.modules.map { mod ->
                mapOf<String, Any>(
                    "id" to mod.id,
                    "name" to mod.name,
                    "enabled" to mod.enabled,
                    "role" to mod.role,
                    "placement" to mod.placement,
                    "depth" to mod.depth,
                    "order" to mod.order,
                    "scope" to mod.scope
                )
            }
            lastGameplayRequest = LastGameplayRequestDiagnostic(
                provider = "openrouter",
                endpoint = endpoint,
                modelId = model,
                scope = diagnosticMetadata.scope,
                generationSettings = genSettings,
                modulesMetadata = modsMeta,
                serializedRequest = JSONObject(requestJson.toString())
            )
        }

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer ${settings.openRouterApiKey.trim()}")
            .header("HTTP-Referer", "https://beforethebell.app")
            .header("X-Title", "Before the Bell")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseString = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val rawErr = try {
                        val errObj = JSONObject(responseString)
                        val err = errObj.optJSONObject("error")
                        err?.optString("message", responseString) ?: responseString
                    } catch (e: Exception) {
                        responseString
                    }

                    val code = response.code
                    val userFriendly = when (code) {
                        401 -> "Authentication error (401): Invalid OpenRouter API key. Check Settings."
                        402 -> "Payment required (402): Insufficient OpenRouter credits."
                        404 -> "Model not found (404): Model '$model' is not accessible."
                        429 -> "Rate limit (429): Request rate limit or provider capacity reached. Please wait a moment."
                        else -> "OpenRouter HTTP $code: $rawErr"
                    }
                    val errorDetail = ApiErrorDetail(
                        summary = userFriendly,
                        requestType = "gameplay",
                        modelId = model,
                        endpointRegion = "Global",
                        rawProviderError = rawErr,
                        httpStatusCode = code
                    )
                    return@withContext Result.failure(errorDetail)
                }

                val responseJson = JSONObject(responseString)
                val choices = responseJson.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return@withContext Result.failure(IOException("Empty completion response from OpenRouter."))
                }

                val messageObj = choices.getJSONObject(0).optJSONObject("message")
                val content = messageObj?.optString("content", "") ?: ""

                val parsed = parseRobustJson(content)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Network error calling OpenRouter"))
        }
    }

    private suspend fun generateOpenRouterText(
        settings: AppSettings,
        messages: List<TransmittedMessage>,
        maxTokens: Int,
        temperature: Float,
        requestType: String = "general"
    ): Result<String> = withContext(Dispatchers.IO) {
        val model = settings.openRouterModelId.trim().ifBlank { "google/gemini-2.5-flash" }
        if (settings.openRouterApiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("OpenRouter API key is missing. Please enter your key in Settings."))
        }

        val messagesArray = JSONArray()
        for (m in messages) {
            messagesArray.put(m.toJsonObject())
        }

        val requestJson = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", temperature)
            put("top_p", settings.topP)
            put("max_tokens", maxTokens)
        }

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer ${settings.openRouterApiKey.trim()}")
            .header("HTTP-Referer", "https://beforethebell.app")
            .header("X-Title", "Before the Bell")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseString = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val rawErr = try {
                        val errObj = JSONObject(responseString)
                        val err = errObj.optJSONObject("error")
                        err?.optString("message", responseString) ?: responseString
                    } catch (e: Exception) {
                        responseString
                    }

                    val code = response.code
                    val userFriendly = when (code) {
                        401 -> "Authentication error (401): Invalid OpenRouter API key. Check Settings."
                        402 -> "Payment required (402): Insufficient OpenRouter credits."
                        404 -> "Model not found (404): Model '$model' is not accessible."
                        429 -> "Rate limit (429): Request rate limit or provider capacity reached. Please wait a moment."
                        else -> "OpenRouter HTTP $code: $rawErr"
                    }
                    val errorDetail = ApiErrorDetail(
                        summary = userFriendly,
                        requestType = requestType,
                        modelId = model,
                        endpointRegion = "Global",
                        rawProviderError = rawErr,
                        httpStatusCode = code
                    )
                    return@withContext Result.failure(errorDetail)
                }

                val responseJson = JSONObject(responseString)
                val choices = responseJson.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return@withContext Result.failure(IOException("Empty text completion response from OpenRouter."))
                }

                val messageObj = choices.getJSONObject(0).optJSONObject("message")
                val content = messageObj?.optString("content", "")?.trim() ?: ""
                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Network error calling OpenRouter"))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Google Vertex AI Completion & Text
    // ---------------------------------------------------------------------------------------------

    private suspend fun generateVertexCompletion(
        settings: AppSettings,
        messages: List<TransmittedMessage>,
        diagnosticMetadata: DiagnosticMetadata? = null
    ): Result<ParsedTurnResponse> = withContext(Dispatchers.IO) {
        val model = settings.vertexModelId.trim().ifBlank { "gemini-4-argon" }
        if (settings.vertexApiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Google Vertex API key is missing. Please enter your key in Settings."))
        }

        val (systemParts, contentsArray) = buildGeminiContents(messages)

        val requestJson = JSONObject().apply {
            if (systemParts.length() > 0) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", systemParts)
                })
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", settings.temperature)
                put("topP", settings.topP)
                put("maxOutputTokens", settings.maxTokens)
            })
        }

        // Capture diagnostic immediately before transmission (sanitized, excluding API key)
        if (diagnosticMetadata != null) {
            val endpoint = "https://aiplatform.googleapis.com/v1/projects/973206776830/locations/global/publishers/google/models/$model:generateContent"
            val genSettings = mapOf<String, Any>(
                "temperature" to settings.temperature,
                "topP" to settings.topP,
                "maxTokens" to settings.maxTokens,
                "contextCompressionEnabled" to settings.contextCompressionEnabled,
                "inputTokenBudget" to settings.inputTokenBudget
            )
            val modsMeta = diagnosticMetadata.modules.map { mod ->
                mapOf<String, Any>(
                    "id" to mod.id,
                    "name" to mod.name,
                    "enabled" to mod.enabled,
                    "role" to mod.role,
                    "placement" to mod.placement,
                    "depth" to mod.depth,
                    "order" to mod.order,
                    "scope" to mod.scope
                )
            }
            lastGameplayRequest = LastGameplayRequestDiagnostic(
                provider = "vertex",
                endpoint = endpoint,
                modelId = model,
                scope = diagnosticMetadata.scope,
                generationSettings = genSettings,
                modulesMetadata = modsMeta,
                serializedRequest = JSONObject(requestJson.toString())
            )
        }

        val url = "https://aiplatform.googleapis.com/v1/projects/973206776830/locations/global/publishers/google/models/$model:generateContent?key=${settings.vertexApiKey.trim()}"
        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseString = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val rawErr = try {
                        val errObj = JSONObject(responseString)
                        val err = errObj.optJSONObject("error")
                        err?.optString("message", responseString) ?: responseString
                    } catch (e: Exception) {
                        responseString
                    }

                    val code = response.code
                    val userFriendly = when (code) {
                        400 -> "Bad Request (400): $rawErr"
                        401, 403 -> "Authentication error ($code): Invalid Google Vertex API key or unauthorized project access."
                        404 -> "Model not found (404): Vertex model '$model' is not accessible."
                        429 -> "Rate limit (429): Google Vertex quota exceeded or capacity exhausted. Please wait a moment."
                        else -> "Google Vertex HTTP $code: $rawErr"
                    }
                    val errorDetail = ApiErrorDetail(
                        summary = userFriendly,
                        requestType = "gameplay",
                        modelId = model,
                        endpointRegion = "Global (aiplatform.googleapis.com)",
                        rawProviderError = rawErr,
                        httpStatusCode = code
                    )
                    return@withContext Result.failure(errorDetail)
                }

                val responseJson = JSONObject(responseString)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext Result.failure(IOException("Empty completion response from Google Vertex."))
                }

                val contentObj = candidates.getJSONObject(0).optJSONObject("content")
                val parts = contentObj?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text", "") ?: ""

                if (text.isBlank()) {
                    return@withContext Result.failure(IOException("Received blank text from Google Vertex."))
                }

                val parsed = parseRobustJson(text)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Network error calling Google Vertex"))
        }
    }

    private suspend fun generateVertexText(
        settings: AppSettings,
        messages: List<TransmittedMessage>,
        maxTokens: Int,
        temperature: Float,
        requestType: String = "general"
    ): Result<String> = withContext(Dispatchers.IO) {
        val model = settings.vertexModelId.trim().ifBlank { "gemini-4-argon" }
        if (settings.vertexApiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Google Vertex API key is missing. Please enter your key in Settings."))
        }

        val (systemParts, contentsArray) = buildGeminiContents(messages)

        val requestJson = JSONObject().apply {
            if (systemParts.length() > 0) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", systemParts)
                })
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "text/plain")
                put("temperature", temperature)
                put("topP", settings.topP)
                put("maxOutputTokens", maxTokens)
            })
        }

        val url = "https://aiplatform.googleapis.com/v1/projects/973206776830/locations/global/publishers/google/models/$model:generateContent?key=${settings.vertexApiKey.trim()}"
        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseString = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val rawErr = try {
                        val errObj = JSONObject(responseString)
                        val err = errObj.optJSONObject("error")
                        err?.optString("message", responseString) ?: responseString
                    } catch (e: Exception) {
                        responseString
                    }

                    val code = response.code
                    val userFriendly = when (code) {
                        400 -> "Bad Request (400): $rawErr"
                        401, 403 -> "Authentication error ($code): Invalid Google Vertex API key or unauthorized project access."
                        404 -> "Model not found (404): Vertex model '$model' is not accessible."
                        429 -> "Rate limit (429): Google Vertex quota exceeded or capacity exhausted. Please wait a moment."
                        else -> "Google Vertex HTTP $code: $rawErr"
                    }
                    val errorDetail = ApiErrorDetail(
                        summary = userFriendly,
                        requestType = requestType,
                        modelId = model,
                        endpointRegion = "Global (aiplatform.googleapis.com)",
                        rawProviderError = rawErr,
                        httpStatusCode = code
                    )
                    return@withContext Result.failure(errorDetail)
                }

                val responseJson = JSONObject(responseString)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext Result.failure(IOException("Empty text completion response from Google Vertex."))
                }

                val contentObj = candidates.getJSONObject(0).optJSONObject("content")
                val parts = contentObj?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text", "")?.trim() ?: ""

                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Network error calling Google Vertex"))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Google Gemini API / AI Studio Completion & Text
    // ---------------------------------------------------------------------------------------------

    private suspend fun generateGeminiCompletion(
        settings: AppSettings,
        messages: List<TransmittedMessage>,
        diagnosticMetadata: DiagnosticMetadata? = null
    ): Result<ParsedTurnResponse> = withContext(Dispatchers.IO) {
        val model = settings.geminiModelId.trim().ifBlank { "gemini-2.5-flash" }
        if (settings.geminiApiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Google Gemini API key is missing. Please enter your key in Settings."))
        }

        val (systemParts, contentsArray) = buildGeminiContents(messages)

        val requestJson = JSONObject().apply {
            if (systemParts.length() > 0) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", systemParts)
                })
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", settings.temperature)
                put("topP", settings.topP)
                put("maxOutputTokens", settings.maxTokens)
            })
        }

        if (diagnosticMetadata != null) {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            val genSettings = mapOf<String, Any>(
                "temperature" to settings.temperature,
                "topP" to settings.topP,
                "maxTokens" to settings.maxTokens,
                "contextCompressionEnabled" to settings.contextCompressionEnabled,
                "inputTokenBudget" to settings.inputTokenBudget
            )
            val modsMeta = diagnosticMetadata.modules.map { mod ->
                mapOf<String, Any>(
                    "id" to mod.id,
                    "name" to mod.name,
                    "enabled" to mod.enabled,
                    "role" to mod.role,
                    "placement" to mod.placement,
                    "depth" to mod.depth,
                    "order" to mod.order,
                    "scope" to mod.scope
                )
            }
            lastGameplayRequest = LastGameplayRequestDiagnostic(
                provider = "gemini",
                endpoint = endpoint,
                modelId = model,
                scope = diagnosticMetadata.scope,
                generationSettings = genSettings,
                modulesMetadata = modsMeta,
                serializedRequest = JSONObject(requestJson.toString())
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${settings.geminiApiKey.trim()}"
        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseString = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val rawErr = try {
                        val errObj = JSONObject(responseString)
                        val err = errObj.optJSONObject("error")
                        err?.optString("message", responseString) ?: responseString
                    } catch (e: Exception) {
                        responseString
                    }

                    val code = response.code
                    val userFriendly = when (code) {
                        400 -> "Bad Request (400): $rawErr"
                        401, 403 -> "Authentication error ($code): Invalid Google Gemini API key."
                        404 -> "Model not found (404): Gemini model '$model' is not accessible."
                        429 -> "Rate limit (429): Google Gemini API quota exceeded or capacity exhausted. Please wait a moment."
                        else -> "Google Gemini HTTP $code: $rawErr"
                    }
                    val errorDetail = ApiErrorDetail(
                        summary = userFriendly,
                        requestType = "gameplay",
                        modelId = model,
                        endpointRegion = "Global (generativelanguage.googleapis.com)",
                        rawProviderError = rawErr,
                        httpStatusCode = code
                    )
                    return@withContext Result.failure(errorDetail)
                }

                val responseJson = JSONObject(responseString)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext Result.failure(IOException("Empty completion response from Google Gemini API."))
                }

                val contentObj = candidates.getJSONObject(0).optJSONObject("content")
                val parts = contentObj?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text", "") ?: ""

                if (text.isBlank()) {
                    return@withContext Result.failure(IOException("Received blank text from Google Gemini API."))
                }

                val parsed = parseRobustJson(text)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Network error calling Google Gemini API"))
        }
    }

    private suspend fun generateGeminiText(
        settings: AppSettings,
        messages: List<TransmittedMessage>,
        maxTokens: Int,
        temperature: Float,
        requestType: String = "general"
    ): Result<String> = withContext(Dispatchers.IO) {
        val model = settings.geminiModelId.trim().ifBlank { "gemini-2.5-flash" }
        if (settings.geminiApiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Google Gemini API key is missing. Please enter your key in Settings."))
        }

        val (systemParts, contentsArray) = buildGeminiContents(messages)

        val requestJson = JSONObject().apply {
            if (systemParts.length() > 0) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", systemParts)
                })
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "text/plain")
                put("temperature", temperature)
                put("topP", settings.topP)
                put("maxOutputTokens", maxTokens)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${settings.geminiApiKey.trim()}"
        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseString = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val rawErr = try {
                        val errObj = JSONObject(responseString)
                        val err = errObj.optJSONObject("error")
                        err?.optString("message", responseString) ?: responseString
                    } catch (e: Exception) {
                        responseString
                    }

                    val code = response.code
                    val userFriendly = when (code) {
                        400 -> "Bad Request (400): $rawErr"
                        401, 403 -> "Authentication error ($code): Invalid Google Gemini API key."
                        404 -> "Model not found (404): Gemini model '$model' is not accessible."
                        429 -> "Rate limit (429): Google Gemini API quota exceeded or capacity exhausted. Please wait a moment."
                        else -> "Google Gemini HTTP $code: $rawErr"
                    }
                    val errorDetail = ApiErrorDetail(
                        summary = userFriendly,
                        requestType = requestType,
                        modelId = model,
                        endpointRegion = "Global (generativelanguage.googleapis.com)",
                        rawProviderError = rawErr,
                        httpStatusCode = code
                    )
                    return@withContext Result.failure(errorDetail)
                }

                val responseJson = JSONObject(responseString)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext Result.failure(IOException("Empty text completion response from Google Gemini API."))
                }

                val contentObj = candidates.getJSONObject(0).optJSONObject("content")
                val parts = contentObj?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text", "")?.trim() ?: ""

                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Network error calling Google Gemini API"))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Custom OpenAI-Compatible Endpoint Completion & Text
    // ---------------------------------------------------------------------------------------------

    private suspend fun generateCustomCompletion(
        settings: AppSettings,
        messages: List<TransmittedMessage>,
        diagnosticMetadata: DiagnosticMetadata? = null
    ): Result<ParsedTurnResponse> = withContext(Dispatchers.IO) {
        val model = settings.customModelId.trim().ifBlank { "gpt-4o" }
        val rawBase = settings.customBaseUrl.trim().removeSuffix("/")
        if (rawBase.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Custom endpoint Base URL is missing. Please enter the Base URL in Settings."))
        }

        val url = if (rawBase.endsWith("/chat/completions")) rawBase else "$rawBase/chat/completions"

        val messagesArray = JSONArray()
        for (m in messages) {
            messagesArray.put(m.toJsonObject())
        }

        val requestJson = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", settings.temperature)
            put("top_p", settings.topP)
            put("max_tokens", settings.maxTokens)
            put("response_format", JSONObject().apply { put("type", "json_object") })
        }

        if (diagnosticMetadata != null) {
            val genSettings = mapOf<String, Any>(
                "temperature" to settings.temperature,
                "topP" to settings.topP,
                "maxTokens" to settings.maxTokens,
                "contextCompressionEnabled" to settings.contextCompressionEnabled,
                "inputTokenBudget" to settings.inputTokenBudget
            )
            val modsMeta = diagnosticMetadata.modules.map { mod ->
                mapOf<String, Any>(
                    "id" to mod.id,
                    "name" to mod.name,
                    "enabled" to mod.enabled,
                    "role" to mod.role,
                    "placement" to mod.placement,
                    "depth" to mod.depth,
                    "order" to mod.order,
                    "scope" to mod.scope
                )
            }
            lastGameplayRequest = LastGameplayRequestDiagnostic(
                provider = "custom",
                endpoint = url,
                modelId = model,
                scope = diagnosticMetadata.scope,
                generationSettings = genSettings,
                modulesMetadata = modsMeta,
                serializedRequest = JSONObject(requestJson.toString())
            )
        }

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(body)

        if (settings.customApiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${settings.customApiKey.trim()}")
        }

        try {
            client.newCall(reqBuilder.build()).execute().use { response ->
                val responseString = response.body?.string() ?: ""

                // Fallback: If endpoint rejects response_format (common on local models like Ollama/LM Studio)
                if (!response.isSuccessful && response.code == 400 && responseString.contains("response_format", ignoreCase = true)) {
                    val retryJson = JSONObject().apply {
                        put("model", model)
                        put("messages", messagesArray)
                        put("temperature", settings.temperature)
                        put("top_p", settings.topP)
                        put("max_tokens", settings.maxTokens)
                    }
                    val retryReq = Request.Builder()
                        .url(url)
                        .header("Content-Type", "application/json")
                        .apply {
                            if (settings.customApiKey.isNotBlank()) {
                                header("Authorization", "Bearer ${settings.customApiKey.trim()}")
                            }
                        }
                        .post(retryJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    client.newCall(retryReq).execute().use { retryResponse ->
                        val retryString = retryResponse.body?.string() ?: ""
                        if (!retryResponse.isSuccessful) {
                            return@withContext handleCustomHttpError(retryResponse.code, retryString, model, url)
                        }
                        return@withContext parseOpenAiChatResponse(retryString)
                    }
                }

                if (!response.isSuccessful) {
                    return@withContext handleCustomHttpError(response.code, responseString, model, url)
                }

                parseOpenAiChatResponse(responseString)
            }
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Network error connecting to custom endpoint $url"))
        }
    }

    private suspend fun generateCustomText(
        settings: AppSettings,
        messages: List<TransmittedMessage>,
        maxTokens: Int,
        temperature: Float,
        requestType: String = "general"
    ): Result<String> = withContext(Dispatchers.IO) {
        val model = settings.customModelId.trim().ifBlank { "gpt-4o" }
        val rawBase = settings.customBaseUrl.trim().removeSuffix("/")
        if (rawBase.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Custom endpoint Base URL is missing. Please enter the Base URL in Settings."))
        }

        val url = if (rawBase.endsWith("/chat/completions")) rawBase else "$rawBase/chat/completions"

        val messagesArray = JSONArray()
        for (m in messages) {
            messagesArray.put(m.toJsonObject())
        }

        val requestJson = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", temperature)
            put("top_p", settings.topP)
            put("max_tokens", maxTokens)
        }

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(body)

        if (settings.customApiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${settings.customApiKey.trim()}")
        }

        try {
            client.newCall(reqBuilder.build()).execute().use { response ->
                val responseString = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val rawErr = try {
                        val errObj = JSONObject(responseString)
                        val err = errObj.optJSONObject("error")
                        err?.optString("message", responseString) ?: responseString
                    } catch (e: Exception) {
                        responseString
                    }

                    val code = response.code
                    val userFriendly = when (code) {
                        401 -> "Authentication error (401): Invalid API key for custom endpoint."
                        404 -> "Not found (404): Check Base URL ($url) or model '$model'."
                        429 -> "Rate limit (429): Custom endpoint capacity or rate limit reached."
                        else -> "Custom endpoint HTTP $code: $rawErr"
                    }
                    val errorDetail = ApiErrorDetail(
                        summary = userFriendly,
                        requestType = requestType,
                        modelId = model,
                        endpointRegion = url,
                        rawProviderError = rawErr,
                        httpStatusCode = code
                    )
                    return@withContext Result.failure(errorDetail)
                }

                val responseJson = JSONObject(responseString)
                val choices = responseJson.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return@withContext Result.failure(IOException("Empty text completion response from custom endpoint."))
                }

                val messageObj = choices.getJSONObject(0).optJSONObject("message")
                val content = messageObj?.optString("content", "")?.trim() ?: ""
                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Network error connecting to custom endpoint $url"))
        }
    }

    private fun handleCustomHttpError(code: Int, responseString: String, model: String, url: String): Result<ParsedTurnResponse> {
        val rawErr = try {
            val errObj = JSONObject(responseString)
            val err = errObj.optJSONObject("error")
            err?.optString("message", responseString) ?: responseString
        } catch (e: Exception) {
            responseString
        }

        val userFriendly = when (code) {
            401 -> "Authentication error (401): Invalid API key for custom endpoint."
            404 -> "Not found (404): Check Base URL ($url) or model '$model'."
            429 -> "Rate limit (429): Custom endpoint capacity or rate limit reached."
            else -> "Custom endpoint HTTP $code: $rawErr"
        }
        val errorDetail = ApiErrorDetail(
            summary = userFriendly,
            requestType = "gameplay",
            modelId = model,
            endpointRegion = url,
            rawProviderError = rawErr,
            httpStatusCode = code
        )
        return Result.failure(errorDetail)
    }

    private fun parseOpenAiChatResponse(responseString: String): Result<ParsedTurnResponse> {
        val responseJson = JSONObject(responseString)
        val choices = responseJson.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            return Result.failure(IOException("Empty completion response from OpenAI-compatible endpoint."))
        }

        val messageObj = choices.getJSONObject(0).optJSONObject("message")
        val content = messageObj?.optString("content", "") ?: ""

        val parsed = parseRobustJson(content)
        return Result.success(parsed)
    }

    // ---------------------------------------------------------------------------------------------
    // Shared Gemini Prompt Builder (Vertex & Gemini AI Studio)
    // ---------------------------------------------------------------------------------------------

    private fun buildGeminiContents(messages: List<TransmittedMessage>): Pair<JSONArray, JSONArray> {
        val systemParts = JSONArray()
        var leadingSystemIndex = 0
        while (leadingSystemIndex < messages.size && messages[leadingSystemIndex].role.equals("system", ignoreCase = true)) {
            val content = messages[leadingSystemIndex].content.trim()
            if (content.isNotEmpty()) {
                systemParts.put(JSONObject().apply { put("text", content) })
            }
            leadingSystemIndex++
        }

        val contentsArray = JSONArray()

        for (i in leadingSystemIndex until messages.size) {
            val m = messages[i]
            val trimmedContent = m.content.trim()
            if (trimmedContent.isEmpty()) continue

            val role = when {
                m.role.equals("system", ignoreCase = true) || m.role.equals("tool", ignoreCase = true) -> "user"
                m.role.equals("assistant", ignoreCase = true) || m.role.equals("model", ignoreCase = true) -> "model"
                else -> "user"
            }

            // Merge consecutive messages with the same role
            if (contentsArray.length() > 0 && contentsArray.getJSONObject(contentsArray.length() - 1).getString("role") == role) {
                val lastEntry = contentsArray.getJSONObject(contentsArray.length() - 1)
                val parts = lastEntry.getJSONArray("parts")
                var merged = false
                for (pIdx in 0 until parts.length()) {
                    val partObj = parts.getJSONObject(pIdx)
                    if (partObj.has("text")) {
                        val existingText = partObj.getString("text")
                        partObj.put("text", "$existingText\n\n$trimmedContent")
                        merged = true
                        break
                    }
                }
                if (!merged) {
                    parts.put(JSONObject().apply { put("text", trimmedContent) })
                }
            } else {
                val partsArray = JSONArray().apply {
                    put(JSONObject().apply { put("text", trimmedContent) })
                }
                contentsArray.put(JSONObject().apply {
                    put("role", role)
                    put("parts", partsArray)
                })
            }
        }

        // If no user or model turn was supplied, add a minimal user prompt
        if (contentsArray.length() == 0) {
            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply { put(JSONObject().apply { put("text", "Generate next turn.") }) })
            })
        }

        return Pair(systemParts, contentsArray)
    }

    // ---------------------------------------------------------------------------------------------
    // Connection Test
    // ---------------------------------------------------------------------------------------------

    suspend fun testConnection(settings: AppSettings): Result<String> = withContext(Dispatchers.IO) {
        val (providerName, keyMissing) = when (settings.provider) {
            "vertex" -> "Google Vertex AI" to settings.vertexApiKey.isBlank()
            "gemini" -> "Google Gemini API" to settings.geminiApiKey.isBlank()
            "openrouter" -> "OpenRouter" to settings.openRouterApiKey.isBlank()
            "custom" -> "Custom OpenAI Endpoint" to settings.customBaseUrl.isBlank()
            else -> settings.provider to true
        }

        if (keyMissing) {
            val missingPrompt = if (settings.provider == "custom") "Please provide a Base URL." else "Please provide an API key."
            return@withContext Result.failure(IllegalStateException(missingPrompt))
        }

        val messages = listOf(
            TransmittedMessage(role = "system", content = "Respond with JSON only: {\"story\": \"Connection confirmed.\"}"),
            TransmittedMessage(role = "user", content = "Ping")
        )
        val res = generateCompletion(settings, messages)
        res.map { "Successfully connected to ${settings.activeModelId} ($providerName)!" }
    }

    // ---------------------------------------------------------------------------------------------
    // Robust Turn Response JSON Parser
    // ---------------------------------------------------------------------------------------------

    private fun parseRobustJson(raw: String): ParsedTurnResponse {
        var clean = raw.trim()

        // Strip markdown code fences
        if (clean.contains("```")) {
            val fenceMatch = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""", RegexOption.IGNORE_CASE).find(clean)
            if (fenceMatch != null) {
                clean = fenceMatch.groupValues[1].trim()
            }
        }

        // Find outermost JSON brackets
        val startIdx = clean.indexOf('{')
        val endIdx = clean.lastIndexOf('}')
        if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            clean = clean.substring(startIdx, endIdx + 1)
        }

        // Fix common trailing commas before } or ]
        clean = clean.replace(Regex(""",\s*\}"""), "}").replace(Regex(""",\s*\]"""), "]")

        val json = try {
            JSONObject(clean)
        } catch (e: Exception) {
            throw java.io.IOException("Failed to parse model response as valid JSON: ${e.message}")
        }

        val story = json.optString("story", "").trim()
        if (story.isBlank()) {
            throw java.io.IOException("Model returned empty story text in response.")
        }
        val speech = json.optString("speech", "").trim()
        val situation = if (json.has("situation") && !json.isNull("situation")) {
            json.optString("situation").trim().ifBlank { null }
        } else null
        val locationName = if (json.has("locationName")) json.optString("locationName") else null

        val objStatus = json.optString("objectiveStatus", "ongoing")
        val objReason = if (json.has("objectiveResolutionReason") && !json.isNull("objectiveResolutionReason")) json.optString("objectiveResolutionReason") else null

        val trustDelta = json.optInt("trustDelta", 0)
        val dispositionDelta = json.optInt("dispositionDelta", 0)
        val timeSpent = json.optInt("timeSpentMinutes", 1).coerceAtLeast(0)

        val impressionUpdate = if (json.has("impressionUpdate") && !json.isNull("impressionUpdate")) json.optString("impressionUpdate") else null
        val consequenceNote = if (json.has("consequenceNote") && !json.isNull("consequenceNote")) json.optString("consequenceNote") else null

        val continuityUpdatesList = mutableListOf<JSONObject>()
        val cuArr = json.optJSONArray("continuityUpdates")
        if (cuArr != null) {
            for (i in 0 until cuArr.length()) {
                cuArr.optJSONObject(i)?.let { continuityUpdatesList.add(it) }
            }
        }

        val actionSuggestionsList = mutableListOf<ActionOption>()
        val asArr = json.optJSONArray("actionSuggestions") ?: json.optJSONArray("physicalSuggestions")
        if (asArr != null) {
            for (i in 0 until asArr.length()) {
                val item = asArr.opt(i)
                if (item is JSONObject) {
                    val label = item.optString("label", item.optString("action", "")).trim()
                    val prose = item.optString("prose", item.optString("narrative", item.optString("display", item.optString("text", "")))).trim()
                    val actualProse = if (prose.isNotBlank() && prose != label) prose else (if (item.has("action") && item.optString("action").trim() != label) item.optString("action").trim() else "")
                    if (label.isNotBlank()) {
                        actionSuggestionsList.add(
                            ActionOption(
                                label = label,
                                prose = actualProse,
                                verb = item.optString("verb", "Act"),
                                timeCostMinutes = item.optInt("timeCostMinutes", 1),
                                category = item.optString("category", "action")
                            )
                        )
                    }
                } else if (item is String && item.isNotBlank()) {
                    actionSuggestionsList.add(
                        ActionOption(
                            label = item.trim(),
                            prose = "",
                            verb = "Act",
                            timeCostMinutes = 1,
                            category = "action"
                        )
                    )
                }
            }
        }

        val speechSuggestionsList = mutableListOf<String>()
        val ssArr = json.optJSONArray("speechSuggestions")
        if (ssArr != null) {
            for (i in 0 until ssArr.length()) {
                val line = ssArr.optString(i, "").trim()
                if (line.isNotBlank()) {
                    speechSuggestionsList.add(line)
                }
            }
        }

        // Opening-only scenario sheet and starting equipment
        val sheetObj = json.optJSONObject("hiddenScenario")
        val hidden = HiddenScenario.fromJsonObject(sheetObj)
        val objObj = sheetObj?.optJSONObject("objective")
        val authoredObjective = if (objObj != null) {
            ScenarioObjectiveRecord(
                title = objObj.optString("title", "").trim(),
                detail = objObj.optString("stakes", objObj.optString("detail", "")).trim(),
                successCondition = objObj.optString("success", objObj.optString("successCondition", "")).trim(),
                failureCondition = objObj.optString("failure", objObj.optString("failureCondition", "")).trim()
            )
        } else null
        fun sheetString(key: String): String? =
            if (sheetObj != null && sheetObj.has(key) && !sheetObj.isNull(key)) sheetObj.optString(key).trim().ifBlank { null } else null

        val startingPossessionsList = mutableListOf<String>()
        val spArr = json.optJSONArray("startingPossessions")
        if (spArr != null) {
            for (i in 0 until spArr.length()) {
                val item = spArr.opt(i)
                if (item is String && item.isNotBlank()) {
                    startingPossessionsList.add(item.trim())
                } else if (item is JSONObject) {
                    val name = item.optString("name", item.optString("item", "")).trim()
                    if (name.isNotBlank()) startingPossessionsList.add(name)
                }
            }
        }

        val startingConstraintsList = mutableListOf<String>()
        val scArr = json.optJSONArray("startingConstraints")
        if (scArr != null) {
            for (i in 0 until scArr.length()) {
                val c = scArr.optString(i, "").trim()
                if (c.isNotBlank()) startingConstraintsList.add(c)
            }
        }

        return ParsedTurnResponse(
            story = story,
            speech = speech,
            situation = situation,
            objectiveStatus = objStatus,
            objectiveResolutionReason = objReason,
            trustDelta = trustDelta,
            dispositionDelta = dispositionDelta,
            timeSpentMinutes = timeSpent,
            impressionUpdate = impressionUpdate,
            consequenceNote = consequenceNote,
            continuityUpdates = continuityUpdatesList,
            actionSuggestions = actionSuggestionsList,
            speechSuggestions = speechSuggestionsList,
            locationName = locationName,
            hiddenScenario = hidden,
            authoredObjective = authoredObjective,
            assignedPlayerName = sheetString("playerName"),
            assignedPlayerRole = sheetString("playerRole"),
            startTime = sheetString("startTime") ?: if (json.has("startTime") && !json.isNull("startTime")) json.optString("startTime").trim().ifBlank { null } else null,
            startingPossessions = startingPossessionsList,
            startingConstraints = startingConstraintsList
        )
    }
}
