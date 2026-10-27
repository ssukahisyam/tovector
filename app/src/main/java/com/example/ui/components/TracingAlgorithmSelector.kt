package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.TraceMode
import com.example.ui.theme.CyanVectorPrimary

/**
 * Minimalist Jetpack Compose selector for the 5 Vector Tracing Algorithms
 * (Geometric Anti-Mbulet, Potrace B/W, VTracer Multicolor, Pixel Polygon, Centerline Sketch)
 * with clean line iconography and concise descriptions.
 */
@Composable
fun TracingAlgorithmSelector(
    selectedMode: TraceMode,
    onModeSelected: (TraceMode) -> Unit,
    modifier: Modifier = Modifier,
    headerTitle: String = "Mode Algoritma",
    defaultCompactCarousel: Boolean = false,
    tagPrefix: String = "algo_selector"
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${tagPrefix}_container"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (headerTitle.isNotEmpty()) {
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (defaultCompactCarousel) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TraceMode.entries.forEach { mode ->
                    MinimalCompactModeCard(
                        mode = mode,
                        isSelected = selectedMode == mode,
                        onClick = { onModeSelected(mode) },
                        testTag = "${tagPrefix}_chip_${mode.id}"
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TraceMode.entries.forEach { mode ->
                    MinimalListModeRow(
                        mode = mode,
                        isSelected = selectedMode == mode,
                        onClick = { onModeSelected(mode) },
                        testTag = "${tagPrefix}_card_${mode.id}"
                    )
                }
            }
        }
    }
}

@Composable
private fun MinimalListModeRow(
    mode: TraceMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bg"
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 0.8.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MinimalModeGlyph(
                mode = mode,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = mode.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) CyanVectorPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = mode.shortSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(18.dp)
                    .border(
                        width = if (isSelected) 5.dp else 1.5.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun MinimalCompactModeCard(
    mode: TraceMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(188.dp)
            .border(
                width = 0.8.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MinimalModeGlyph(
                    mode = mode,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    sizeDp = 28
                )
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = mode.shortSummary,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MinimalModeGlyph(
    mode: TraceMode,
    tint: Color,
    sizeDp: Int = 34
) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .background(
                tint.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size((sizeDp - 14).dp)) {
            val w = size.width
            val h = size.height
            val sw = 1.8.dp.toPx()

            when (mode) {
                TraceMode.GEOMETRIC_SHARP -> {
                    val path = Path().apply {
                        moveTo(w * 0.5f, h * 0.1f)
                        lineTo(w * 0.9f, h * 0.35f)
                        lineTo(w * 0.9f, h * 0.75f)
                        lineTo(w * 0.5f, h * 0.92f)
                        lineTo(w * 0.1f, h * 0.75f)
                        lineTo(w * 0.1f, h * 0.35f)
                        close()
                    }
                    drawPath(path, color = tint, style = Stroke(width = sw))
                }
                TraceMode.POTRACE_BW -> {
                    val curve = Path().apply {
                        moveTo(w * 0.1f, h * 0.8f)
                        cubicTo(w * 0.1f, h * 0.1f, w * 0.9f, h * 0.9f, w * 0.9f, h * 0.2f)
                    }
                    drawPath(curve, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round))
                    drawCircle(tint, radius = 2.2.dp.toPx(), center = Offset(w * 0.1f, h * 0.8f))
                    drawCircle(tint, radius = 2.2.dp.toPx(), center = Offset(w * 0.9f, h * 0.2f))
                }
                TraceMode.VTRACER_MULTICOLOR -> {
                    drawCircle(
                        color = tint,
                        radius = w * 0.32f,
                        center = Offset(w * 0.38f, h * 0.42f),
                        style = Stroke(width = sw)
                    )
                    drawCircle(
                        color = tint.copy(alpha = 0.55f),
                        radius = w * 0.32f,
                        center = Offset(w * 0.65f, h * 0.58f)
                    )
                }
                TraceMode.PIXEL_POLYGON -> {
                    val cell = w / 3f
                    drawRect(tint, Offset(cell, 0f), Size(cell, cell))
                    drawRect(tint, Offset(0f, cell), Size(cell * 3, cell))
                    drawRect(tint, Offset(0f, cell * 2), Size(cell, cell))
                    drawRect(tint, Offset(cell * 2, cell * 2), Size(cell, cell))
                }
                TraceMode.CENTERLINE_SKETCH -> {
                    val cx = w * 0.5f
                    val cy = h * 0.52f
                    drawLine(tint, Offset(cx, cy), Offset(w * 0.5f, h * 0.1f), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(tint, Offset(cx, cy), Offset(w * 0.15f, h * 0.85f), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(tint, Offset(cx, cy), Offset(w * 0.85f, h * 0.85f), strokeWidth = sw, cap = StrokeCap.Round)
                }
            }
        }
    }
}
