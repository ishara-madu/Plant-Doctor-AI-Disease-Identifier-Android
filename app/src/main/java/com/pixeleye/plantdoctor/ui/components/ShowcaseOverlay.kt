package com.pixeleye.plantdoctor.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A full-screen showcase overlay with a transparent spotlight cutout.
 *
 * Touch rules:
 *  - The dim area (outside the cutout) is FULLY BLOCKED — no taps pass through.
 *  - Inside the cutout: if [onTargetTap] / [onTargetLongPress] is provided the gesture
 *    is detected and the callback fired (event consumed). Otherwise the touch is NOT
 *    consumed and falls through to the element under the cutout.
 *  - The "Got it" button always calls [onDismiss].
 *
 * @param targetRect          Screen-space bounding box of the highlighted element (px).
 * @param icon                Leading icon shown in the tooltip card header.
 * @param title               Short heading text.
 * @param message             Longer instruction text.
 * @param buttonLabel         Label for the dismiss button.
 * @param onDismiss           Called when the user taps "Got it".
 * @param onTargetTap         Optional: called when the user taps inside the cutout.
 *                            When null, tap passes through to underlying composable.
 * @param onTargetLongPress   Optional: called when user long-presses inside the cutout.
 *                            When null, long-press passes through to underlying composable.
 */
@Composable
fun ShowcaseOverlay(
    targetRect: Rect,
    icon: ImageVector,
    title: String,
    message: String,
    buttonLabel: String = "Got it",
    onDismiss: () -> Unit,
    onTargetTap: (() -> Unit)? = null,
    onTargetLongPress: (() -> Unit)? = null,
    cornerRadius: Float = 24f,
    cutoutPadding: Float = 16f
) {
    val density = LocalDensity.current

    // ── Pulse animation ─────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "showcase_pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "pulse"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "pulse_alpha"
    )

    val paddedRect = Rect(
        left   = targetRect.left   - cutoutPadding,
        top    = targetRect.top    - cutoutPadding,
        right  = targetRect.right  + cutoutPadding,
        bottom = targetRect.bottom + cutoutPadding
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val spaceBelow = screenHeightPx - paddedRect.bottom
        val spaceAbove = paddedRect.top
        val placeBelow = spaceBelow >= spaceAbove

        val cutoutBottomDp = with(density) { paddedRect.bottom.toDp() }
        val cutoutTopDp    = with(density) { paddedRect.top.toDp() }
        val tooltipGap     = 20.dp

        // ── 1. Dim layer + cutout (BlendMode.Clear punch-through) ──
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(Color.Black.copy(alpha = 0.75f))

            // Transparent spotlight
            drawPath(
                path = Path().apply {
                    addRoundRect(RoundRect(rect = paddedRect, cornerRadius = CornerRadius(cornerRadius)))
                },
                color = Color.Transparent,
                blendMode = BlendMode.Clear
            )

            // Pulsing ring
            val maxPulse = 28.dp.toPx()
            drawRoundRect(
                color = Color.White.copy(alpha = pulseAlpha * 0.55f),
                topLeft = Offset(
                    paddedRect.left  - maxPulse * pulseProgress,
                    paddedRect.top   - maxPulse * pulseProgress
                ),
                size = Size(
                    paddedRect.width  + maxPulse * 2 * pulseProgress,
                    paddedRect.height + maxPulse * 2 * pulseProgress
                ),
                cornerRadius = CornerRadius(cornerRadius + maxPulse * pulseProgress),
                style = Stroke(width = 2.5.dp.toPx())
            )
        }

        // ── 2. Touch-blocking + cutout gesture routing ─────────────
        //
        // This overlay CONSUMES all touches OUTSIDE the cutout, making the dim
        // area completely non-interactive. Touches inside the cutout are either:
        //   • Routed to an explicit callback (onTargetTap / onTargetLongPress), or
        //   • NOT consumed → falls through to the element beneath the overlay.
        //
        // Important: the tooltip Surface is drawn AFTER this Box (higher Z), so its
        // Button taps are intercepted by the Surface before reaching this handler.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(paddedRect, onTargetTap, onTargetLongPress) {
                    if (onTargetTap != null || onTargetLongPress != null) {
                        // Explicit-callback mode: detect gestures in the whole screen.
                        // Taps/LongPresses inside the cutout route to the callback;
                        // all touches outside the cutout are consumed (blocked) by
                        // detectTapGestures naturally (it consumes events after detection).
                        detectTapGestures(
                            onTap = { offset ->
                                if (paddedRect.contains(offset)) {
                                    onTargetTap?.invoke()
                                }
                                // Outside cutout: event is consumed by detectTapGestures = blocked ✓
                            },
                            onLongPress = { offset ->
                                if (paddedRect.contains(offset)) {
                                    onTargetLongPress?.invoke()
                                }
                            }
                        )
                    } else {
                        // Pass-through mode: manually block outside, don't touch inside.
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(
                                    androidx.compose.ui.input.pointer.PointerEventPass.Initial
                                )
                                event.changes.forEach { change ->
                                    if (!paddedRect.contains(change.position)) {
                                        change.consume()
                                    }
                                    // Inside cutout: NOT consumed → propagates to sibling below ✓
                                }
                            }
                        }
                    }
                }
        )

        // ── 3. Tooltip card (highest Z — always receives its own touches) ──
        Box(
            modifier = if (placeBelow) {
                Modifier
                    .fillMaxSize()
                    .padding(
                        start  = 20.dp,
                        end    = 20.dp,
                        top    = cutoutBottomDp + tooltipGap,
                        bottom = 20.dp
                    )
            } else {
                Modifier
                    .fillMaxSize()
                    .padding(
                        start  = 20.dp,
                        end    = 20.dp,
                        top    = 20.dp,
                        bottom = (maxHeight - cutoutTopDp) + tooltipGap
                    )
            },
            contentAlignment = if (placeBelow) Alignment.TopCenter else Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 16.dp,
                modifier = Modifier.widthIn(max = 400.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {

                    // ── Icon chip + title row ───────────────────
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .then(
                                    Modifier.graphicsLayer {} // ensures draw layer
                                )
                                .padding(0.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Message ─────────────────────────────────
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── Dismiss button ───────────────────────────
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = buttonLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
