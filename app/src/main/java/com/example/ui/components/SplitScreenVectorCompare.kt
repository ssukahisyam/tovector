package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.model.PathCommand
import com.example.model.SelectedPathNode
import com.example.model.VecPoint
import com.example.model.VectorPathLayer
import com.example.model.VectorTraceResult
import com.example.ui.theme.AmberTangentSecondary
import com.example.ui.theme.CyanVectorPrimary
import com.example.ui.theme.EmeraldNodeOptimized
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Synchronized Split-Screen Slider Component that renders the original raster image
 * on the left and the real-time resulting SVG trace on the right.
 * Supports:
 * - Two-finger pinch-to-zoom (up to 8x) and pan synchronized across both curtains
 * - Interactive Node Inspector hit-testing & node dragging even when zoomed in
 * - Single-finger split curtain dragging
 */
@Composable
fun SplitScreenVectorCompare(
    sourceBitmap: Bitmap,
    traceResult: VectorTraceResult?,
    splitFraction: Float,
    zoom: Float,
    panOffset: Offset,
    showWireframeOverlay: Boolean,
    showBezierHandles: Boolean,
    isNodeInspectorEnabled: Boolean = false,
    selectedPathNode: SelectedPathNode? = null,
    onSplitFractionChange: (Float) -> Unit,
    onZoomPanChange: (Float, Offset) -> Unit,
    onSelectPathNode: (SelectedPathNode?) -> Unit = {},
    onShiftSelectedNode: (Float, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }
    val density = LocalDensity.current
    val hitTolerancePx = with(density) { 28.dp.toPx() }
    val padPx = with(density) { 22.dp.toPx() }
    val curtainGrabRadiusPx = with(density) { 30.dp.toPx() }
    val tapSlopPx = with(density) { 10.dp.toPx() }

    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(panOffset)
    val currentSplit by rememberUpdatedState(splitFraction)
    val currentTraceResult by rememberUpdatedState(traceResult)
    val currentNodeInspector by rememberUpdatedState(isNodeInspectorEnabled)
    val currentWireframe by rememberUpdatedState(showWireframeOverlay)
    val currentHandles by rememberUpdatedState(showBezierHandles)

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("split_screen_vector_compare")
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val canvasW = size.width.toFloat().coerceAtLeast(1f)
                            val canvasH = size.height.toFloat().coerceAtLeast(1f)
                            val splitPx = canvasW * currentSplit

                            val res = currentTraceResult
                            val imgW = (res?.width ?: sourceBitmap.width).coerceAtLeast(1).toFloat()
                            val imgH = (res?.height ?: sourceBitmap.height).coerceAtLeast(1).toFloat()
                            val availW = (canvasW - padPx * 2).coerceAtLeast(10f)
                            val availH = (canvasH - padPx * 2).coerceAtLeast(10f)
                            val fitScale = min(availW / imgW, availH / imgH)
                            val drawW = imgW * fitScale
                            val drawH = imgH * fitScale
                            val originX = (canvasW - drawW) * 0.5f
                            val originY = (canvasH - drawH) * 0.5f

                            // 1. Check if touch started on the split divider thumb/line
                            val nearDivider = abs(down.position.x - splitPx) <= curtainGrabRadiusPx

                            // 2. If Node Inspector (or Wireframe) is active and not grabbing divider, hit-test Bezier nodes
                            val hitNode: SelectedPathNode? = if (!nearDivider && res != null && (currentNodeInspector || currentWireframe)) {
                                hitTestNearestBezierNode(
                                    tapOffset = down.position,
                                    canvasWidth = canvasW,
                                    canvasHeight = canvasH,
                                    panOffset = currentPan,
                                    zoom = currentZoom,
                                    originX = originX,
                                    originY = originY,
                                    fitScale = fitScale,
                                    layers = res.visibleLayers,
                                    showHandles = currentHandles,
                                    maxHitDistanceScreenPx = hitTolerancePx
                                )
                            } else {
                                null
                            }

                            if (hitNode != null) {
                                onSelectPathNode(hitNode)
                            }

                            var isDraggingCurtain = nearDivider
                            var isDraggingNode = hitNode != null && currentNodeInspector
                            var usedMultiTouch = false
                            var totalMoveDist = 0f

                            do {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }

                                if (pressedCount >= 2) {
                                    usedMultiTouch = true
                                    isDraggingCurtain = false
                                    isDraggingNode = false

                                    val gestureZoom = event.calculateZoom()
                                    val gesturePan = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    val oldZoom = currentZoom
                                    val newZoom = (oldZoom * gestureZoom).coerceIn(0.6f, 8.0f)
                                    val zoomRatio = newZoom / oldZoom

                                    val center = Offset(canvasW * 0.5f, canvasH * 0.5f)
                                    val rawPan = if (centroid != Offset.Unspecified) {
                                        (currentPan + gesturePan) + (centroid - center - currentPan) * (1f - zoomRatio)
                                    } else {
                                        currentPan + gesturePan
                                    }

                                    val maxPanX = canvasW * max(0.8f, (newZoom - 0.4f) * 0.65f)
                                    val maxPanY = canvasH * max(0.8f, (newZoom - 0.4f) * 0.65f)
                                    val newPan = if (newZoom <= 1.01f && hypot(rawPan.x, rawPan.y) < 12f) {
                                        Offset.Zero
                                    } else {
                                        Offset(
                                            x = rawPan.x.coerceIn(-maxPanX, maxPanX),
                                            y = rawPan.y.coerceIn(-maxPanY, maxPanY)
                                        )
                                    }
                                    onZoomPanChange(newZoom, newPan)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else if (pressedCount == 1 && !usedMultiTouch) {
                                    val change = event.changes.first()
                                    val delta = change.position - change.previousPosition
                                    val stepDist = hypot(delta.x, delta.y)
                                    totalMoveDist += stepDist

                                    if (stepDist > 0.2f) {
                                        when {
                                            isDraggingNode -> {
                                                // Shift the selected Bezier node in SVG space while zoomed in
                                                val effectiveScale = (fitScale * currentZoom).coerceAtLeast(0.1f)
                                                onShiftSelectedNode(
                                                    delta.x / effectiveScale,
                                                    delta.y / effectiveScale
                                                )
                                                change.consume()
                                            }
                                            isDraggingCurtain -> {
                                                val nextFrac = (change.position.x / canvasW).coerceIn(0.03f, 0.97f)
                                                onSplitFractionChange(nextFrac)
                                                change.consume()
                                            }
                                            currentZoom > 1.03f -> {
                                                // Pan the zoomed viewport when dragging on empty space
                                                val maxPanX = canvasW * max(0.8f, (currentZoom - 0.4f) * 0.65f)
                                                val maxPanY = canvasH * max(0.8f, (currentZoom - 0.4f) * 0.65f)
                                                val newPan = Offset(
                                                    x = (currentPan.x + delta.x).coerceIn(-maxPanX, maxPanX),
                                                    y = (currentPan.y + delta.y).coerceIn(-maxPanY, maxPanY)
                                                )
                                                onZoomPanChange(currentZoom, newPan)
                                                change.consume()
                                            }
                                            abs(delta.x) > 1.5f -> {
                                                val nextFrac = (change.position.x / canvasW).coerceIn(0.03f, 0.97f)
                                                onSplitFractionChange(nextFrac)
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            // Handle pure tap to select a node if user didn't drag
                            if (!usedMultiTouch && totalMoveDist < tapSlopPx && hitNode != null) {
                                onSelectPathNode(hitNode)
                            }
                        }
                    }
            ) {
                drawSplitCheckerboard()

                val imgW = (traceResult?.width ?: sourceBitmap.width).coerceAtLeast(1).toFloat()
                val imgH = (traceResult?.height ?: sourceBitmap.height).coerceAtLeast(1).toFloat()

                val pad = 22.dp.toPx()
                val availW = (size.width - pad * 2).coerceAtLeast(10f)
                val availH = (size.height - pad * 2).coerceAtLeast(10f)
                val fitScale = min(availW / imgW, availH / imgH)

                val drawW = imgW * fitScale
                val drawH = imgH * fitScale
                val left = (size.width - drawW) * 0.5f
                val top = (size.height - drawH) * 0.5f

                val splitX = size.width * splitFraction
                val isWireframeVisible = showWireframeOverlay || isNodeInspectorEnabled

                // Left Curtain: Original Raster Image (synchronized zoom/pan)
                clipRect(left = 0f, top = 0f, right = splitX, bottom = size.height) {
                    withTransform({
                        translate(panOffset.x, panOffset.y)
                        scale(zoom, zoom, pivot = Offset(size.width * 0.5f, size.height * 0.5f))
                    }) {
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(left, top),
                            size = Size(drawW, drawH)
                        )
                        drawImage(
                            image = imageBitmap,
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt())
                        )
                        if (isNodeInspectorEnabled && traceResult != null && selectedPathNode != null) {
                            drawSelectedNodeReticle(
                                layers = traceResult.visibleLayers,
                                selectedNode = selectedPathNode,
                                originX = left,
                                originY = top,
                                scale = fitScale,
                                zoom = zoom
                            )
                        }
                        drawRect(
                            color = Color(0xFF334155),
                            topLeft = Offset(left, top),
                            size = Size(drawW, drawH),
                            style = Stroke(width = 1.5f / zoom)
                        )
                    }
                }

                // Right Curtain: Real-Time Resulting SVG Trace (+ optional Wireframe & Node Inspector Overlay)
                clipRect(left = splitX, top = 0f, right = size.width, bottom = size.height) {
                    withTransform({
                        translate(panOffset.x, panOffset.y)
                        scale(zoom, zoom, pivot = Offset(size.width * 0.5f, size.height * 0.5f))
                    }) {
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(left, top),
                            size = Size(drawW, drawH)
                        )
                        if (traceResult != null) {
                            drawSplitSvgLayers(
                                layers = traceResult.visibleLayers,
                                originX = left,
                                originY = top,
                                scale = fitScale,
                                alpha = if (isWireframeVisible) 0.72f else 1f
                            )
                            if (isWireframeVisible) {
                                drawSplitWireframeOverlay(
                                    layers = traceResult.visibleLayers,
                                    originX = left,
                                    originY = top,
                                    scale = fitScale,
                                    zoom = zoom,
                                    showHandles = showBezierHandles
                                )
                            }
                            if (selectedPathNode != null && isWireframeVisible) {
                                drawSelectedNodeReticle(
                                    layers = traceResult.visibleLayers,
                                    selectedNode = selectedPathNode,
                                    originX = left,
                                    originY = top,
                                    scale = fitScale,
                                    zoom = zoom
                                )
                            }
                        }
                        drawRect(
                            color = Color(0xFF334155),
                            topLeft = Offset(left, top),
                            size = Size(drawW, drawH),
                            style = Stroke(width = 1.5f / zoom)
                        )
                    }
                }

                // Vertical Split Divider Line & Tactile Thumb Handle
                drawLine(
                    color = Color.Black.copy(alpha = 0.45f),
                    start = Offset(splitX, 0f),
                    end = Offset(splitX, size.height),
                    strokeWidth = 4.dp.toPx()
                )
                drawLine(
                    color = CyanVectorPrimary,
                    start = Offset(splitX, 0f),
                    end = Offset(splitX, size.height),
                    strokeWidth = 2.dp.toPx()
                )

                val centerY = size.height * 0.5f
                val pillW = 32.dp.toPx()
                val pillH = 44.dp.toPx()
                drawRoundRect(
                    color = Color(0xEE0A0B0E),
                    topLeft = Offset(splitX - pillW * 0.5f, centerY - pillH * 0.5f),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                )
                drawRoundRect(
                    color = CyanVectorPrimary,
                    topLeft = Offset(splitX - pillW * 0.5f, centerY - pillH * 0.5f),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(width = 1.6.dp.toPx())
                )

                // Left & Right Gripper Arrows inside Thumb
                val arrowSize = 4.5.dp.toPx()
                val leftArrow = Path().apply {
                    moveTo(splitX - 3.dp.toPx(), centerY - arrowSize)
                    lineTo(splitX - 8.dp.toPx(), centerY)
                    lineTo(splitX - 3.dp.toPx(), centerY + arrowSize)
                }
                val rightArrow = Path().apply {
                    moveTo(splitX + 3.dp.toPx(), centerY - arrowSize)
                    lineTo(splitX + 8.dp.toPx(), centerY)
                    lineTo(splitX + 3.dp.toPx(), centerY + arrowSize)
                }
                drawPath(
                    leftArrow,
                    color = Color.White,
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    rightArrow,
                    color = CyanVectorPrimary,
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // Left / Right Side Indicator Badges at Bottom Corners of Split Area
            if (splitFraction > 0.18f) {
                Surface(
                    color = Color(0xCC0A0B0E),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 8.dp)
                        .border(0.6.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                ) {
                    Text(
                        text = "RASTER ASLI (${(splitFraction * 100).roundToInt()}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (splitFraction < 0.82f) {
                Surface(
                    color = Color(0xCC06202E),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 8.dp)
                        .border(0.6.dp, CyanVectorPrimary.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                ) {
                    Text(
                        text = "HASIL SVG (${((1f - splitFraction) * 100).roundToInt()}%)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyanVectorPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Bottom Precision Split Scrubber Bar + Quick Snap Presets (Asli | 50:50 | SVG)
        Surface(
            color = Color(0xFF0E131B),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SplitSnapChip(
                    label = "Asli",
                    isSelected = splitFraction >= 0.94f,
                    onClick = { onSplitFractionChange(0.96f) },
                    testTag = "split_snap_raster"
                )

                Icon(
                    imageVector = Icons.Default.Compare,
                    contentDescription = "Split Slider",
                    tint = CyanVectorPrimary,
                    modifier = Modifier.size(16.dp)
                )

                Slider(
                    value = splitFraction,
                    onValueChange = { onSplitFractionChange(it.coerceIn(0.03f, 0.97f)) },
                    valueRange = 0.03f..0.97f,
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                        .testTag("split_compare_slider")
                )

                SplitSnapChip(
                    label = "50:50",
                    isSelected = splitFraction in 0.46f..0.54f,
                    onClick = { onSplitFractionChange(0.50f) },
                    testTag = "split_snap_50"
                )

                SplitSnapChip(
                    label = "SVG",
                    isSelected = splitFraction <= 0.06f,
                    onClick = { onSplitFractionChange(0.04f) },
                    testTag = "split_snap_svg"
                )
            }
        }
    }
}

@Composable
private fun SplitSnapChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = if (isSelected) CyanVectorPrimary else Color(0xFF19212E),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF06202E) else Color(0xFFCBD5E1),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

private fun DrawScope.drawSplitCheckerboard() {
    val grid = 16.dp.toPx()
    val cols = (size.width / grid).toInt() + 1
    val rows = (size.height / grid).toInt() + 1
    val darkCell = Color(0xFF0D1117)
    val lightCell = Color(0xFF141B24)
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            drawRect(
                color = if ((r + c) % 2 == 0) darkCell else lightCell,
                topLeft = Offset(c * grid, r * grid),
                size = Size(grid, grid)
            )
        }
    }
}

internal fun DrawScope.drawSplitSvgLayers(
    layers: List<VectorPathLayer>,
    originX: Float,
    originY: Float,
    scale: Float,
    alpha: Float
) {
    for (layer in layers) {
        val composePath = buildSplitComposePath(layer.commands, originX, originY, scale)
        if (layer.strokeColorHex != null && layer.fillColorHex == null) {
            val strokeColor = parseHexColor(layer.strokeColorHex).copy(alpha = alpha)
            drawPath(
                path = composePath,
                color = strokeColor,
                style = Stroke(
                    width = (layer.strokeWidth * scale).coerceAtLeast(1.5f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        } else {
            val fillColor = parseHexColor(layer.fillColorHex ?: "#111827").copy(alpha = alpha)
            drawPath(
                path = composePath,
                color = fillColor,
                style = Fill
            )
        }
    }
}

internal fun DrawScope.drawSplitWireframeOverlay(
    layers: List<VectorPathLayer>,
    originX: Float,
    originY: Float,
    scale: Float,
    zoom: Float,
    showHandles: Boolean
) {
    val strokeW = (1.6.dp.toPx() / zoom).coerceAtLeast(0.8f)
    val handleStrokeW = (1.0.dp.toPx() / zoom).coerceAtLeast(0.5f)
    val anchorRadius = (3.4.dp.toPx() / zoom).coerceAtLeast(1.5f)
    val handleRadius = (2.4.dp.toPx() / zoom).coerceAtLeast(1.1f)

    fun mapPt(pt: VecPoint): Offset = Offset(originX + pt.x * scale, originY + pt.y * scale)

    for (layer in layers) {
        val path = buildSplitComposePath(layer.commands, originX, originY, scale)
        drawPath(
            path = path,
            color = CyanVectorPrimary,
            style = Stroke(width = strokeW)
        )

        var currentAnchor = Offset(originX, originY)
        for (cmd in layer.commands) {
            when (cmd) {
                is PathCommand.MoveTo -> {
                    currentAnchor = mapPt(cmd.p)
                    drawRect(
                        color = CyanVectorPrimary,
                        topLeft = Offset(currentAnchor.x - anchorRadius, currentAnchor.y - anchorRadius),
                        size = Size(anchorRadius * 2f, anchorRadius * 2f)
                    )
                }
                is PathCommand.LineTo -> {
                    val target = mapPt(cmd.p)
                    drawRect(
                        color = CyanVectorPrimary,
                        topLeft = Offset(target.x - anchorRadius, target.y - anchorRadius),
                        size = Size(anchorRadius * 2f, anchorRadius * 2f)
                    )
                    currentAnchor = target
                }
                is PathCommand.CubicTo -> {
                    val c1 = mapPt(cmd.c1)
                    val c2 = mapPt(cmd.c2)
                    val target = mapPt(cmd.p)

                    if (showHandles) {
                        drawLine(
                            color = AmberTangentSecondary.copy(alpha = 0.85f),
                            start = currentAnchor,
                            end = c1,
                            strokeWidth = handleStrokeW
                        )
                        drawLine(
                            color = AmberTangentSecondary.copy(alpha = 0.85f),
                            start = target,
                            end = c2,
                            strokeWidth = handleStrokeW
                        )
                        drawCircle(
                            color = AmberTangentSecondary,
                            radius = handleRadius,
                            center = c1
                        )
                        drawCircle(
                            color = AmberTangentSecondary,
                            radius = handleRadius,
                            center = c2
                        )
                    }

                    drawCircle(
                        color = EmeraldNodeOptimized,
                        radius = anchorRadius,
                        center = target
                    )
                    currentAnchor = target
                }
                is PathCommand.Close -> {}
            }
        }
    }
}

internal fun buildSplitComposePath(
    commands: List<PathCommand>,
    originX: Float,
    originY: Float,
    scale: Float
): Path {
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
    }
    for (cmd in commands) {
        when (cmd) {
            is PathCommand.MoveTo -> path.moveTo(
                originX + cmd.p.x * scale,
                originY + cmd.p.y * scale
            )
            is PathCommand.LineTo -> path.lineTo(
                originX + cmd.p.x * scale,
                originY + cmd.p.y * scale
            )
            is PathCommand.CubicTo -> path.cubicTo(
                originX + cmd.c1.x * scale,
                originY + cmd.c1.y * scale,
                originX + cmd.c2.x * scale,
                originY + cmd.c2.y * scale,
                originX + cmd.p.x * scale,
                originY + cmd.p.y * scale
            )
            is PathCommand.Close -> path.close()
        }
    }
    return path
}
