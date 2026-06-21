package com.pixeleye.plantdoctor.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import coil.compose.SubcomposeAsyncImage
import com.pixeleye.plantdoctor.utils.loadInterstitialAd
import com.pixeleye.plantdoctor.utils.showInterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import android.app.Activity
import android.content.Context
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.stringResource
import com.pixeleye.plantdoctor.R

import com.pixeleye.plantdoctor.data.api.DiagnosisResponse
import com.pixeleye.plantdoctor.data.api.PlantScanDto
import com.pixeleye.plantdoctor.data.local.PlantReminderEntity
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import com.pixeleye.plantdoctor.ui.theme.TreatmentAccent
import com.pixeleye.plantdoctor.ui.theme.TreatmentCardBg
import com.pixeleye.plantdoctor.ui.theme.TreatmentCardBorder

// ── Main Result Screen ─────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    imageUri: Uri?,
    diagnosisTitle: String,
    diagnosisData: DiagnosisResponse?,
    confidence: Float? = null,
    isLoading: Boolean = true,
    isSaving: Boolean = false,
    showAd: Boolean = false,
    isPremium: Boolean = false,
    id: String? = null,
    parentId: String? = null,
    threadScans: List<PlantScanDto> = emptyList(),
    trackProgressEnabled: Boolean = true,
    onBack: () -> Unit,
    onNewScan: () -> Unit,
    onOpenPaywall: () -> Unit = onNewScan,
    onTrackProgress: () -> Unit = {},
    onViewResult: (PlantScanDto) -> Unit = {},
    onAddReminders: (
        plantName: String,
        wateringEnabled: Boolean,
        wateringHour: Int,
        wateringMinute: Int,
        fertilizingEnabled: Boolean,
        fertilizingHour: Int,
        fertilizingMinute: Int
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    existingReminders: List<PlantReminderEntity> = emptyList(),
    onGoToReminders: () -> Unit = {},
    onUpdateReminderTime: (PlantReminderEntity, Int, Int) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val currentOnTrackProgress by rememberUpdatedState(onTrackProgress)
    var mInterstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }
    var adShown by remember { mutableStateOf(false) }

    BackHandler {
        onBack()
    }

    // Load and Show Ad if requested and not premium
    LaunchedEffect(Unit) {
        if (showAd && !isPremium) {
            loadInterstitialAd(context) { ad ->
                mInterstitialAd = ad
            }
        }
    }

    LaunchedEffect(mInterstitialAd) {
        if (showAd && !isPremium && mInterstitialAd != null && !adShown) {
            val activity = context as? Activity
            if (activity != null) {
                showInterstitialAd(activity, mInterstitialAd) {
                    adShown = true
                }
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.diagnosis_result_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onNewScan,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Eco,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.scan_new),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = currentOnTrackProgress,
                        enabled = !isLoading && !isSaving && trackProgressEnabled && (id != null || parentId != null),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.saving_dots),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Grass,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.track_progress),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (isLoading) {
            LoadingContent(modifier = Modifier.padding(innerPadding))
        } else {
            ResultContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                imageUri = imageUri,
                diagnosisTitle = diagnosisTitle,
                diagnosisData = diagnosisData,
                confidence = confidence,
                isPremium = isPremium,
                currentScanId = id,
                parentId = parentId,
                threadScans = threadScans,
                onScanClick = onViewResult,
                onOpenPaywall = onOpenPaywall,
                onAddReminders = onAddReminders,
                existingReminders = existingReminders,
                onGoToReminders = onGoToReminders,
                onUpdateReminderTime = onUpdateReminderTime
            )
        }
    }
}

// ── Loading State ──────────────────────────────────────────────
@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
                imageVector = Icons.Default.LocalFlorist,
                contentDescription = stringResource(R.string.no_image_content_desc),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.analyzing_plant),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.analyzing_plant_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .width(200.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer
        )
    }
}

// ── Result Content ─────────────────────────────────────────────
@Composable
private fun ResultContent(
    modifier: Modifier = Modifier,
    imageUri: Uri?,
    diagnosisTitle: String,
    diagnosisData: DiagnosisResponse?,
    confidence: Float?,
    isPremium: Boolean,
    currentScanId: String?,
    parentId: String?,
    threadScans: List<PlantScanDto>,
    onScanClick: (PlantScanDto) -> Unit,
    onOpenPaywall: () -> Unit,
    onAddReminders: (
        plantName: String,
        wateringEnabled: Boolean,
        wateringHour: Int,
        wateringMinute: Int,
        fertilizingEnabled: Boolean,
        fertilizingHour: Int,
        fertilizingMinute: Int
    ) -> Unit,
    existingReminders: List<PlantReminderEntity>,
    onGoToReminders: () -> Unit,
    onUpdateReminderTime: (PlantReminderEntity, Int, Int) -> Unit
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ── Hero Header ────────────────────────────────────────
        DiagnosisHeader(
            imageUri = imageUri,
            diagnosisTitle = diagnosisTitle,
            confidence = confidence
        )

        // ── Progress Timeline ───────────────────────────────
        if (threadScans.size > 1) {
            ProgressTimeline(
                scans = threadScans,
                currentScanId = currentScanId,
                onScanClick = onScanClick,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        // ── Diagnosis Summary Card ─────────────────────────────
        if (diagnosisData != null) {
            DiagnosisSummaryCard(
                text = diagnosisData.summary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            // ── Action Plan Sections ───────────────────────────────
            val hasOrganic = diagnosisData.organicTreatments.isNotEmpty()
            val hasChemical = diagnosisData.chemicalTreatments.isNotEmpty()
            val hasAnyTreatment = hasOrganic || hasChemical

            if (hasAnyTreatment) {
                Text(
                    text = stringResource(R.string.treatment_plan),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                if (hasOrganic) {
                    ExpandableSectionCard(
                        title = stringResource(R.string.organic_treatments_title),
                        icon = Icons.Default.Eco,
                        items = diagnosisData.organicTreatments,
                        isHighlighted = false,
                        isPremium = isPremium,
                        onOpenPaywall = onOpenPaywall,
                        scanId = currentScanId ?: diagnosisData.summary.hashCode().toString(),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }

                if (hasChemical) {
                    ExpandableSectionCard(
                        title = stringResource(R.string.chemical_treatments_title),
                        icon = Icons.Default.MedicalServices,
                        items = diagnosisData.chemicalTreatments,
                        isHighlighted = true,
                        isPremium = isPremium,
                        onOpenPaywall = onOpenPaywall,
                        scanId = currentScanId ?: diagnosisData.summary.hashCode().toString(),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
            }

            // ── Care Reminder Card ─────────────────────────────────
            if (hasAnyTreatment) {
                CareReminderCard(
                    scanId = currentScanId,
                    parentId = parentId,
                    plantNameDefault = if (diagnosisTitle == "Plant Analysis" || diagnosisTitle == "Plant Progress Update" || diagnosisTitle == "Plant Timeline Update") {
                        diagnosisData.plantName ?: "My Plant"
                    } else {
                        if (diagnosisTitle.contains(" - ")) {
                            diagnosisTitle.substringBefore(" - ").trim()
                        } else {
                            diagnosisTitle
                        }
                    },
                    wateringTimeDefault = diagnosisData.wateringTime,
                    fertilizingTimeDefault = diagnosisData.fertilizingTime,
                    existingReminders = existingReminders,
                    onAddReminders = onAddReminders,
                    onGoToReminders = onGoToReminders,
                    onUpdateReminderTime = onUpdateReminderTime,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Diagnosis Header ───────────────────────────────────────────
@Composable
private fun DiagnosisHeader(
    imageUri: Uri?,
    diagnosisTitle: String,
    confidence: Float?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Plant image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                if (imageUri != null) {
                    SubcomposeAsyncImage(
                        model = imageUri,
                        contentDescription = stringResource(R.string.scanned_plant_content_desc),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.LocalFlorist,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.image_unavailable),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFlorist,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f)
                                )
                            )
                        )
                )

                // Confidence badge
                if (confidence != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onTertiary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.confident_percent, (confidence * 100).toInt()),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                        }
                    }
                }
            }

            // Title area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.diagnosis_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = diagnosisTitle.ifBlank { "Plant Analysis Complete" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ── Diagnosis Summary Card ─────────────────────────────────────
@Composable
private fun DiagnosisSummaryCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ai_analysis_summary_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = text.ifBlank { stringResource(R.string.no_analysis_available) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )
        }
    }
}

// ── Expandable Section Card ────────────────────────────────────
@Composable
private fun ExpandableSectionCard(
    title: String,
    icon: ImageVector,
    items: List<String>,
    isHighlighted: Boolean = false,
    isPremium: Boolean = true,
    onOpenPaywall: () -> Unit = {},
    scanId: String? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    val containerColor = if (isHighlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val onHighlightColor = if (isHighlighted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val borderColor = if (isHighlighted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 4.dp else 0.dp)
    ) {
        Column {
            // Section header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = if (isHighlighted) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isHighlighted) {
                            onHighlightColor
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isHighlighted) onHighlightColor else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isHighlighted) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = stringResource(R.string.recommended_badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = onHighlightColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                    tint = if (isHighlighted) onHighlightColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Section items
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                val isBlurred = isHighlighted && !isPremium

                Box {
                    Column(
                        modifier = Modifier
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 16.dp
                            )
                            .then(
                                if (isBlurred) {
                                    Modifier
                                        .blur(
                                            radiusX = 12.dp,
                                            radiusY = 12.dp
                                        )
                                        .pointerInput(Unit) {
                                            detectTapGestures { }
                                        }
                                } else {
                                    Modifier
                                }
                            ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        HorizontalDivider(
                            color = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        items.forEachIndexed { index, itemText ->
                            val itemKey = if (scanId != null) "checked_${scanId}_${itemText.hashCode()}" else null
                            ChecklistRow(
                                text = itemText,
                                highlighted = false,
                                isOnDarkBackground = isHighlighted,
                                persistKey = itemKey
                            )
                        }
                    }

                    if (isBlurred) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onOpenPaywall() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.pro_feature_chemical_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
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
private fun InstructionRow(
    text: String,
    isOnDarkBackground: Boolean = false
) {
    val containerColor = if (isOnDarkBackground) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    }

    val iconColor = if (isOnDarkBackground) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.secondary
    }

    val textColor = if (isOnDarkBackground) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = "Instruction/Tip",
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = FontWeight.Medium,
            lineHeight = 20.sp
        )
    }
}

private fun isInstruction(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.length < 15) return false
    
    val t = trimmed.lowercase()
    val keywords = listOf(
        "apply", "spray", "mix ", "dilute", "use ", "wear", "avoid", 
        "ensure", "repeat", "make sure", "do not", "follow", "spraying",
        "watering", "treatment", "preventative", "infestation", "dosage",
        "application", "morning", "evening", "weeks", "days", "interval",
        "diluted", "gently", "thoroughly", "coat ", "drench", "remove ", "prune"
    )
    return keywords.any { t.contains(it) }
}

// ── Checklist Row ──────────────────────────────────────────────
@Composable
private fun ChecklistRow(
    text: String,
    highlighted: Boolean = false,
    isOnDarkBackground: Boolean = false,
    persistKey: String? = null
) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) {
        context.getSharedPreferences("plant_doctor_checklist_prefs", Context.MODE_PRIVATE)
    }

    var checked by remember(persistKey) {
        mutableStateOf(
            if (persistKey != null) {
                sharedPrefs.getBoolean(persistKey, false)
            } else {
                false
            }
        )
    }

    val onCheckedChange: (Boolean) -> Unit = { isChecked ->
        checked = isChecked
        if (persistKey != null) {
            sharedPrefs.edit().putBoolean(persistKey, isChecked).apply()
        }
    }

    val containerColor = if (highlighted && !isOnDarkBackground) {
        TreatmentAccent.copy(alpha = 0.08f)
    } else if (highlighted && isOnDarkBackground) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }

    val checkboxCheckedColor = if (isOnDarkBackground) {
        MaterialTheme.colorScheme.primary
    } else if (highlighted) {
        TreatmentAccent
    } else {
        MaterialTheme.colorScheme.primary
    }

    val checkboxCheckmarkColor = if (isOnDarkBackground) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.surface
    }

    val checkboxUncheckedColor = if (isOnDarkBackground) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outline
    }

    val textColor = if (isOnDarkBackground) {
        if (checked) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        if (checked) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange(it) },
            modifier = Modifier.size(24.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = checkboxCheckedColor,
                checkmarkColor = checkboxCheckmarkColor,
                uncheckedColor = checkboxUncheckedColor
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = if (highlighted || isOnDarkBackground) FontWeight.Medium else FontWeight.Normal,
            textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            lineHeight = 20.sp
        )
    }
}

// ── Progress Timeline Component ───────────────────────────────
@Composable
fun ProgressTimeline(
    scans: List<PlantScanDto>,
    currentScanId: String?,
    onScanClick: (PlantScanDto) -> Unit,
    modifier: Modifier = Modifier
) {
    if (scans.size <= 1) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ListAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.plant_health_timeline_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            scans.forEachIndexed { index, scan ->
                val isCurrent = scan.id == currentScanId

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                        .clickable { onScanClick(scan) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatScanDate(scan.createdAt),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val summaryText = scan.treatmentPlan.lines().firstOrNull()?.removePrefix("Status:")?.trim() ?: "Check-in"
                        Text(
                            text = if (index == 0) stringResource(R.string.initial_diagnosis) else {
                                if (summaryText.length > 35) summaryText.take(35) + "..." else summaryText
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.view_detail_content_desc),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                if (index < scans.size - 1) {
                    Box(
                        modifier = Modifier
                            .padding(start = 23.dp)
                            .width(2.dp)
                            .height(16.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CareReminderCard(
    scanId: String?,
    parentId: String?,
    plantNameDefault: String,
    wateringTimeDefault: String?,
    fertilizingTimeDefault: String?,
    existingReminders: List<PlantReminderEntity>,
    onAddReminders: (
        plantName: String,
        wateringEnabled: Boolean,
        wateringHour: Int,
        wateringMinute: Int,
        fertilizingEnabled: Boolean,
        fertilizingHour: Int,
        fertilizingMinute: Int
    ) -> Unit,
    onGoToReminders: () -> Unit,
    onUpdateReminderTime: (PlantReminderEntity, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultWatering = remember(wateringTimeDefault) { parseTimeString(wateringTimeDefault, 8, 0) }
    val defaultFertilizing = remember(fertilizingTimeDefault) { parseTimeString(fertilizingTimeDefault, 17, 0) }

    // Plant family ID is the first scan's ID (the parent scan's ID if this is a progress update, otherwise the current scan's ID)
    val plantFamilyScanId = if (!parentId.isNullOrBlank() && parentId != scanId) parentId else scanId
    val familyReminders = remember(plantFamilyScanId, existingReminders) {
        if (plantFamilyScanId == null) emptyList() else existingReminders.filter { it.scanId == plantFamilyScanId }
    }
    
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE) }
    
    var isUpdatePromptDismissed by remember(scanId) {
        mutableStateOf(if (scanId != null) sharedPrefs.getBoolean("reminder_update_handled_$scanId", false) else false)
    }
    var activeUpdatePicker by remember { mutableStateOf<String?>(null) }
    
    val parentWatering = remember(familyReminders) {
        familyReminders.firstOrNull { it.careType.equals("Watering", ignoreCase = true) }
    }
    val parentFertilizing = remember(familyReminders) {
        familyReminders.firstOrNull { it.careType.equals("Fertilizing", ignoreCase = true) }
    }

    val markUpdateHandled = {
        if (scanId != null) {
            sharedPrefs.edit().putBoolean("reminder_update_handled_$scanId", true).apply()
            isUpdatePromptDismissed = true
        }
    }

    val isProgressUpdate = !parentId.isNullOrBlank() && parentId != scanId

    if (isProgressUpdate && familyReminders.isNotEmpty()) {
        val isWateringDifferent = parentWatering != null && wateringTimeDefault != null &&
                (parentWatering.hour != defaultWatering.first || parentWatering.minute != defaultWatering.second)
                
        val isFertilizingDifferent = parentFertilizing != null && fertilizingTimeDefault != null &&
                (parentFertilizing.hour != defaultFertilizing.first || parentFertilizing.minute != defaultFertilizing.second)
                
        if ((isWateringDifferent || isFertilizingDifferent) && !isUpdatePromptDismissed) {
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.new_care_times_recommended),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.new_care_times_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (isWateringDifferent) {
                        Spacer(modifier = Modifier.height(16.dp))
                        val oldTime = String.format("%02d:%02d %s", if (parentWatering!!.hour % 12 == 0) 12 else parentWatering.hour % 12, parentWatering.minute, if (parentWatering.hour >= 12) "PM" else "AM")
                        val newTime = String.format("%02d:%02d %s", if (defaultWatering.first % 12 == 0) 12 else defaultWatering.first % 12, defaultWatering.second, if (defaultWatering.first >= 12) "PM" else "AM")
                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(text = stringResource(R.string.watering_reminder_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(text = stringResource(R.string.current_vs_recommended, oldTime, newTime), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { 
                                        onUpdateReminderTime(parentWatering, defaultWatering.first, defaultWatering.second)
                                        markUpdateHandled()
                                    }, 
                                    modifier = Modifier.weight(1f), 
                                    shape = RoundedCornerShape(8.dp)
                                ) { 
                                    Text(stringResource(R.string.use_ai_time), fontSize = 12.sp) 
                                }
                                TextButton(onClick = { activeUpdatePicker = "watering" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) { Text(stringResource(R.string.custom_time), fontSize = 12.sp) }
                            }
                        }
                    }
                    
                    if (isFertilizingDifferent) {
                        Spacer(modifier = Modifier.height(16.dp))
                        val oldTime = String.format("%02d:%02d %s", if (parentFertilizing!!.hour % 12 == 0) 12 else parentFertilizing.hour % 12, parentFertilizing.minute, if (parentFertilizing.hour >= 12) "PM" else "AM")
                        val newTime = String.format("%02d:%02d %s", if (defaultFertilizing.first % 12 == 0) 12 else defaultFertilizing.first % 12, defaultFertilizing.second, if (defaultFertilizing.first >= 12) "PM" else "AM")
                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(text = stringResource(R.string.treatment_fertilizer_reminder_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(text = stringResource(R.string.current_vs_recommended, oldTime, newTime), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { 
                                        onUpdateReminderTime(parentFertilizing, defaultFertilizing.first, defaultFertilizing.second)
                                        markUpdateHandled()
                                    }, 
                                    modifier = Modifier.weight(1f), 
                                    shape = RoundedCornerShape(8.dp)
                                ) { 
                                    Text(stringResource(R.string.use_ai_time), fontSize = 12.sp) 
                                }
                                TextButton(onClick = { activeUpdatePicker = "fertilizing" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) { Text(stringResource(R.string.custom_time), fontSize = 12.sp) }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { markUpdateHandled() }) { Text(stringResource(R.string.keep_existing_times), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
                        TextButton(onClick = onGoToReminders) { Text(stringResource(R.string.manage_reminders)) }
                    }
                }
            }

            if (activeUpdatePicker != null) {
                val pickerType = activeUpdatePicker
                val parentReminder = if (pickerType == "watering") parentWatering else parentFertilizing
                val initialHour = parentReminder?.hour ?: (if (pickerType == "watering") defaultWatering.first else defaultFertilizing.first)
                val initialMinute = parentReminder?.minute ?: (if (pickerType == "watering") defaultWatering.second else defaultFertilizing.second)
                val timePickerDialog = android.app.TimePickerDialog(context, { _, hour, minute -> 
                    if (parentReminder != null) {
                        onUpdateReminderTime(parentReminder, hour, minute)
                    }
                    markUpdateHandled()
                    activeUpdatePicker = null 
                }, initialHour, initialMinute, false)
                timePickerDialog.setOnDismissListener { activeUpdatePicker = null }; timePickerDialog.show()
            }
            return
        }
    }

    if (parentWatering != null && parentFertilizing != null) {
        Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.care_reminders_scheduled_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(R.string.care_reminders_active_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onGoToReminders, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.manage_reminders)) }
            }
        }
        return
    }

    val myPlantDefault = stringResource(R.string.my_plant_default)
    val uniquePlantName = remember(plantNameDefault, scanId, parentId, existingReminders, familyReminders) {
        val existingName = familyReminders.firstOrNull()?.plantName
        if (existingName != null) {
            existingName
        } else if (parentId != null) {
            existingReminders.firstOrNull { it.scanId == parentId }?.plantName ?: plantNameDefault
        } else {
            generateUniquePlantName(plantNameDefault, scanId, existingReminders, myPlantDefault)
        }
    }
    var plantName by remember(uniquePlantName) { mutableStateOf(uniquePlantName) }
    val isNameDuplicate = remember(plantName, plantFamilyScanId, existingReminders) { val trimmed = plantName.trim(); trimmed.isNotBlank() && existingReminders.any { it.scanId != plantFamilyScanId && it.plantName.trim().equals(trimmed, ignoreCase = true) } }
    var wateringEnabled by remember(parentWatering) { mutableStateOf(parentWatering == null) }
    var wateringHour by remember { mutableStateOf(defaultWatering.first) }
    var wateringMinute by remember { mutableStateOf(defaultWatering.second) }
    var fertilizingEnabled by remember(parentFertilizing) { mutableStateOf(parentFertilizing == null) }
    var fertilizingHour by remember { mutableStateOf(defaultFertilizing.first) }
    var fertilizingMinute by remember { mutableStateOf(defaultFertilizing.second) }
    var activePicker by remember { mutableStateOf<String?>(null) }
    var isAdded by remember { mutableStateOf(false) }

    if (isAdded) {
        Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = stringResource(R.string.reminders_scheduled_success), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        return
    }

    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.schedule_care_reminders), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            if (familyReminders.isEmpty()) {
                androidx.compose.material3.OutlinedTextField(
                    value = plantName,
                    onValueChange = { plantName = it },
                    label = { Text(stringResource(R.string.plant_name_label)) },
                    isError = isNameDuplicate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                if (isNameDuplicate) {
                    Text(
                        text = stringResource(R.string.plant_name_duplicate_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Text(
                    text = stringResource(R.string.plant_name_display, plantName),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (parentWatering != null) {
                val formattedTime = String.format("%02d:%02d %s", if (parentWatering.hour % 12 == 0) 12 else parentWatering.hour % 12, parentWatering.minute, if (parentWatering.hour >= 12) "PM" else "AM")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.daily_watering_reminder_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    Text(
                        text = stringResource(R.string.daily_watering_scheduled_at, formattedTime),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(imageVector = Icons.Default.WaterDrop, contentDescription = null, tint = if (wateringEnabled) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.daily_watering_reminder_label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (wateringEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedVisibility(visible = wateringEnabled) {
                            val formattedWateringTime = String.format("%02d:%02d %s", if (wateringHour % 12 == 0) 12 else wateringHour % 12, wateringMinute, if (wateringHour >= 12) "PM" else "AM")
                            TextButton(onClick = { activePicker = "watering" }) { Text(formattedWateringTime, fontWeight = FontWeight.Bold) }
                        }
                        Switch(checked = wateringEnabled, onCheckedChange = { wateringEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (parentFertilizing != null) {
                val formattedTime = String.format("%02d:%02d %s", if (parentFertilizing.hour % 12 == 0) 12 else parentFertilizing.hour % 12, parentFertilizing.minute, if (parentFertilizing.hour >= 12) "PM" else "AM")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Grass,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.daily_treatment_fertilizer_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    Text(
                        text = stringResource(R.string.daily_watering_scheduled_at, formattedTime),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(imageVector = Icons.Default.Grass, contentDescription = null, tint = if (fertilizingEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.daily_treatment_fertilizer_label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (fertilizingEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedVisibility(visible = fertilizingEnabled) {
                            val formattedFertilizingTime = String.format("%02d:%02d %s", if (fertilizingHour % 12 == 0) 12 else fertilizingHour % 12, fertilizingMinute, if (fertilizingHour >= 12) "PM" else "AM")
                            TextButton(onClick = { activePicker = "fertilizing" }) { Text(formattedFertilizingTime, fontWeight = FontWeight.Bold) }
                        }
                        Switch(checked = fertilizingEnabled, onCheckedChange = { fertilizingEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { if (plantName.isNotBlank() && (wateringEnabled || fertilizingEnabled)) { onAddReminders(plantName, wateringEnabled, wateringHour, wateringMinute, fertilizingEnabled, fertilizingHour, fertilizingMinute); isAdded = true } }, enabled = plantName.isNotBlank() && !isNameDuplicate && (wateringEnabled || fertilizingEnabled), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text(stringResource(R.string.schedule_care_reminders)) }
        }
    }

    if (activePicker != null) {
        val pickerType = activePicker
        val initialHour = if (pickerType == "watering") wateringHour else fertilizingHour
        val initialMinute = if (pickerType == "watering") wateringMinute else fertilizingMinute
        val timePickerDialog = android.app.TimePickerDialog(context, { _, hour, minute -> if (pickerType == "watering") { wateringHour = hour; wateringMinute = minute } else { fertilizingHour = hour; fertilizingMinute = minute }; activePicker = null }, initialHour, initialMinute, false)
        timePickerDialog.setOnDismissListener { activePicker = null }; timePickerDialog.show()
    }
}

private fun parseTimeString(timeStr: String?, defaultHour: Int, defaultMinute: Int): Pair<Int, Int> {
    if (timeStr.isNullOrBlank()) return Pair(defaultHour, defaultMinute)
    return try {
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: defaultHour
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: defaultMinute
        Pair(hour, minute)
    } catch (_: Exception) {
        Pair(defaultHour, defaultMinute)
    }
}

private fun generateUniquePlantName(baseName: String, currentScanId: String?, existing: List<PlantReminderEntity>, defaultName: String): String {
    val baseTrimmed = baseName.trim()
    if (baseTrimmed.isBlank()) return defaultName
    
    val otherPlantNames = existing
        .filter { it.scanId != currentScanId }
        .map { it.plantName.trim().lowercase() }
        .toSet()
        
    if (baseTrimmed.lowercase() !in otherPlantNames) {
        return baseTrimmed
    }
    var counter = 2
    while (true) {
        val proposedName = "$baseTrimmed $counter"
        if (proposedName.lowercase() !in otherPlantNames) {
            return proposedName
        }
        counter++
    }
}
