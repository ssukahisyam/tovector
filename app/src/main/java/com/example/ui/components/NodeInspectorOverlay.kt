package com.example.ui.components

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.EditableNodeInfo
import com.example.model.NodeSubPoint
import com.example.model.PathCommand
import com.example.model.SelectedPathNode
import com.example.model.VecPoint
import com.example.model.VectorPathLayer
import com.example.ui.theme.AmberTangentSecondary
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CyanVectorPrimary
import com.example.ui.theme.EmeraldNodeOptimized
import java.util.Locale
import kotlin.math.hypot

/**
 * Converts a screen tap Offset on the interactive canvas into the SVG coordinate space
 * and finds the nearest Bezier anchor point or tangent handle within touch tolerance.
 */
fun hitTestNearestBezierNode(
    tapOffset: Offset,
    canvasWidth: Float,
    canvasHeight: Float,
    panOffset: Offset,
    zoom: Float,
    originX: Float,
    originY: Float,
    fitScale: Float,
    layers: List<VectorPathLayer>,
    showHandles: Boolean,
    maxHitDistanceScreenPx: Float
): SelectedPathNode? {
    if (fitScale <= 0f || zoom <= 0f) return null

    // Invert canvas withTransform(translate(panOffset), scale(zoom, pivot = center))
    val pivotX = canvasWidth * 0.5f
    val pivotY = canvasHeight * 0.5f
    val unpannedX = tapOffset.x - panOffset.x
    val unpannedY = tapOffset.y - panOffset.y
    val untransformedX = pivotX + (unpannedX - pivotX) / zoom
    val untransformedY = pivotY + (unpannedY - pivotY) / zoom

    val svgTapX = (untransformedX - originX) / fitScale
    val svgTapY = (untransformedY - originY) / fitScale
    val maxDistSvg = (maxHitDistanceScreenPx / (fitScale * zoom)).coerceAtLeast(4f)

    var bestMatch: SelectedPathNode? = null
    var bestDist = maxDistSvg

    for (layer in layers) {
        for ((cmdIndex, cmd) in layer.commands.withIndex()) {
            when (cmd) {
                is PathCommand.MoveTo -> {
                    val d = hypot(cmd.p.x - svgTapX, cmd.p.y - svgTapY)
                    if (d <= bestDist) {
                        bestDist = d
                        bestMatch = SelectedPathNode(layer.id, cmdIndex, NodeSubPoint.ANCHOR)
                    }
                }
                is PathCommand.LineTo -> {
                    val d = hypot(cmd.p.x - svgTapX, cmd.p.y - svgTapY)
                    if (d <= bestDist) {
                        bestDist = d
                        bestMatch = SelectedPathNode(layer.id, cmdIndex, NodeSubPoint.ANCHOR)
                    }
                }
                is PathCommand.CubicTo -> {
                    if (showHandles) {
                        val dC1 = hypot(cmd.c1.x - svgTapX, cmd.c1.y - svgTapY)
                        if (dC1 <= bestDist) {
                            bestDist = dC1
                            bestMatch = SelectedPathNode(layer.id, cmdIndex, NodeSubPoint.CONTROL_1)
                        }
                        val dC2 = hypot(cmd.c2.x - svgTapX, cmd.c2.y - svgTapY)
                        if (dC2 <= bestDist) {
                            bestDist = dC2
                            bestMatch = SelectedPathNode(layer.id, cmdIndex, NodeSubPoint.CONTROL_2)
                        }
                    }
                    val dAnchor = hypot(cmd.p.x - svgTapX, cmd.p.y - svgTapY)
                    if (dAnchor <= bestDist) {
                        bestDist = dAnchor
                        bestMatch = SelectedPathNode(layer.id, cmdIndex, NodeSubPoint.ANCHOR)
                    }
                }
                is PathCommand.Close -> {}
            }
        }
    }
    return bestMatch
}

/**
 * Draws a high-contrast precision reticle, active segment highlight, and tangent arms
 * around the currently selected Bezier node on the canvas.
 */
fun DrawScope.drawSelectedNodeReticle(
    layers: List<VectorPathLayer>,
    selectedNode: SelectedPathNode,
    originX: Float,
    originY: Float,
    scale: Float,
    zoom: Float
) {
    val targetLayer = layers.find { it.id == selectedNode.layerId } ?: return
    val idx = selectedNode.commandIndex
    if (idx !in targetLayer.commands.indices) return

    fun mapPt(pt: VecPoint): Offset = Offset(originX + pt.x * scale, originY + pt.y * scale)

    // Find previous anchor point for drawing the active segment highlight
    var prevAnchorSvg: VecPoint? = null
    for (i in (idx - 1) downTo 0) {
        when (val prevCmd = targetLayer.commands[i]) {
            is PathCommand.MoveTo -> {
                prevAnchorSvg = prevCmd.p
                break
            }
            is PathCommand.LineTo -> {
                prevAnchorSvg = prevCmd.p
                break
            }
            is PathCommand.CubicTo -> {
                prevAnchorSvg = prevCmd.p
                break
            }
            is PathCommand.Close -> {}
        }
    }

    val cmd = targetLayer.commands[idx]
    val activeAnchorSvg: VecPoint = when (cmd) {
        is PathCommand.MoveTo -> cmd.p
        is PathCommand.LineTo -> cmd.p
        is PathCommand.CubicTo -> cmd.p
        is PathCommand.Close -> return
    }

    // Highlight active segment
    if (prevAnchorSvg != null) {
        val segPath = Path()
        val p0 = mapPt(prevAnchorSvg)
        segPath.moveTo(p0.x, p0.y)
        when (cmd) {
            is PathCommand.LineTo -> {
                val p1 = mapPt(cmd.p)
                segPath.lineTo(p1.x, p1.y)
            }
            is PathCommand.CubicTo -> {
                val c1 = mapPt(cmd.c1)
                val c2 = mapPt(cmd.c2)
                val p1 = mapPt(cmd.p)
                segPath.cubicTo(c1.x, c1.y, c2.x, c2.y, p1.x, p1.y)
            }
            else -> {}
        }
        drawPath(
            path = segPath,
            color = EmeraldNodeOptimized,
            style = Stroke(width = (3.0.dp.toPx() / zoom).coerceAtLeast(1.4f), cap = StrokeCap.Round)
        )
    }

    // If CubicTo, draw highlighted tangent handle arms
    if (cmd is PathCommand.CubicTo) {
        val c1Screen = mapPt(cmd.c1)
        val c2Screen = mapPt(cmd.c2)
        val anchorScreen = mapPt(cmd.p)
        val startScreen = prevAnchorSvg?.let { mapPt(it) } ?: anchorScreen
        val armStroke = (1.6.dp.toPx() / zoom).coerceAtLeast(0.8f)
        val handleRad = (4.2.dp.toPx() / zoom).coerceAtLeast(2.0f)

        drawLine(
            color = AmberTangentSecondary,
            start = startScreen,
            end = c1Screen,
            strokeWidth = armStroke
        )
        drawLine(
            color = AmberTangentSecondary,
            start = anchorScreen,
            end = c2Screen,
            strokeWidth = armStroke
        )
        drawCircle(
            color = AmberTangentSecondary,
            radius = handleRad,
            center = c1Screen
        )
        drawCircle(
            color = AmberTangentSecondary,
            radius = handleRad,
            center = c2Screen
        )
    }

    // Determine exact sub-point coordinate for the target reticle
    val targetSvgPt = when {
        cmd is PathCommand.CubicTo && selectedNode.subPoint == NodeSubPoint.CONTROL_1 -> cmd.c1
        cmd is PathCommand.CubicTo && selectedNode.subPoint == NodeSubPoint.CONTROL_2 -> cmd.c2
        else -> activeAnchorSvg
    }
    val targetScreen = mapPt(targetSvgPt)
    val reticleColor = if (selectedNode.subPoint == NodeSubPoint.ANCHOR) {
        EmeraldNodeOptimized
    } else {
        AmberTangentSecondary
    }

    val outerRadius = (10.dp.toPx() / zoom).coerceAtLeast(4.5f)
    val innerRadius = (4.2.dp.toPx() / zoom).coerceAtLeast(2.0f)
    val ringStroke = (1.8.dp.toPx() / zoom).coerceAtLeast(0.9f)
    val tickLen = (4.5.dp.toPx() / zoom).coerceAtLeast(2.0f)

    // Outer Halo & Ring
    drawCircle(
        color = reticleColor.copy(alpha = 0.22f),
        radius = outerRadius * 1.35f,
        center = targetScreen
    )
    drawCircle(
        color = reticleColor,
        radius = outerRadius,
        center = targetScreen,
        style = Stroke(width = ringStroke)
    )

    // 4 Crosshair Ticks
    drawLine(
        color = reticleColor,
        start = Offset(targetScreen.x - outerRadius - tickLen, targetScreen.y),
        end = Offset(targetScreen.x - outerRadius + tickLen * 0.4f, targetScreen.y),
        strokeWidth = ringStroke
    )
    drawLine(
        color = reticleColor,
        start = Offset(targetScreen.x + outerRadius - tickLen * 0.4f, targetScreen.y),
        end = Offset(targetScreen.x + outerRadius + tickLen, targetScreen.y),
        strokeWidth = ringStroke
    )
    drawLine(
        color = reticleColor,
        start = Offset(targetScreen.x, targetScreen.y - outerRadius - tickLen),
        end = Offset(targetScreen.x, targetScreen.y - outerRadius + tickLen * 0.4f),
        strokeWidth = ringStroke
    )
    drawLine(
        color = reticleColor,
        start = Offset(targetScreen.x, targetScreen.y + outerRadius - tickLen * 0.4f),
        end = Offset(targetScreen.x, targetScreen.y + outerRadius + tickLen),
        strokeWidth = ringStroke
    )

    // Center Point
    drawCircle(
        color = Color.White,
        radius = innerRadius,
        center = targetScreen
    )
    drawCircle(
        color = reticleColor,
        radius = innerRadius,
        center = targetScreen,
        style = Stroke(width = ringStroke)
    )
}

/**
 * Interactive Node Inspector Control Panel.
 * Allows stepping through Bezier nodes, selecting Anchor vs Control Handles (C1/C2),
 * shifting node coordinates with selectable step precision, converting sharp/curve nodes,
 * or deleting specific path nodes.
 */
@Composable
fun NodeInspectorControlPanel(
    nodeInfo: EditableNodeInfo?,
    selectedSubPoint: NodeSubPoint,
    onStepNode: (Int) -> Unit,
    onSelectSubPoint: (NodeSubPoint) -> Unit,
    onShiftNode: (Float, Float) -> Unit,
    onDeleteNode: () -> Unit,
    onToggleCurveType: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nudgeStepPx by remember { mutableFloatStateOf(2.0f) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .testTag("node_inspector_control_panel")
    ) {
        if (nodeInfo == null) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Node Inspector (Titik Bezier)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ketuk langsung titik pada kanvas atau gunakan tombol navigasi untuk memilih, menggeser, atau menghapus simpul path SVG.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val activeCoord: VecPoint = when (selectedSubPoint) {
                NodeSubPoint.ANCHOR -> nodeInfo.anchor
                NodeSubPoint.CONTROL_1 -> nodeInfo.control1 ?: nodeInfo.anchor
                NodeSubPoint.CONTROL_2 -> nodeInfo.control2 ?: nodeInfo.anchor
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Top Navigation Row: Prev Node | Node Index & Layer Info | Next Node
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onStepNode(-1) },
                        modifier = Modifier
                            .size(34.dp)
                            .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .testTag("node_inspector_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Simpul Sebelumnya",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = EmeraldNodeOptimized,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Simpul #${nodeInfo.globalNodeOrdinal} / ${nodeInfo.totalEditableNodes}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "${nodeInfo.layerName} • ${nodeInfo.commandTypeLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanVectorPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { onStepNode(1) },
                        modifier = Modifier
                            .size(34.dp)
                            .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .testTag("node_inspector_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Simpul Berikutnya",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 2. Sub-Point Target Selector (Anchor vs Control Handle C1 / C2 for Cubic Bezier)
                if (nodeInfo.control1 != null && nodeInfo.control2 != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NodeSubPoint.entries.forEach { sub ->
                            val selected = selectedSubPoint == sub
                            val accent = if (sub == NodeSubPoint.ANCHOR) EmeraldNodeOptimized else AmberTangentSecondary
                            Surface(
                                color = if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        0.8.dp,
                                        if (selected) accent else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectSubPoint(sub) }
                                    .testTag("node_subpoint_${sub.name}")
                            ) {
                                Text(
                                    text = sub.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Coordinate Telemetry & Step Precision + Directional Shift Controls
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.6.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Coordinate Readout + Step Selector (0.5px | 2px | 8px)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "KOORDINAT SVG (${selectedSubPoint.label.uppercase(Locale.US)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "X: %.1f px   •   Y: %.1f px", activeCoord.x, activeCoord.y),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EmeraldNodeOptimized
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(0.5f to "0.5px", 2.0f to "2px", 8.0f to "8px").forEach { (stepVal, label) ->
                                    val isStepSelected = nudgeStepPx == stepVal
                                    Surface(
                                        color = if (isStepSelected) CyanVectorPrimary else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .border(
                                                0.6.dp,
                                                if (isStepSelected) CyanVectorPrimary else MaterialTheme.colorScheme.outline,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { nudgeStepPx = stepVal }
                                            .testTag("nudge_step_$label")
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isStepSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isStepSelected) Color(0xFF05202E) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Directional Shift Bar: Left (-X), Up (-Y), Down (+Y), Right (+X)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            NudgeDirectionButton(
                                label = "← X",
                                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                onClick = { onShiftNode(-nudgeStepPx, 0f) },
                                testTag = "nudge_node_left",
                                modifier = Modifier.weight(1f)
                            )
                            NudgeDirectionButton(
                                label = "↑ Y",
                                icon = Icons.Default.KeyboardArrowUp,
                                onClick = { onShiftNode(0f, -nudgeStepPx) },
                                testTag = "nudge_node_up",
                                modifier = Modifier.weight(1f)
                            )
                            NudgeDirectionButton(
                                label = "↓ Y",
                                icon = Icons.Default.KeyboardArrowDown,
                                onClick = { onShiftNode(0f, nudgeStepPx) },
                                testTag = "nudge_node_down",
                                modifier = Modifier.weight(1f)
                            )
                            NudgeDirectionButton(
                                label = "X →",
                                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                onClick = { onShiftNode(nudgeStepPx, 0f) },
                                testTag = "nudge_node_right",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

                // 4. Node Geometry Actions: Convert Sharp/Bezier Curve & Delete Node
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onToggleCurveType,
                        enabled = nodeInfo.canConvertCurveLine,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("toggle_node_curve_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = "Ubah Lurus atau Lengkung",
                            tint = CyanVectorPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (nodeInfo.control1 != null) "Jadikan Lurus" else "Jadikan Bezier",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    OutlinedButton(
                        onClick = onDeleteNode,
                        enabled = nodeInfo.canDelete,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CoralDanger
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("delete_selected_node_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Hapus Titik Simpul",
                            tint = if (nodeInfo.canDelete) CoralDanger else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Hapus Titik",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NudgeDirectionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .height(34.dp)
            .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = CyanVectorPrimary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
