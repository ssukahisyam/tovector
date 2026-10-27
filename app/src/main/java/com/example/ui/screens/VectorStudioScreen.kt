package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CanvasViewMode
import com.example.model.NodeSubPoint
import com.example.model.SelectedPathNode
import com.example.model.TraceConfig
import com.example.model.TraceMode
import com.example.model.VectorPathLayer
import com.example.ui.VectoForgeUiState
import com.example.ui.components.NodeInspectorControlPanel
import com.example.ui.components.TracingAlgorithmSelector
import com.example.ui.components.TracingParameterControlPanel
import com.example.ui.components.VectorInteractiveCanvas
import com.example.ui.components.parseHexColor
import com.example.ui.theme.EmeraldNodeOptimized

@Composable
fun VectorStudioScreen(
    uiState: VectoForgeUiState,
    enableBackToQueue: Boolean,
    onNavigateBackToQueue: () -> Unit,
    onSelectBatchItem: (String) -> Unit,
    onSetCanvasViewMode: (CanvasViewMode) -> Unit,
    onSetSplitFraction: (Float) -> Unit,
    onToggleWireframeOverlay: () -> Unit,
    onToggleBezierHandles: () -> Unit,
    onSetRasterBackdropOpacity: (Float) -> Unit,
    onToggleNodeInspector: () -> Unit = {},
    onSelectPathNode: (SelectedPathNode?) -> Unit = {},
    onSelectNodeSubPoint: (NodeSubPoint) -> Unit = {},
    onStepSelectedNode: (Int) -> Unit = {},
    onShiftSelectedNode: (Float, Float) -> Unit = { _, _ -> },
    onDeleteSelectedNode: () -> Unit = {},
    onToggleSelectedNodeCurveType: () -> Unit = {},
    onToggleUseGlobal: (Boolean) -> Unit,
    onSelectModeForActive: (TraceMode) -> Unit,
    onUpdateActiveConfig: ((TraceConfig) -> TraceConfig) -> Unit,
    onToggleLayerVisibility: (String, String) -> Unit,
    onDeleteLayer: (String, String) -> Unit,
    onSaveSingleSvg: () -> Unit,
    onShareSingleSvg: () -> Unit,
    onOpenSvgInspector: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (enableBackToQueue) {
        BackHandler {
            onNavigateBackToQueue()
        }
    }

    val activeItem = uiState.selectedItem
    if (activeItem == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Pilih gambar dari Antrean Batch terlebih dahulu.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val activeConfig = activeItem.effectiveConfig(uiState.globalConfig)
    val traceResult = activeItem.result
    var studioSubTab by remember { mutableIntStateOf(0) } // 0 = Parameter, 1 = Mode, 2 = Layer

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Minimalist Item Switcher Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            uiState.batchItems.forEach { item ->
                val selected = item.id == activeItem.id
                Surface(
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .border(
                            0.8.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(20.dp)
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSelectBatchItem(item.id) }
                        .testTag("studio_switcher_${item.id}")
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 2. Clean View Mode Bar + Node Inspector & Wireframe Overlay Toggle Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = uiState.isNodeInspectorEnabled,
                onClick = onToggleNodeInspector,
                label = {
                    Text(
                        text = "Node Inspector (Edit Titik)",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                modifier = Modifier.testTag("chip_node_inspector")
            )

            FilterChip(
                selected = uiState.showWireframeOverlay || uiState.canvasViewMode == CanvasViewMode.WIREFRAME_NODES,
                onClick = onToggleWireframeOverlay,
                label = {
                    Text(
                        text = "Wireframe Overlay",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                modifier = Modifier.testTag("chip_wireframe_overlay")
            )

            CanvasViewMode.entries.forEach { mode ->
                FilterChip(
                    selected = uiState.canvasViewMode == mode,
                    onClick = { onSetCanvasViewMode(mode) },
                    label = {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    modifier = Modifier.testTag("view_mode_${mode.name}")
                )
            }
        }

        // 3. Vector Canvas with Toggleable Wireframe View & Interactive Node Inspector Overlay
        VectorInteractiveCanvas(
            sourceBitmap = activeItem.sourceBitmap,
            traceResult = traceResult,
            viewMode = uiState.canvasViewMode,
            splitFraction = uiState.splitFraction,
            showWireframeOverlay = uiState.showWireframeOverlay,
            showBezierHandles = uiState.showBezierHandles,
            rasterBackdropOpacity = uiState.rasterBackdropOpacity,
            isNodeInspectorEnabled = uiState.isNodeInspectorEnabled,
            selectedPathNode = uiState.selectedPathNode,
            onSplitFractionChange = onSetSplitFraction,
            onToggleWireframeOverlay = onToggleWireframeOverlay,
            onToggleBezierHandles = onToggleBezierHandles,
            onRasterBackdropOpacityChange = onSetRasterBackdropOpacity,
            onToggleNodeInspector = onToggleNodeInspector,
            onSelectPathNode = onSelectPathNode,
            onShiftSelectedNode = onShiftSelectedNode,
            onDeleteSelectedNode = onDeleteSelectedNode,
            modifier = Modifier
                .fillMaxWidth()
                .height(308.dp)
        )

        // 3B. Interactive Node Inspector Panel (Appears when Node Inspector is active)
        AnimatedVisibility(visible = uiState.isNodeInspectorEnabled) {
            NodeInspectorControlPanel(
                nodeInfo = uiState.activeEditableNodeInfo,
                selectedSubPoint = uiState.selectedPathNode?.subPoint ?: NodeSubPoint.ANCHOR,
                onStepNode = onStepSelectedNode,
                onSelectSubPoint = onSelectNodeSubPoint,
                onShiftNode = onShiftSelectedNode,
                onDeleteNode = onDeleteSelectedNode,
                onToggleCurveType = onToggleSelectedNodeCurveType
            )
        }

        // 4. Minimalist Metrics & Export Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (traceResult != null) {
                Column {
                    Text(
                        text = "${traceResult.totalNodes} Nodes • ${traceResult.totalCurves} Kurva • ${traceResult.totalLines} Garis",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Reduksi titik -${traceResult.reductionPercent}% • ${traceResult.visibleLayers.size} layer aktif",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldNodeOptimized
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onOpenSvgInspector,
                    modifier = Modifier
                        .size(38.dp)
                        .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .testTag("studio_inspect_code_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Kode SVG",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onShareSingleSvg,
                    modifier = Modifier
                        .size(38.dp)
                        .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .testTag("studio_share_svg_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Bagikan SVG",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Button(
                    onClick = onSaveSingleSvg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("studio_save_svg_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Simpan SVG",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simpan")
                }
            }
        }

        // 5. Minimalist Segmented Control (Parameter & Mode | Layer Pruner)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf(
                    "Parameter Real-Time" to "subtab_parameters",
                    "5 Mode Algoritma" to "subtab_modes",
                    "Layer (${traceResult?.layers?.size ?: 0})" to "subtab_layers"
                )
                tabs.forEachIndexed { idx, (label, tag) ->
                    val selected = studioSubTab == idx
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { studioSubTab = idx }
                            .testTag(tag)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }

        when (studioSubTab) {
            0 -> {
                TracingParameterControlPanel(
                    config = activeConfig,
                    isProcessing = activeItem.status == com.example.model.BatchItemStatus.PROCESSING,
                    useGlobalConfig = activeItem.useGlobalConfig,
                    onToggleUseGlobal = onToggleUseGlobal,
                    onUpdateConfig = onUpdateActiveConfig,
                    onResetToModeDefault = {
                        onSelectModeForActive(activeConfig.mode)
                    },
                    tagPrefix = "studio_param"
                )
            }
            1 -> {
                TracingAlgorithmSelector(
                    selectedMode = activeConfig.mode,
                    onModeSelected = onSelectModeForActive,
                    headerTitle = "",
                    defaultCompactCarousel = false,
                    tagPrefix = "studio_mode"
                )
            }
            else -> {
                MinimalLayerPrunerPanel(
                    itemId = activeItem.id,
                    layers = traceResult?.layers ?: emptyList(),
                    onToggleLayerVisibility = onToggleLayerVisibility,
                    onDeleteLayer = onDeleteLayer
                )
            }
        }
    }
}

@Composable
private fun MinimalLayerPrunerPanel(
    itemId: String,
    layers: List<VectorPathLayer>,
    onToggleLayerVisibility: (String, String) -> Unit,
    onDeleteLayer: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (layers.isEmpty()) {
            Text(
                text = "Tidak ada layer yang tersisa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            layers.forEach { layer ->
                val swatchHex = layer.fillColorHex ?: layer.strokeColorHex ?: "#111827"
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(parseHexColor(swatchHex))
                                    .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            )
                            Column {
                                Text(
                                    text = layer.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (layer.isVisible) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${layer.nodeCount} nodes • ${layer.curveCount} kurva",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row {
                            IconButton(
                                onClick = { onToggleLayerVisibility(itemId, layer.id) },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("toggle_layer_${layer.id}")
                            ) {
                                Icon(
                                    imageVector = if (layer.isVisible) {
                                        Icons.Default.Visibility
                                    } else {
                                        Icons.Default.VisibilityOff
                                    },
                                    contentDescription = "Visibilitas",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDeleteLayer(itemId, layer.id) },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("delete_layer_${layer.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Hapus",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
