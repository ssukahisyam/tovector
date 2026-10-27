package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.TraceConfig
import com.example.model.TraceMode
import com.example.ui.theme.CyanVectorPrimary
import com.example.ui.theme.EmeraldNodeOptimized
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Minimalist Real-Time Parameter Control Panel with dedicated sliders for
 * Smoothness, Threshold, and Despeckle, plus expandable Anti-Mbulet geometry controls.
 */
@Composable
fun TracingParameterControlPanel(
    config: TraceConfig,
    isProcessing: Boolean,
    useGlobalConfig: Boolean?,
    onToggleUseGlobal: ((Boolean) -> Unit)? = null,
    onUpdateConfig: ((TraceConfig) -> TraceConfig) -> Unit,
    onResetToModeDefault: () -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "param_panel"
) {
    var showAdvancedGeometry by remember { mutableStateOf(true) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .testTag("${tagPrefix}_root")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header Row with Live Status Indicator & Reset Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = CyanVectorPrimary
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(EmeraldNodeOptimized, CircleShape)
                        )
                    }

                    Column {
                        Text(
                            text = "Fine-Tune Parameter (${config.mode.badge})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isProcessing) {
                                "Memperbarui kurva secara real-time..."
                            } else {
                                "Real-time sync aktif"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isProcessing) CyanVectorPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (useGlobalConfig != null && onToggleUseGlobal != null) {
                        Surface(
                            color = if (useGlobalConfig) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleUseGlobal(!useGlobalConfig) }
                                .testTag("${tagPrefix}_override_badge")
                        ) {
                            Text(
                                text = if (useGlobalConfig) "Global" else "Custom",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (useGlobalConfig) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onResetToModeDefault,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("${tagPrefix}_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Parameter Default",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outline)

            // 2. Core Real-Time Sliders: Smoothness, Threshold, Despeckle
            // --- A. SMOOTHNESS SLIDER ---
            RealTimeSliderRow(
                title = "Smoothness",
                subtitle = if (config.mode == TraceMode.PIXEL_POLYGON) {
                    "0.00 = Poligon siku murni, >0.10 = Haluskan sudut kurva Bezier"
                } else {
                    "Kelengkungan kurva Cubic Bezier (0 = garis kaku, 1.35 = ekstra halus)"
                },
                valueText = String.format(Locale.US, "%.2f", config.smoothness),
                value = config.smoothness,
                valueRange = 0.0f..1.35f,
                stepDelta = 0.05f,
                onValueChange = { newVal ->
                    onUpdateConfig { it.copy(smoothness = newVal.coerceIn(0.0f, 1.35f)) }
                },
                testTag = "${tagPrefix}_slider_smoothness"
            )

            // --- B. THRESHOLD SLIDER ---
            RealTimeSliderRow(
                title = "Threshold",
                subtitle = "Ambang batas kepekaan gelap-terang piksel (Luminance 10–245)",
                valueText = "${config.threshold} / 255",
                value = config.threshold.toFloat(),
                valueRange = 10f..245f,
                stepDelta = 5f,
                onValueChange = { newVal ->
                    onUpdateConfig { it.copy(threshold = newVal.roundToInt().coerceIn(10, 245)) }
                },
                testTag = "${tagPrefix}_slider_threshold"
            )

            // --- C. DESPECKLE SLIDER ---
            RealTimeSliderRow(
                title = "Despeckle",
                subtitle = "Filter turdsize untuk membuang bintik/noise piksel kecil (0–100 px)",
                valueText = "${config.despeckleSize} px",
                value = config.despeckleSize.toFloat(),
                valueRange = 0f..100f,
                stepDelta = 2f,
                onValueChange = { newVal ->
                    onUpdateConfig { it.copy(despeckleSize = newVal.roundToInt().coerceIn(0, 100)) }
                },
                testTag = "${tagPrefix}_slider_despeckle"
            )

            // 3. Expandable Anti-Mbulet & Mode-Specific Fine Tuning
            HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outline)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedGeometry = !showAdvancedGeometry }
                    .testTag("${tagPrefix}_toggle_advanced"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kontrol Geometri & Anti-Mbulet Lanjutan",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (showAdvancedGeometry) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Tampilkan/Sembunyikan",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = showAdvancedGeometry) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Simplify Tolerance (Anti-Mbulet Douglas-Peucker)
                    RealTimeSliderRow(
                        title = "Simplify Tolerance (Anti-Mbulet ε)",
                        subtitle = "Reduksi titik poligon (naikkan jika garis lurus terlihat bergelombang)",
                        valueText = String.format(Locale.US, "%.1f px", config.simplifyTolerance),
                        value = config.simplifyTolerance,
                        valueRange = 0.4f..7.5f,
                        stepDelta = 0.2f,
                        onValueChange = { newVal ->
                            onUpdateConfig { it.copy(simplifyTolerance = newVal.coerceIn(0.4f, 7.5f)) }
                        },
                        testTag = "${tagPrefix}_slider_simplify"
                    )

                    // Corner Angle Threshold (AlphaMax)
                    RealTimeSliderRow(
                        title = "Corner Threshold (AlphaMax)",
                        subtitle = "Batas sudut lancip yang dikunci agar tidak ikut dibulatkan",
                        valueText = "${config.cornerThresholdDeg.roundToInt()}°",
                        value = config.cornerThresholdDeg,
                        valueRange = 45f..165f,
                        stepDelta = 5f,
                        onValueChange = { newVal ->
                            onUpdateConfig { it.copy(cornerThresholdDeg = newVal.coerceIn(45f, 165f)) }
                        },
                        testTag = "${tagPrefix}_slider_corner"
                    )

                    if (config.mode == TraceMode.VTRACER_MULTICOLOR || config.mode == TraceMode.PIXEL_POLYGON) {
                        RealTimeSliderRow(
                            title = "Color Layers (K-Means Palette)",
                            subtitle = "Jumlah pengelompokan layer warna datar",
                            valueText = "${config.colorCount} Warna",
                            value = config.colorCount.toFloat(),
                            valueRange = 2f..12f,
                            stepDelta = 1f,
                            onValueChange = { newVal ->
                                onUpdateConfig { it.copy(colorCount = newVal.roundToInt().coerceIn(2, 12)) }
                            },
                            testTag = "${tagPrefix}_slider_colors"
                        )
                    }

                    if (config.mode == TraceMode.CENTERLINE_SKETCH) {
                        RealTimeSliderRow(
                            title = "Stroke Width",
                            subtitle = "Ketebalan garis tengah pada output SVG",
                            valueLabel = String.format(Locale.US, "%.1f px", config.strokeWidth),
                            value = config.strokeWidth,
                            valueRange = 1.0f..8.0f,
                            stepDelta = 0.5f,
                            onValueChange = { newVal ->
                                onUpdateConfig { it.copy(strokeWidth = newVal.coerceIn(1.0f, 8.0f)) }
                            },
                            testTag = "${tagPrefix}_slider_stroke"
                        )
                    }

                    // Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Orthogonal Line Snap",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Kunci segmen vertikal & horizontal agar presisi siku",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.orthogonalSnap,
                            onCheckedChange = { checked ->
                                onUpdateConfig { it.copy(orthogonalSnap = checked) }
                            },
                            modifier = Modifier.testTag("${tagPrefix}_switch_orthogonal")
                        )
                    }

                    if (config.mode == TraceMode.VTRACER_MULTICOLOR || config.mode == TraceMode.PIXEL_POLYGON) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Hide Background Layer",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Sembunyikan warna latar tepi secara otomatis",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = config.removeBackground,
                                onCheckedChange = { checked ->
                                    onUpdateConfig { it.copy(removeBackground = checked) }
                                },
                                modifier = Modifier.testTag("${tagPrefix}_switch_bg")
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Invert Black / White",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Balikkan area objek terang di atas latar gelap",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = config.invertBw,
                                onCheckedChange = { checked ->
                                    onUpdateConfig { it.copy(invertBw = checked) }
                                },
                                modifier = Modifier.testTag("${tagPrefix}_switch_invert")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RealTimeSliderRow(
    title: String,
    subtitle: String,
    valueText: String = "",
    valueLabel: String = valueText,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    stepDelta: Float,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Fine Stepper (- / Value / +)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            onValueChange((value - stepDelta).coerceIn(valueRange.start, valueRange.endInclusive))
                        }
                        .testTag("${testTag}_minus")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Kurangi $title",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = valueLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanVectorPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            onValueChange((value + stepDelta).coerceIn(valueRange.start, valueRange.endInclusive))
                        }
                        .testTag("${testTag}_plus")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah $title",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}
