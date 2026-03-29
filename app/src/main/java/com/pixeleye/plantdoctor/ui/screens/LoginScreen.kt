package com.pixeleye.plantdoctor.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LoginScreen(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onGoogleSignIn: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top decorative gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // Hero Illustration
            LoginIllustration(
                modifier = Modifier.size(200.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // App name
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Eco,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Plant Doctor",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your AI-powered plant health companion.\nDetect diseases. Get treatments. Grow healthy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Feature highlights
            FeatureRow(
                icon = Icons.Default.LocalFlorist,
                text = "Instant disease detection with AI"
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureRow(
                icon = Icons.Default.Grass,
                text = "Personalized treatment recommendations"
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureRow(
                icon = Icons.Default.Eco,
                text = "Track your plant health history"
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Error message
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Google Sign-In button
            Button(
                onClick = onGoogleSignIn,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Signing in...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    // Google "G" icon drawn with Canvas
                    GoogleIcon(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Continue with Google",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "By continuing, you agree to our Terms of Service",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LoginIllustration(
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "leaf_sway")
    val swayOffset by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f

        // ── Ground ─────────────────────────────────────────────
        drawOval(
            color = secondaryColor.copy(alpha = 0.2f),
            topLeft = Offset(w * 0.15f, h * 0.85f),
            size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.1f)
        )

        // ── Pot ────────────────────────────────────────────────
        val potPath = Path().apply {
            moveTo(cx - w * 0.12f, h * 0.72f)
            lineTo(cx - w * 0.15f, h * 0.92f)
            lineTo(cx + w * 0.15f, h * 0.92f)
            lineTo(cx + w * 0.12f, h * 0.72f)
            close()
        }
        drawPath(potPath, color = secondaryColor.copy(alpha = 0.55f))

        drawRoundRect(
            color = secondaryColor.copy(alpha = 0.65f),
            topLeft = Offset(cx - w * 0.14f, h * 0.70f),
            size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.045f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
        )

        // Soil dots
        for (i in 0..4) {
            val dotX = cx - w * 0.06f + (i * w * 0.03f)
            drawCircle(
                color = secondaryColor.copy(alpha = 0.3f),
                radius = 2.dp.toPx(),
                center = Offset(dotX, h * 0.72f)
            )
        }

        // ── Main Stem ──────────────────────────────────────────
        val sway = swayOffset
        drawLine(
            color = primaryColor,
            start = Offset(cx, h * 0.70f),
            end = Offset(cx + sway * 0.3f, h * 0.30f),
            strokeWidth = 4.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // ── Leaves ─────────────────────────────────────────────
        val stemTop = Offset(cx + sway * 0.3f, h * 0.30f)
        val stemMid = Offset(cx + sway * 0.15f, h * 0.50f)
        val stemLower = Offset(cx, h * 0.62f)

        // Large right leaf
        val leaf1 = Path().apply {
            moveTo(stemMid.x, stemMid.y)
            cubicTo(
                stemMid.x + w * 0.22f + sway, stemMid.y - h * 0.06f,
                stemMid.x + w * 0.28f + sway, stemMid.y - h * 0.20f,
                stemMid.x + w * 0.10f + sway, stemMid.y - h * 0.24f
            )
            cubicTo(
                stemMid.x + w * 0.06f, stemMid.y - h * 0.14f,
                stemMid.x + w * 0.03f, stemMid.y - h * 0.05f,
                stemMid.x, stemMid.y
            )
            close()
        }
        drawPath(leaf1, color = primaryColor)

        // Large left leaf
        val leaf2 = Path().apply {
            moveTo(stemLower.x, stemLower.y)
            cubicTo(
                stemLower.x - w * 0.24f - sway, stemLower.y - h * 0.06f,
                stemLower.x - w * 0.28f - sway, stemLower.y - h * 0.22f,
                stemLower.x - w * 0.10f - sway, stemLower.y - h * 0.26f
            )
            cubicTo(
                stemLower.x - w * 0.06f, stemLower.y - h * 0.16f,
                stemLower.x - w * 0.03f, stemLower.y - h * 0.05f,
                stemLower.x, stemLower.y
            )
            close()
        }
        drawPath(leaf2, color = primaryContainer)

        // Small top leaf
        val leaf3 = Path().apply {
            moveTo(stemTop.x, stemTop.y)
            cubicTo(
                stemTop.x + w * 0.08f + sway, stemTop.y - h * 0.06f,
                stemTop.x + w * 0.14f + sway, stemTop.y - h * 0.18f,
                stemTop.x + w * 0.04f + sway, stemTop.y - h * 0.22f
            )
            cubicTo(
                stemTop.x - w * 0.02f, stemTop.y - h * 0.12f,
                stemTop.x - w * 0.01f, stemTop.y - h * 0.03f,
                stemTop.x, stemTop.y
            )
            close()
        }
        drawPath(leaf3, color = primaryColor.copy(alpha = 0.85f))

        // Leaf veins
        drawLine(
            color = primaryColor.copy(alpha = 0.25f),
            start = stemMid,
            end = Offset(stemMid.x + w * 0.14f + sway, stemMid.y - h * 0.15f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = primaryColor.copy(alpha = 0.25f),
            start = stemLower,
            end = Offset(stemLower.x - w * 0.13f - sway, stemLower.y - h * 0.16f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // ── Scanning circles ───────────────────────────────────
        val scanCx = cx + w * 0.28f
        val scanCy = cy - h * 0.02f

        // Dashed outer ring
        val outerR = w * 0.16f
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30).toDouble())
            val startA = angle - 0.2
            val endA = angle + 0.2
            drawArc(
                color = tertiaryColor.copy(alpha = 0.4f),
                startAngle = Math.toDegrees(startA).toFloat() + 90f,
                sweepAngle = 12f,
                useCenter = false,
                topLeft = Offset(scanCx - outerR, scanCy - outerR),
                size = androidx.compose.ui.geometry.Size(outerR * 2, outerR * 2),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Inner solid ring
        drawCircle(
            color = tertiaryColor.copy(alpha = 0.15f),
            radius = outerR * 0.7f,
            center = Offset(scanCx, scanCy)
        )
        drawCircle(
            color = tertiaryColor.copy(alpha = 0.5f),
            radius = outerR * 0.7f,
            center = Offset(scanCx, scanCy),
            style = Stroke(width = 2.dp.toPx())
        )

        // Crosshair
        val chLen = outerR * 0.35f
        drawLine(
            color = tertiaryColor.copy(alpha = 0.5f),
            start = Offset(scanCx - chLen, scanCy),
            end = Offset(scanCx + chLen, scanCy),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = tertiaryColor.copy(alpha = 0.5f),
            start = Offset(scanCx, scanCy - chLen),
            end = Offset(scanCx, scanCy + chLen),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Small sparkle
        val sparkX = scanCx + outerR * 0.9f
        val sparkY = scanCy - outerR * 0.6f
        val sparkS = 2.5.dp.toPx()
        drawLine(tertiaryColor, Offset(sparkX, sparkY), Offset(sparkX, sparkY - sparkS * 3), sparkS, StrokeCap.Round)
        drawLine(tertiaryColor, Offset(sparkX - sparkS * 1.5f, sparkY - sparkS * 1.5f), Offset(sparkX + sparkS * 1.5f, sparkY - sparkS * 1.5f), sparkS, StrokeCap.Round)
    }
}

@Composable
private fun GoogleIcon(
    modifier: Modifier = Modifier
) {
    // Simplified stylized "G" using Canvas
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * 0.42f

        // Blue segment (right)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -90f,
            sweepAngle = 60f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(width = r * 0.32f, cap = StrokeCap.Butt)
        )

        // Green segment (bottom)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(width = r * 0.32f, cap = StrokeCap.Butt)
        )

        // Yellow segment (left)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(width = r * 0.32f, cap = StrokeCap.Butt)
        )

        // Red segment (top)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(width = r * 0.32f, cap = StrokeCap.Butt)
        )

        // Horizontal bar of the "G"
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(cx, cy),
            end = Offset(cx + r, cy),
            strokeWidth = r * 0.32f,
            cap = StrokeCap.Round
        )
    }
}
