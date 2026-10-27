package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.model.CanvasViewMode
import com.example.model.NodeSubPoint
import com.example.model.PathCommand
import com.example.model.SelectedPathNode
import com.example.model.VecPoint
import com.example.model.VectorPathLayer
import com.example.model.VectorTraceResult
import com.example.ui.theme.AmberTangentSecondary
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CyanVectorPrimary
import com.example.ui.theme.EmeraldNodeOptimized
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Interactive Image & SVG Vector Preview Component with:
 * - Pinch-to-Zoom (0.6x to 8.0x) and Pan functionality around focal centroid
 * - Synchronized Split-Screen Slider (Before Raster vs After SVG Trace)
 * - Toggleable Wireframe View overlay over the original image
 * - Simultaneous Interactive Node Inspector overlay that remains active and usable
 *   even when zoomed in for sub-pixel precision Bezier node editing.
 */
@Composable
fun VectorInteractiveCanvas(
    sourceBitmap: Bitmap,
    traceResult: VectorTraceResult?,
    viewMode: CanvasViewMode,
    splitFraction: Float,
    showWireframeOverlay: Boolean,
    showBezierHandles: Boolean,
    rasterBackdropOpacity: Float,
    isNodeInspectorEnabled: Boolean = false,
    selectedPathNode: SelectedPathNode? = null,
    onSplitFractionChange: (Float) -> Unit,
    onToggleWireframeOverlay: () -> Unit,
    onToggleBezierHandles: () -> Unit,
    onRasterBackdropOpacityChange: (Float) -> Unit,
    onToggleNodeInspector: () -> Unit = {},
    onSelectPathNode: (SelectedPathNode?) -> Unit = {},
    onShiftSelectedNode: (Float, Float) -> Unit = { _, _ -> },
    onDeleteSelectedNode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize(1, 1)) }

    val imageBitmap = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }
    val density = LocalDensity.current
    val hitTolerancePx = with(density) { 28.dp.toPx() }
    val padPx = with(density) { 24.dp.toPx() }
    val tapSlopPx = with(density) { 10.dp.toPx() }

    val isWireframeActive = showWireframeOverlay ||
        viewMode == CanvasViewMode.WIREFRAME_NODES ||
        isNodeInspectorEnabled

    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(panOffset)
    val currentTraceResult by rememberUpdatedState(traceResult)
    val currentNodeInspector by rememberUpdatedState(isNodeInspectorEnabled)
    val currentWireframe by rememberUpdatedState(isWireframeActive)
    val currentHandles by rememberUpdatedState(showBezierHandles)

    // Helper to focus & zoom onto the currently selected Bezier node for precision editing
    val focusOnSelectedNode: () -> Unit = {
        val res = traceResult
        val sel = selectedPathNode
        if (res != null && sel != null && viewportSize.width > 10 && viewportSize.height > 10) {
            val layer = res.visibleLayers.find { it.id == sel.layerId }
            val cmd = layer?.commands?.getOrNull(sel.commandIndex)
            val targetPt: VecPoint? = when (cmd) {
                is PathCommand.MoveTo -> cmd.p
                is PathCommand.LineTo -> cmd.p
                is PathCommand.CubicTo -> when (sel.subPoint) {
                    NodeSubPoint.ANCHOR -> cmd.p
                    NodeSubPoint.CONTROL_1 -> cmd.c1
                    NodeSubPoint.CONTROL_2 -> cmd.c2
                }
                else -> null
            }
            if (targetPt != null) {
                val cw = viewportSize.width.toFloat()
                val ch = viewportSize.height.toFloat()
                val imgW = res.width.coerceAtLeast(1).toFloat()
                val imgH = res.height.coerceAtLeast(1).toFloat()
                val availW = (cw - padPx * 2).coerceAtLeast(10f)
                val availH = (ch - padPx * 2).coerceAtLeast(10f)
                val fitScale = min(availW / imgW, availH / imgH)
                val originX = (cw - imgW * fitScale) * 0.5f
                val originY = (ch - imgH * fitScale) * 0.5f

                val artboardX = originX + targetPt.x * fitScale
                val artboardY = originY + targetPt.y * fitScale
                val targetZoom = max(zoom, 3.2f).coerceIn(1f, 8.0f)
                zoom = targetZoom
                panOffset = Offset(
                    x = -(artboardX - cw * 0.5f) * targetZoom,
                    y = -(artboardY - ch * 0.5f) * targetZoom
                )
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { viewportSize = it }
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0E14))
            .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .testTag("vector_interactive_canvas")
    ) {
        if (viewMode == CanvasViewMode.SPLIT_COMPARE) {
            SplitScreenVectorCompare(
                sourceBitmap = sourceBitmap,
                traceResult = traceResult,
                splitFraction = splitFraction,
                zoom = zoom,
                panOffset = panOffset,
                showWireframeOverlay = isWireframeActive,
                showBezierHandles = showBezierHandles,
                isNodeInspectorEnabled = isNodeInspectorEnabled,
                selectedPathNode = selectedPathNode,
                onSplitFractionChange = onSplitFractionChange,
                onZoomPanChange = { newZoom, newPan ->
                    zoom = newZoom
                    panOffset = newPan
                },
                onSelectPathNode = onSelectPathNode,
                onShiftSelectedNode = onShiftSelectedNode,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(viewMode, sourceBitmap) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val canvasW = size.width.toFloat().coerceAtLeast(1f)
                            val canvasH = size.height.toFloat().coerceAtLeast(1f)

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

                            // Hit-test Bezier node at touch-down position (accurately accounting for zoom & panOffset)
                            val hitNode: SelectedPathNode? = if (res != null && (currentNodeInspector || currentWireframe)) {
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

                            if (hitNode != null && currentNodeInspector) {
                                onSelectPathNode(hitNode)
                            }

                            var isDraggingSelectedNode = hitNode != null && currentNodeInspector
                            var usedMultiTouch = false
                            var totalMoveDist = 0f

                            do {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }

                                if (pressedCount >= 2) {
                                    // Two-finger Pinch-to-Zoom & Pan: always active even when Node Inspector is enabled!
                                    usedMultiTouch = true
                                    isDraggingSelectedNode = false

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
                                    zoom = newZoom
                                    panOffset = if (newZoom <= 1.01f && hypot(rawPan.x, rawPan.y) < 12f) {
                                        Offset.Zero
                                    } else {
                                        Offset(
                                            x = rawPan.x.coerceIn(-maxPanX, maxPanX),
                                            y = rawPan.y.coerceIn(-maxPanY, maxPanY)
                                        )
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else if (pressedCount == 1 && !usedMultiTouch) {
                                    val change = event.changes.first()
                                    val delta = change.position - change.previousPosition
                                    val stepDist = hypot(delta.x, delta.y)
                                    totalMoveDist += stepDist

                                    if (stepDist > 0.2f) {
                                        if (isDraggingSelectedNode) {
                                            // Precision node shifting scaled inversely by current zoom level
                                            val effectiveScale = (fitScale * currentZoom).coerceAtLeast(0.1f)
                                            onShiftSelectedNode(
                                                delta.x / effectiveScale,
                                                delta.y / effectiveScale
                                            )
                                            change.consume()
                                        } else if (currentZoom > 1.01f || currentPan != Offset.Zero) {
                                            // Dragging on empty space pans the zoomed viewport while Node Inspector stays active
                                            val maxPanX = canvasW * max(0.8f, (currentZoom - 0.4f) * 0.65f)
                                            val maxPanY = canvasH * max(0.8f, (currentZoom - 0.4f) * 0.65f)
                                            panOffset = Offset(
                                                x = (currentPan.x + delta.x).coerceIn(-maxPanX, maxPanX),
                                                y = (currentPan.y + delta.y).coerceIn(-maxPanY, maxPanY)
                                            )
                                            change.consume()
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            // Pure tap selects the hit Bezier node at any zoom level
                            if (!usedMultiTouch && totalMoveDist < tapSlopPx && hitNode != null) {
                                onSelectPathNode(hitNode)
                            }
                        }
                    }
            ) {
                drawStudioCheckerboard()

                val imgW = (traceResult?.width ?: sourceBitmap.width).coerceAtLeast(1).toFloat()
                val imgH = (traceResult?.height ?: sourceBitmap.height).coerceAtLeast(1).toFloat()

                val pad = 24.dp.toPx()
                val availW = (size.width - pad * 2).coerceAtLeast(10f)
                val availH = (size.height - pad * 2).coerceAtLeast(10f)
                val fitScale = min(availW / imgW, availH / imgH)

                val drawW = imgW * fitScale
                val drawH = imgH * fitScale
                val left = (size.width - drawW) * 0.5f
                val top = (size.height - drawH) * 0.5f

                withTransform({
                    translate(panOffset.x, panOffset.y)
                    scale(zoom, zoom, pivot = Offset(size.width * 0.5f, size.height * 0.5f))
                }) {
                    // Artboard paper backdrop
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(left, top),
                        size = Size(drawW, drawH)
                    )

                    when (viewMode) {
                        CanvasViewMode.ORIGINAL_RASTER -> {
                            drawImage(
                                image = imageBitmap,
                                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
                                alpha = if (isWireframeActive) rasterBackdropOpacity else 1f
                            )
                            if (isWireframeActive && traceResult != null) {
                                drawWireframeAndNodes(
                                    layers = traceResult.visibleLayers,
                                    originX = left,
                                    originY = top,
                                    scale = fitScale,
                                    zoom = zoom,
                                    showHandles = showBezierHandles
                                )
                            }
                        }

                        CanvasViewMode.VECTOR_CLEAN -> {
                            if (isWireframeActive) {
                                drawImage(
                                    image = imageBitmap,
                                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                    dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
                                    alpha = rasterBackdropOpacity * 0.45f
                                )
                            }
                            if (traceResult != null) {
                                drawTracedLayers(
                                    layers = traceResult.visibleLayers,
                                    originX = left,
                                    originY = top,
                                    scale = fitScale,
                                    alpha = if (isWireframeActive) 0.55f else 1f
                                )
                                if (isWireframeActive) {
                                    drawWireframeAndNodes(
                                        layers = traceResult.visibleLayers,
                                        originX = left,
                                        originY = top,
                                        scale = fitScale,
                                        zoom = zoom,
                                        showHandles = showBezierHandles
                                    )
                                }
                            }
                        }

                        CanvasViewMode.WIREFRAME_NODES, CanvasViewMode.SPLIT_COMPARE -> {
                            drawImage(
                                image = imageBitmap,
                                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
                                alpha = rasterBackdropOpacity
                            )
                            if (traceResult != null) {
                                drawTracedLayers(
                                    layers = traceResult.visibleLayers,
                                    originX = left,
                                    originY = top,
                                    scale = fitScale,
                                    alpha = 0.22f
                                )
                                drawWireframeAndNodes(
                                    layers = traceResult.visibleLayers,
                                    originX = left,
                                    originY = top,
                                    scale = fitScale,
                                    zoom = zoom,
                                    showHandles = showBezierHandles
                                )
                            }
                        }
                    }

                    // Draw Interactive Node Inspector Target Reticle if a node is selected
                    if (traceResult != null && selectedPathNode != null && (isWireframeActive || isNodeInspectorEnabled)) {
                        drawSelectedNodeReticle(
                            layers = traceResult.visibleLayers,
                            selectedNode = selectedPathNode,
                            originX = left,
                            originY = top,
                            scale = fitScale,
                            zoom = zoom
                        )
                    }

                    // Artboard crisp border
                    drawRect(
                        color = Color(0xFF334155),
                        topLeft = Offset(left, top),
                        size = Size(drawW, drawH),
                        style = Stroke(width = 1.5f / zoom)
                    )
                }
            }
        }

        // Top HUD Bar: Toggleable Wireframe View Overlay, Interactive Node Inspector & Bezier Handles
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Toggleable Wireframe Overlay Pill
                Surface(
                    color = if (isWireframeActive) {
                        CyanVectorPrimary
                    } else {
                        Color(0xCC0D1117)
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .border(
                            0.8.dp,
                            if (isWireframeActive) CyanVectorPrimary else Color(0xFF334155),
                            RoundedCornerShape(18.dp)
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onToggleWireframeOverlay() }
                        .testTag("toggle_wireframe_overlay_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Polyline,
                            contentDescription = "Wireframe View Overlay",
                            tint = if (isWireframeActive) Color(0xFF06202E) else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isWireframeActive) "Wireframe: ON" else "Wireframe: OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isWireframeActive) Color(0xFF06202E) else Color.White
                        )
                    }
                }

                // Interactive Node Inspector Toggle Pill
                Surface(
                    color = if (isNodeInspectorEnabled) {
                        EmeraldNodeOptimized
                    } else {
                        Color(0xCC0D1117)
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .border(
                            0.8.dp,
                            if (isNodeInspectorEnabled) EmeraldNodeOptimized else Color(0xFF334155),
                            RoundedCornerShape(18.dp)
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onToggleNodeInspector() }
                        .testTag("toggle_node_inspector_hud_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Node Inspector",
                            tint = if (isNodeInspectorEnabled) Color(0xFF032217) else EmeraldNodeOptimized,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isNodeInspectorEnabled) "Edit Simpul: ON" else "Edit Simpul",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isNodeInspectorEnabled) Color(0xFF032217) else Color.White
                        )
                    }
                }

                // Toggle Bezier Tangent Handles Pill when Wireframe is active
                AnimatedVisibility(visible = isWireframeActive) {
                    Surface(
                        color = if (showBezierHandles) {
                            AmberTangentSecondary
                        } else {
                            Color(0xCC0D1117)
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .border(
                                0.8.dp,
                                if (showBezierHandles) AmberTangentSecondary else Color(0xFF334155),
                                RoundedCornerShape(18.dp)
                            )
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onToggleBezierHandles() }
                            .testTag("toggle_bezier_handles_hud_button")
                    ) {
                        Text(
                            text = if (showBezierHandles) "Handles: ON" else "Handles: OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (showBezierHandles) Color(0xFF2B1100) else Color.White,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Quick Delete Node Button in HUD when a node is selected
            if (isNodeInspectorEnabled && selectedPathNode != null) {
                Surface(
                    color = Color(0xDD280A0E),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .border(0.8.dp, CoralDanger, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onDeleteSelectedNode() }
                        .testTag("hud_delete_node_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Hapus Simpul Terpilih",
                            tint = CoralDanger,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Hapus",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CoralDanger
                        )
                    }
                }
            }
        }

        // Bottom HUD Bar: Wireframe Legend / Opacity Control + Precision Zoom & Focus Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = 10.dp,
                    end = 10.dp,
                    bottom = if (viewMode == CanvasViewMode.SPLIT_COMPARE) 40.dp else 10.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isWireframeActive && viewMode != CanvasViewMode.SPLIT_COMPARE) {
                Surface(
                    color = Color(0xD90D1117),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.border(0.8.dp, Color(0xFF30363D), RoundedCornerShape(10.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LegendDot(color = CyanVectorPrimary, label = "Sudut")
                            LegendDot(
                                color = EmeraldNodeOptimized,
                                label = if (isNodeInspectorEnabled) "Simpul Aktif" else "Kurva"
                            )
                            if (showBezierHandles) {
                                LegendDot(color = AmberTangentSecondary, label = "Tangent")
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = "Opasitas Gambar Asli",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Asli ${(rasterBackdropOpacity * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCBD5E1)
                            )
                            Slider(
                                value = rasterBackdropOpacity,
                                onValueChange = onRasterBackdropOpacityChange,
                                valueRange = 0.0f..1.0f,
                                modifier = Modifier
                                    .width(76.dp)
                                    .size(height = 18.dp, width = 76.dp)
                                    .testTag("wireframe_backdrop_opacity_slider")
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Interactive Zoom (- | 100%..800% | +), Focus Selected Node & Reset Pill
            Surface(
                color = Color(0xE611161F),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.border(0.8.dp, Color(0xFF30363D), RoundedCornerShape(20.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    // Zoom Out (-)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable {
                                val nextZoom = (zoom / 1.35f).coerceIn(0.6f, 8.0f)
                                zoom = nextZoom
                                if (nextZoom <= 1.02f) {
                                    panOffset = Offset.Zero
                                } else {
                                    panOffset *= (nextZoom / zoom)
                                }
                            }
                            .testTag("zoom_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Perkecil Zoom",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = "${(zoom * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyanVectorPrimary,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .testTag("zoom_percentage_text")
                    )

                    // Zoom In (+)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable {
                                val oldZoom = zoom
                                val nextZoom = (oldZoom * 1.35f).coerceIn(0.6f, 8.0f)
                                zoom = nextZoom
                                panOffset *= (nextZoom / oldZoom)
                            }
                            .testTag("zoom_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Perbesar Zoom",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Focus & Zoom on Selected Node button when Node Inspector has an active node
                    if (selectedPathNode != null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { focusOnSelectedNode() }
                                .testTag("focus_selected_node_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterCenterFocus,
                                contentDescription = "Fokus ke Simpul Terpilih",
                                tint = EmeraldNodeOptimized,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Reset Zoom & Pan to 100%
                    if (zoom != 1f || panOffset != Offset.Zero) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable {
                                    zoom = 1f
                                    panOffset = Offset.Zero
                                }
                                .testTag("reset_zoom_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CenterFocusStrong,
                                contentDescription = "Reset Zoom 100%",
                                tint = AmberTangentSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

@Composable
fun MiniVectorPreviewCanvas(
    traceResult: VectorTraceResult,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .background(Color.White)
    ) {
        val imgW = traceResult.width.coerceAtLeast(1).toFloat()
        val imgH = traceResult.height.coerceAtLeast(1).toFloat()
        val scale = min(size.width / imgW, size.height / imgH)
        val ox = (size.width - imgW * scale) * 0.5f
        val oy = (size.height - imgH * scale) * 0.5f
        drawTracedLayers(
            layers = traceResult.visibleLayers,
            originX = ox,
            originY = oy,
            scale = scale,
            alpha = 1f
        )
    }
}

private fun DrawScope.drawStudioCheckerboard() {
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

private fun DrawScope.drawTracedLayers(
    layers: List<VectorPathLayer>,
    originX: Float,
    originY: Float,
    scale: Float,
    alpha: Float
) {
    for (layer in layers) {
        val composePath = buildComposePath(layer.commands, originX, originY, scale)
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

private fun DrawScope.drawWireframeAndNodes(
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
        val path = buildComposePath(layer.commands, originX, originY, scale)
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

private fun buildComposePath(
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

fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFF111827)
    }
}
