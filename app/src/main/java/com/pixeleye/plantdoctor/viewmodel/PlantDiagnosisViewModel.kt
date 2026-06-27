package com.pixeleye.plantdoctor.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.pixeleye.plantdoctor.BuildConfig
import com.pixeleye.plantdoctor.R
import com.pixeleye.plantdoctor.data.UserPreferencesRepository
import com.pixeleye.plantdoctor.data.api.DiagnosisResponse
import com.pixeleye.plantdoctor.data.api.PlantScanDto
import com.pixeleye.plantdoctor.data.api.SupabaseClientProvider
import com.pixeleye.plantdoctor.data.api.PlantScanRepository
import com.pixeleye.plantdoctor.data.api.UserQuotaRepository
import com.pixeleye.plantdoctor.data.api.UserQuotaDto
import com.pixeleye.plantdoctor.utils.compressImageHighQuality
import com.pixeleye.plantdoctor.utils.decodeDownscaledBitmap
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import com.pixeleye.plantdoctor.utils.LocationHelper
import androidx.annotation.Keep

sealed class DiagnosisState {
    data object Idle : DiagnosisState()
    data object Loading : DiagnosisState()
    data class Success(val result: DiagnosisResponse) : DiagnosisState()
    data class Error(val message: String) : DiagnosisState()
}

sealed class UploadState {
    data object Idle : UploadState()
    data object Uploading : UploadState()
    data class Success(val imageUrl: String, val scanId: String, val parentId: String?) : UploadState()
    data class Error(val message: String) : UploadState()
}

/**
 * Intermediate model for parsing Gemini's structured JSON response.
 * Determines whether the image is a plant before committing to storage/DB writes.
 */
@Keep
internal data class GeminiAnalysisResponse(
    @SerializedName("is_plant") val isPlant: Boolean,
    @SerializedName("plant_name") val plantName: String?,
    @SerializedName("watering_time") val wateringTime: String?,
    @SerializedName("fertilizing_time") val fertilizingTime: String?,
    @SerializedName("diagnosis_summary") val diagnosisSummary: String,
    @SerializedName("organic_treatments") val organicTreatments: List<String>,
    @SerializedName("chemical_treatments") val chemicalTreatments: List<String>,
    @SerializedName("health_status_percentage") val healthStatusPercentage: Int? = null
)

class PlantDiagnosisViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val plantScanRepository: PlantScanRepository,
    private val userQuotaRepository: UserQuotaRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PlantDiagnosisVM"
        private const val STORAGE_BUCKET = "plant-images"
        private const val TABLE_NAME = "plant_scans"
    }

    private val exhaustedKeys = mutableSetOf<String>()
    private var lastExhaustedResetDate: String = ""

    private fun checkAndResetExhaustedKeys() {
        val today = LocalDate.now().toString()
        if (lastExhaustedResetDate != today) {
            exhaustedKeys.clear()
            lastExhaustedResetDate = today
            Log.d(TAG, "New day detected. Cleared exhaustedKeys cache.")
        }
    }

    private fun createGenerativeModel(apiKey: String): GenerativeModel {
        return GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey,
            systemInstruction = content {
                text("""You are an expert, highly authoritative agricultural and botanical pathologist. Analyze the provided image to diagnose plant health.

You MUST return ONLY a single valid JSON object. No markdown, no prose. The JSON must strictly follow this structure:

{
  "is_plant": true or false,
  "plant_name": "The common name of the plant (e.g. Tomato, Rose, Mango, etc.) in the user's preferred language or null if not a plant",
  "health_status_percentage": 75,
  "watering_time": "The recommended daily time for watering in 'HH:mm' format (e.g., '08:00' or '17:00') or null if not a plant",
  "fertilizing_time": "The recommended daily time for applying treatment/fertilizer/medicine in 'HH:mm' format (e.g., '09:00' or '16:30') or null if not a plant",
  "diagnosis_summary": "A detailed explanation of the disease, pest, or nutrient deficiency.",
  "organic_treatments": ["Organic step 1", "Natural step 2"],
  "chemical_treatments": ["Chemical step 1", "Agrochemical step 2"]
}

Rules:
1. If the image is NOT a plant: set "is_plant" to false, explain what it is in "diagnosis_summary", and leave both treatment arrays empty [].
2. If it IS a plant: set "is_plant" to true. Provide a precise "diagnosis_summary".
3. "organic_treatments": List actionable, natural, DIY, or organic farming methods (e.g., Neem oil, pruning, compost).
4. "chemical_treatments": List specific, commercially available agrochemical treatments, synthetic fertilizers, or pesticides.
5. CRITICAL SAFETY RULE: For chemical treatments, suggest the active ingredient or class of chemical, but STRICTLY advise the user to 'read the manufacturer's label for dosage and safety'.
6. The lists "organic_treatments" and "chemical_treatments" MUST contain ONLY direct, actionable, step-by-step treatment items. Any general advice, localization availability notes, supplier/brand information, or descriptive paragraphs MUST be written inside "diagnosis_summary" instead of the treatment lists.
7. ENVIRONMENT-AWARE ANALYSIS: Analyze the visual background and lighting of the image to determine if the plant is located indoors (e.g. a room, windowsill, indoor pot) or outdoors (e.g. garden, yard, field).
   - If Outdoors: Directly align your watering schedule, treatment application times, and protection tips with the provided 5-day weather forecast.
   - If Indoors: Tailor recommendations for indoor environments (e.g. emphasize airflow/ventilation to prevent fungal buildup, ensure proper light, and warn against overwatering). Clarify that the outdoor weather has less direct impact but still affects ambient conditions.
   - If visual context is ambiguous or a close-up: State this in the "diagnosis_summary" and provide conditional advice (e.g., "If this plant is outdoors, do X; if it is indoors, do Y").
8. "health_status_percentage": An integer between 0 and 100 representing the estimated health/recovery percentage of the plant (0 = completely dead/severely diseased, 100 = perfectly healthy). If this is a follow-up/progress check-in (when historical timeline is provided below), analyze the new image, compare it with the history log, and increase or decrease this percentage based on signs of recovery or worsening compared to the previous check-in. If it is a first/initial scan, estimate the initial health level (e.g. 30% if heavily diseased, 80% if minor spots). Set to null if not a plant.
9. Translate only values, keep JSON keys unchanged. """.trimIndent())
            },
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 32
                topP = 0.95f
                maxOutputTokens = 8192
                responseMimeType = "application/json"
            },
            safetySettings = listOf(
                SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
                SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
                SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
            )
        )
    }

    private val supabaseClient by lazy {
        SupabaseClientProvider.getClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        )
    }

    private val _diagnosisState = MutableStateFlow<DiagnosisState>(DiagnosisState.Idle)
    val diagnosisState: StateFlow<DiagnosisState> = _diagnosisState.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    private val _scanCount = MutableStateFlow(0)
    val scanCount: StateFlow<Int> = _scanCount.asStateFlow()

    private val _snackbarEvent = MutableStateFlow<com.pixeleye.plantdoctor.ui.components.SnackbarState?>(null)
    val snackbarEvent: StateFlow<com.pixeleye.plantdoctor.ui.components.SnackbarState?> = _snackbarEvent.asStateFlow()

    suspend fun checkQuota(): UserQuotaDto {
        return try {
            val quota = userQuotaRepository.checkQuota()
            _scanCount.value = quota.dailyCount
            quota
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check quota", e)
            throw e
        }
    }

    suspend fun incrementQuota() {
        try {
            userQuotaRepository.incrementQuota()
            _scanCount.value = _scanCount.value + 1
        } catch (e: Exception) {
            Log.e(TAG, "Failed to increment quota", e)
        }
    }

    fun analyzePlant(image: Bitmap, userNotes: String = "", imageUri: Uri? = null, locationStr: String? = null, context: Context? = null, isPremium: Boolean = false, parentId: String? = null) {
        viewModelScope.launch {
            _diagnosisState.value = DiagnosisState.Loading
            _uploadState.value = UploadState.Idle

            try {
                // Local image verification with ML Kit
                val isPlantLocal = checkIfImageContainsPlant(image)
                if (!isPlantLocal) {
                    val errorMsg = context?.getString(R.string.not_a_plant_error)
                        ?: "The image does not appear to contain a plant. Please try again with a plant photo."
                    Log.d(TAG, "Local validation rejected the image.")
                    _diagnosisState.value = DiagnosisState.Error(errorMsg)
                    return@launch
                }

                val threadScans = if (parentId != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val local = plantScanRepository.getThreadScansLocal(parentId)
                            if (local.isNotEmpty()) local else plantScanRepository.getThreadScansRemote(parentId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error fetching thread scans for parentId: $parentId", e)
                            emptyList()
                        }
                    }
                } else {
                    emptyList()
                }

                // 1. UNIVERSAL QUOTA CHECK (Fair Use Policy: 3 for Free, 50 for Pro)
                val currentQuota = try {
                    checkQuota()
                } catch (e: Exception) {
                    Log.e(TAG, "Quota check failed", e)
                    // If DB is down, we allow it for Free users but log it as a risk.
                    // For now, we'll be strict to protect the API costs.
                    null
                }

                if (currentQuota != null) {
                    val maxLimit = if (isPremium) 50 else 6
                    if (currentQuota.dailyCount >= maxLimit) {
                        val limitMsg = if (isPremium) {
                            "Daily fair-use limit of 50 scans reached for PRO users."
                        } else {
                            "Daily limit of 6 scans reached. Upgrade to PRO for 50 scans/day!"
                        }
                        _diagnosisState.value = DiagnosisState.Error(limitMsg)
                        return@launch
                    }
                }

                // Downscale image for Gemini if context+uri available (saves bandwidth, faster AI response)
                val inputImage = if (context != null && imageUri != null) {
                    withContext(Dispatchers.IO) {
                        decodeDownscaledBitmap(context, imageUri)
                    }
                } else {
                    image
                }

                // Fetch coordinates & weather forecast if not cached yet
                var weatherForecast: String? = com.pixeleye.plantdoctor.data.api.WeatherRepository.cachedForecastText
                if (weatherForecast == null && context != null) {
                    try {
                        val coords = LocationHelper.getCoordinates(context)
                        if (coords != null) {
                            Log.d(TAG, "Weather cache miss. Fetching forecast for coordinates: ${coords.latitude}, ${coords.longitude}")
                            weatherForecast = com.pixeleye.plantdoctor.data.api.WeatherRepository.fetchAndCacheForecast(coords.latitude, coords.longitude)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to dynamically fetch weather during scan", e)
                    }
                }

                // Read saved preferences to personalize the prompt
                val prefs = userPreferencesRepository.userPreferences.first()
                val country = prefs.country
                val aiLanguage = prefs.selectedAiLanguage

                val personalizationContext = buildString {
                    if (country.isNotBlank() || aiLanguage.isNotBlank() || locationStr != null || !weatherForecast.isNullOrBlank()) {
                        appendLine("CONTEXT:")
                        if (aiLanguage.isNotBlank()) {
                            appendLine("- You MUST provide your final JSON structured diagnosis ONLY in $aiLanguage.")
                            appendLine("- The 'plant_name' field MUST also be in $aiLanguage (e.g. 'තක්කාලි' or 'රෝස' if language is Sinhala, 'தக்காளி' or 'ரோஜா' if Tamil). Do not provide the plant name in English if language is $aiLanguage.")
                            appendLine("- CRITICAL: Translate ONLY the values (text content) of the JSON fields. The JSON keys ('is_plant', 'plant_name', 'watering_time', 'fertilizing_time', 'diagnosis_summary', 'organic_treatments', 'chemical_treatments') MUST remain exactly in English as specified in the system instructions. Do not translate the keys.")
                            appendLine("- JSON SAFETY: Do NOT use unescaped double quotes (\") inside any of the JSON string values (especially in 'diagnosis_summary' or 'plant_name'). If you want to quote or highlight a word, use single quotes (') instead. This prevents JSON parsing errors.")
                            if (aiLanguage.equals("Sinhala", ignoreCase = true)) {
                                appendLine("- SAFETY & COMPLIANCE: In Sinhala, describe chemical and organic treatments using mild, safe terminology (e.g., use 'පාලනය සදහා' (for control) or 'ප්‍රතිකාර' (treatments) instead of words meaning poison/toxin like 'විෂ' or 'වස'). Avoid copying long passages verbatim from external websites to prevent automated copyright/citation blocks.")
                            }
                        }

                        // Prefer dynamic robust location over static country preferences
                        val targetLocation = locationStr ?: country
                        if (targetLocation.isNotBlank()) {
                            appendLine("- The user is located at/in: $targetLocation. Suggest agricultural treatments, chemical compositions, and organic solutions that are locally available and commonly used in this region.")
                            appendLine("- Mention specific local brands, agrochemical suppliers, or farming practices relevant to $targetLocation when appropriate. CRITICAL: Any local supplier/brand notes, general availability advice, or regional context MUST be included inside the 'diagnosis_summary' text and NOT as items in the 'chemical_treatments' or 'organic_treatments' lists.")
                        }

                        if (!weatherForecast.isNullOrBlank()) {
                            appendLine("- ENVIRONMENTAL & WEATHER FORECAST CONTEXT:")
                            appendLine("The upcoming 5-day weather forecast at the user's location is:")
                            appendLine(weatherForecast)
                            appendLine("- Factor this weather forecast into your recommended care timeline (e.g., watering schedules, treatment timings). If rain is expected, warn the user about chemical wash-off and advise appropriate protective measures.")
                        }
                        appendLine()
                    }
                }

                val basePrompt = userNotes.ifBlank {
                    "Please analyze this plant image and identify any diseases, pests, or nutrient deficiencies. Provide a detailed treatment plan."
                }

                val historyContext = if (parentId != null && threadScans.isNotEmpty()) {
                    buildString {
                        appendLine("PROGRESS TRACKING / FOLLOW-UP CONTEXT:")
                        appendLine("This is a follow-up check-in for a plant that was diagnosed previously. The historical timeline of this plant is:")
                        
                        val firstScan = threadScans.first()
                        val last10Scans = threadScans.takeLast(10)
                        val selectedScans = if (last10Scans.contains(firstScan)) {
                            last10Scans
                        } else {
                            listOf(firstScan) + last10Scans
                        }

                        selectedScans.forEachIndexed { index, scan ->
                            val prefix = if (scan == firstScan && scan != last10Scans.firstOrNull()) {
                                "Day 1 / Initial Diagnosis"
                            } else {
                                "Recent History Log (Scan Date: ${com.pixeleye.plantdoctor.ui.screens.formatScanDate(scan.createdAt)})"
                            }
                            appendLine("- $prefix:")
                            appendLine("  Diagnosis: ${scan.diseaseTitle}")
                            appendLine("  Treatment Plan & Progress: ${scan.treatmentPlan}")
                        }
                        appendLine()
                        appendLine("INSTRUCTIONS FOR THIS FOLLOW-UP:")
                        appendLine("1. Analyze the new image provided and compare it with the previous states listed above.")
                        appendLine("2. Evaluate if the plant is showing signs of improvement, worsening, or remaining the same.")
                        appendLine("3. Provide a clear summary comparing current status to previous status, noting visual changes.")
                        appendLine("4. Update the organic and chemical treatments list to reflect the next steps. Explain what to continue doing, what to stop, and any new measures to take.")
                        appendLine("5. Start your 'diagnosis_summary' with a clear status header (e.g. 'Status: Improving', 'Status: Worsening', or 'Status: No Change') followed by the comparison analysis.")
                        appendLine("6. CRITICAL SAFETY: If the new image contains a completely different plant species or a different plant compared to the previous context, set the status header to 'Status: Mismatch - Different Plant Detected' and warn the user in the summary, advising them to photograph the original plant. Do not generate treatment plans for mismatched plants.")
                        appendLine("7. CRITICAL: This is a progress update. The plant has already been identified in previous scans. Set the 'plant_name' JSON field to null. Do not attempt to identify or name it.")
                        appendLine()
                    }
                } else {
                    ""
                }

                val fullPrompt = "$personalizationContext$historyContext$basePrompt"

                Log.d(TAG, "Analyzing with context — locationStr=$locationStr, fallbackCountry=$country, aiLanguage=$aiLanguage")

                val inputContent = content {
                    image(inputImage)
                    text(fullPrompt)
                }

                // Fetch candidate keys and shuffle them to distribute API load evenly
                val keysString = BuildConfig.GEMINI_API_KEYS
                val allKeys = if (keysString.isNotBlank()) {
                    keysString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }
                
                val candidateKeys = if (allKeys.isNotEmpty()) allKeys.shuffled() else listOf(BuildConfig.GEMINI_API_KEY)
                
                // Filter out exhausted keys and handle daily reset
                checkAndResetExhaustedKeys()
                val availableKeys = candidateKeys.filter { it !in exhaustedKeys }
                
                if (availableKeys.isEmpty()) {
                    throw Exception("All Gemini API keys are exhausted for today. Please try again tomorrow.")
                }

                var responseText: String? = null
                var lastError: Exception? = null

                for (key in availableKeys) {
                    try {
                        Log.d(TAG, "Attempting analysis with key: ${if (key.length > 8) key.take(8) + "..." else key}")
                        val model = createGenerativeModel(key)
                        
                        val response = withTimeout(45_000L) {
                            model.generateContent(inputContent)
                        }
                        
                        responseText = response.text
                        if (responseText != null) {
                            break // Succeeded!
                        } else {
                            throw Exception("Empty response from AI model.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Analysis failed with key: ${if (key.length > 8) key.take(8) + "..." else key}", e)
                        lastError = e

                        val isQuotaError = e.message?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true ||
                                e.message?.contains("quota", ignoreCase = true) == true ||
                                e.message?.contains("429") == true

                        if (isQuotaError) {
                            Log.w(TAG, "Key is exhausted. Adding to exhaustedKeys cache.")
                            exhaustedKeys.add(key)
                            // Continue loop to try next key
                        } else {
                            // If it's a network error or other error, do not retry other keys to prevent long delay!
                            throw e
                        }
                    }
                }

                val resultText = responseText ?: throw lastError ?: Exception("No response generated from AI model.")



                Log.d(TAG, "Raw Gemini response: $resultText")

                // Parse into the intermediate model that includes is_plant
                val cleanedJson = cleanJsonString(resultText)
                val geminiResult = try {
                    Gson().fromJson(cleanedJson, GeminiAnalysisResponse::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse Gemini JSON: $resultText", e)
                    throw Exception("AI returned an unexpected format. Please try again.")
                }

                // ── Decision gate: is this actually a plant? ────────────
                if (!geminiResult.isPlant) {
                    // Not a plant — show Gemini's descriptive feedback as an error and STOP.
                    // No storage upload. No DB record. No wasted resources.
                    Log.d(TAG, "Image rejected as non-plant: ${geminiResult.diagnosisSummary}")
                    _diagnosisState.value = DiagnosisState.Error(
                        geminiResult.diagnosisSummary.ifBlank {
                            "The image does not appear to contain a plant. Please try again with a plant photo."
                        }
                    )
                    return@launch
                }

                // ── It IS a plant — proceed with full diagnosis flow ─────
                val diagnosisResponse = DiagnosisResponse(
                    summary = geminiResult.diagnosisSummary,
                    organicTreatments = geminiResult.organicTreatments,
                    chemicalTreatments = geminiResult.chemicalTreatments,
                    plantName = geminiResult.plantName,
                    wateringTime = geminiResult.wateringTime,
                    fertilizingTime = geminiResult.fertilizingTime,
                    healthStatusPercentage = geminiResult.healthStatusPercentage
                )

                _diagnosisState.value = DiagnosisState.Success(diagnosisResponse)

                // Format for Supabase storage
                val stringifiedTreatmentPlan = buildString {
                    append(geminiResult.diagnosisSummary)
                    if (geminiResult.organicTreatments.isNotEmpty()) {
                        append("\n\nOrganic Treatments:\n")
                        geminiResult.organicTreatments.forEach { step ->
                            append("- $step\n")
                        }
                    }
                    if (geminiResult.chemicalTreatments.isNotEmpty()) {
                        append("\n\nChemical Treatments:\n")
                        geminiResult.chemicalTreatments.forEach { step ->
                            append("- $step\n")
                        }
                    }
                    if (!geminiResult.plantName.isNullOrBlank()) {
                        append("\n\nPlant Name: ${geminiResult.plantName}")
                    }
                    if (!geminiResult.wateringTime.isNullOrBlank()) {
                        append("\nWatering Time: ${geminiResult.wateringTime}")
                    }
                    if (!geminiResult.fertilizingTime.isNullOrBlank()) {
                        append("\nFertilizing Time: ${geminiResult.fertilizingTime}")
                    }
                    if (geminiResult.healthStatusPercentage != null) {
                        append("\nHealth Level: ${geminiResult.healthStatusPercentage}%")
                    }
                }.trim()

                // ── SUCCESS! Universal Increment ────────────
                incrementQuota()

                // Free User IAM Trigger for 50% discount
                if (!isPremium) {
                    com.onesignal.OneSignal.InAppMessages.addTrigger("scan_done", "true")
                }

                // Upload to Supabase in background (only for confirmed plants)
                uploadToSupabase(
                    context = context,
                    image = inputImage,
                    imageUri = imageUri,
                    diseaseTitle = if (parentId != null) "Plant Progress Update" else "Plant Analysis",
                    treatmentPlan = stringifiedTreatmentPlan,
                    parentId = parentId,
                    healthStatusPercentage = geminiResult.healthStatusPercentage
                )

            } catch (e: Exception) {
                Log.e(TAG, "Gemini analysis failed", e)
                val isTimeout = e is kotlinx.coroutines.TimeoutCancellationException ||
                        e is java.net.SocketTimeoutException ||
                        e.message?.contains("timeout", ignoreCase = true) == true ||
                        e.cause?.message?.contains("timeout", ignoreCase = true) == true
                
                val errorMessage = if (isTimeout) {
                    "The analysis timed out. Please try again with a better network connection."
                } else {
                    when (e) {
                        is java.net.UnknownHostException, is IOException -> "No internet connection. Please check your network."
                        else -> {
                            val details = "[${e.javaClass.simpleName}] ${e.message ?: "Unknown error"}${if (e.cause != null) " (Cause: ${e.cause?.message})" else ""}"
                            "API Error: $details"
                        }
                    }
                }
                _diagnosisState.value = DiagnosisState.Error(errorMessage)
            }
        }
    }

    private fun uploadToSupabase(
        context: Context?,
        image: Bitmap,
        imageUri: Uri?,
        diseaseTitle: String,
        treatmentPlan: String,
        parentId: String? = null,
        healthStatusPercentage: Int? = null
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            try {
                withTimeout(30_000L) {
                    val currentUser = supabaseClient.auth.currentUserOrNull()
                    val currentUserId = currentUser?.id 
                        ?: throw Exception("User is not logged in. Cannot upload scan.")

                    val imageBytes = withContext(Dispatchers.IO) {
                        if (context != null && imageUri != null) {
                            compressImageHighQuality(context, imageUri)
                        } else {
                            compressBitmapToJpeg(image)
                        }
                    }

                    val fileName = "${UUID.randomUUID()}.webp"
                    val bucket = supabaseClient.storage.from(STORAGE_BUCKET)
                    bucket.upload(path = fileName, data = imageBytes, upsert = false)
                    Log.d(TAG, "Image uploaded to storage: $fileName")

                    val imageUrl = bucket.publicUrl(fileName)
                    Log.d(TAG, "Public URL: $imageUrl")

                    val scanDto = PlantScanDto(
                        id = UUID.randomUUID().toString(),
                        userId = currentUserId,
                        imageUrl = imageUrl,
                        diseaseTitle = diseaseTitle,
                        treatmentPlan = treatmentPlan,
                        parentId = parentId,
                        healthStatusPercentage = healthStatusPercentage
                    )
                    val inserted = plantScanRepository.insertScan(scanDto)
                    Log.d(TAG, "Record inserted into repository and local DB")

                    _uploadState.value = UploadState.Success(
                        imageUrl = imageUrl,
                        scanId = inserted.id ?: scanDto.id ?: "",
                        parentId = inserted.parentId ?: scanDto.parentId
                    )
                }
            } catch (e: Exception) {
                Log.e("SupabaseError", "Supabase upload failed, falling back to local save: ${e.message}", e)
                try {
                    val fallbackScanId = UUID.randomUUID().toString()
                    val guestUserId = supabaseClient.auth.currentUserOrNull()?.id ?: "guest_user"
                    val localImageUrl = imageUri?.toString() ?: ""

                    val fallbackScanDto = PlantScanDto(
                        id = fallbackScanId,
                        userId = guestUserId,
                        imageUrl = localImageUrl,
                        diseaseTitle = diseaseTitle,
                        treatmentPlan = treatmentPlan,
                        parentId = parentId,
                        healthStatusPercentage = healthStatusPercentage
                    )

                    plantScanRepository.insertScanLocal(fallbackScanDto)
                    Log.d(TAG, "Successfully saved scan locally as fallback")

                    _uploadState.value = UploadState.Success(
                        imageUrl = localImageUrl,
                        scanId = fallbackScanId,
                        parentId = parentId ?: fallbackScanId
                    )

                    val isConnected = context?.let { ctx ->
                        try {
                            val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                            val activeNet = cm.activeNetwork
                            val caps = cm.getNetworkCapabilities(activeNet)
                            caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                        } catch (_: Exception) {
                            false
                        }
                    } ?: false

                    val currentUser = supabaseClient.auth.currentUserOrNull()
                    val snackbarMsg = when {
                        !isConnected -> "Saved locally (offline mode)"
                        currentUser == null -> "Saved locally (unauthenticated)"
                        else -> "Saved locally (upload failed: ${e.localizedMessage ?: "sync failed"})"
                    }
                    showSnackbar(snackbarMsg, com.pixeleye.plantdoctor.ui.components.SnackbarType.SUCCESS)
                } catch (localEx: Exception) {
                    Log.e(TAG, "Local save fallback failed", localEx)
                    _uploadState.value = UploadState.Error(
                        e.message ?: "Failed to save scan."
                    )
                }
            }
        }
    }

    private fun compressBitmapToJpeg(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return stream.toByteArray()
    }

    private fun cleanJsonString(input: String): String {
        var cleaned = input.trim()
        if (cleaned.startsWith("```")) {
            val firstNewline = cleaned.indexOf('\n')
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1)
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length - 3)
            }
        }
        return cleaned.trim()
    }

    private suspend fun checkIfImageContainsPlant(bitmap: Bitmap): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    val keywords = listOf(
                        "plant", "leaf", "flower", "tree", "flora", "botany",
                        "vegetable", "fruit", "houseplant", "garden", "herb",
                        "shrub", "organism", "produce"
                    )
                    val containsPlant = labels.any { label ->
                        val text = label.text.lowercase()
                        val confidence = label.confidence
                        confidence >= 0.5f && keywords.any { text.contains(it) }
                    }
                    continuation.resume(containsPlant)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit image labeling failed, falling back to Gemini", e)
                    continuation.resume(true) // Fallback to Gemini
                }
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit initialization failed, falling back to Gemini", e)
            continuation.resume(true) // Fallback to Gemini
        }
    }

    fun resetState() {
        _diagnosisState.value = DiagnosisState.Idle
        _uploadState.value = UploadState.Idle
    }

    fun consumeSnackbarEvent() {
        _snackbarEvent.value = null
    }

    fun showSnackbar(message: String, type: com.pixeleye.plantdoctor.ui.components.SnackbarType) {
        _snackbarEvent.value = com.pixeleye.plantdoctor.ui.components.SnackbarState(message, type)
    }

    class Factory(
        private val userPreferencesRepository: UserPreferencesRepository,
        private val plantScanRepository: PlantScanRepository,
        private val userQuotaRepository: UserQuotaRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlantDiagnosisViewModel(userPreferencesRepository, plantScanRepository, userQuotaRepository) as T
        }
    }
}
