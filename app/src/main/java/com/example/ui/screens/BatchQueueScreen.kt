package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BatchImageItem
import com.example.model.BatchItemStatus
import com.example.model.TraceConfig
import com.example.model.TraceMode
import com.example.model.ZipArchiveOptions
import com.example.ui.VectoForgeUiState
import com.example.ui.components.BatchProcessingControlDashboard
import com.example.ui.components.BatchZipExportBottomSheet
import com.example.ui.components.MiniVectorPreviewCanvas
import com.example.ui.components.TracingAlgorithmSelector
import com.example.ui.components.TracingParameterControlPanel
import com.example.ui.theme.AmberTangentSecondary
import com.example.ui.theme.CyanVectorPrimary
import com.example.ui.theme.EmeraldNodeOptimized
import java.util.Locale

@Composable
fun BatchQueueScreen(
    uiState: VectoForgeUiState,
    onPickGalleryImages: () -> Unit,
    onLoadSamplePack: () -> Unit,
    onSelectGlobalMode: (TraceMode) -> Unit,
    onUpdateGlobalConfig: ((TraceConfig) -> TraceConfig) -> Unit,
    onProcessAllBatch: () -> Unit,
    onCancelBatch: () -> Unit,
    onApplyGlobalToAll: () -> Unit,
    onSelectAllForExport: (Boolean) -> Unit,
    onToggleItemExportSelection: (String) -> Unit,
    onReprocessSingleItem: (String) -> Unit,
    onUpdateZipOptions: ((ZipArchiveOptions) -> ZipArchiveOptions) -> Unit,
    onExportBatchZip: () -> Unit,
    onShareBatchZip: () -> Unit,
    onOpenItemInStudio: (String) -> Unit,
    onToggleItemUseGlobal: (String, Boolean) -> Unit,
    onRemoveItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showGlobalTuning by remember { mutableStateOf(false) }
    var showZipExportSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("batch_queue_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Batch Processing Control Dashboard & 'Export as ZIP' Module
        item {
            BatchProcessingControlDashboard(
                uiState = uiState,
                onPickGalleryImages = onPickGalleryImages,
                onRunBatchAll = onProcessAllBatch,
                onCancelBatch = onCancelBatch,
                onApplyGlobalToAll = onApplyGlobalToAll,
                onSelectAllForExport = onSelectAllForExport,
                onOpenZipPackageSheet = { showZipExportSheet = true },
                onQuickExportZip = onExportBatchZip,
                onReloadSamples = onLoadSamplePack
            )
        }

        // 2. Minimalist Global Preset Selector
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Preset Global Batch",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = { showGlobalTuning = !showGlobalTuning },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("toggle_global_sliders_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Parameter Global",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showGlobalTuning) "Tutup Parameter" else "Atur Toleransi",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                TracingAlgorithmSelector(
                    selectedMode = uiState.globalConfig.mode,
                    onModeSelected = onSelectGlobalMode,
                    headerTitle = "",
                    defaultCompactCarousel = true,
                    tagPrefix = "global_mode"
                )

                AnimatedVisibility(visible = showGlobalTuning) {
                    TracingParameterControlPanel(
                        config = uiState.globalConfig,
                        isProcessing = uiState.isBatchProcessing,
                        useGlobalConfig = null,
                        onUpdateConfig = onUpdateGlobalConfig,
                        onResetToModeDefault = {
                            onSelectGlobalMode(uiState.globalConfig.mode)
                        },
                        tagPrefix = "global_param"
                    )
                }
            }
        }

        // 3. Minimalist Queue List Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Antrean Gambar (${uiState.batchItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (uiState.batchItems.isNotEmpty()) {
                    Text(
                        text = "${uiState.exportableCount} masuk ZIP • ${uiState.customOverrideCount} kustom",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 4. Clean Minimalist Batch Item Rows with ZIP Selection & Status Tracking
        items(uiState.batchItems, key = { it.id }) { item ->
            MinimalBatchItemRow(
                item = item,
                globalConfig = uiState.globalConfig,
                isSelected = item.id == uiState.selectedItem?.id,
                onOpenStudio = { onOpenItemInStudio(item.id) },
                onToggleExportIncluded = { onToggleItemExportSelection(item.id) },
                onReprocessItem = { onReprocessSingleItem(item.id) },
                onToggleGlobal = { useGlobal -> onToggleItemUseGlobal(item.id, useGlobal) },
                onDelete = { onRemoveItem(item.id) }
            )
        }
    }

    if (showZipExportSheet) {
        BatchZipExportBottomSheet(
            uiState = uiState,
            onDismiss = { showZipExportSheet = false },
            onUpdateZipOptions = onUpdateZipOptions,
            onToggleItemSelection = onToggleItemExportSelection,
            onSelectAllForExport = onSelectAllForExport,
            onSaveZipArchive = onExportBatchZip,
            onShareZipArchive = onShareBatchZip
        )
    }
}

@Composable
private fun MinimalBatchItemRow(
    item: BatchImageItem,
    globalConfig: TraceConfig,
    isSelected: Boolean,
    onOpenStudio: () -> Unit,
    onToggleExportIncluded: () -> Unit,
    onReprocessItem: () -> Unit,
    onToggleGlobal: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val effectiveCfg = item.effectiveConfig(globalConfig)
    val rasterBitmap = remember(item.sourceBitmap) { item.sourceBitmap.asImageBitmap() }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 0.8.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable { onOpenStudio() }
            .testTag("batch_item_card_${item.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Export-to-ZIP Toggle Checkbox Pill
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (item.includedInExport) {
                            EmeraldNodeOptimized
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .border(
                        0.8.dp,
                        if (item.includedInExport) EmeraldNodeOptimized else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onToggleExportIncluded() }
                    .testTag("toggle_zip_include_${item.id}"),
                contentAlignment = Alignment.Center
            ) {
                if (item.includedInExport) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Termasuk dalam ZIP",
                        tint = Color(0xFF032217),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Side-by-side Raster & Vector Preview
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = rasterBitmap,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White)
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.status == BatchItemStatus.PROCESSING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 1.8.dp
                        )
                    } else if (item.result != null) {
                        MiniVectorPreviewCanvas(
                            traceResult = item.result,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Title, Mode & Node Stats
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    ItemStatusTag(status = item.status)
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = effectiveCfg.mode.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (item.useGlobalConfig) "Global" else "Kustom",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.useGlobalConfig) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            CyanVectorPrimary
                        },
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { onToggleGlobal(!item.useGlobalConfig) }
                            .testTag("toggle_override_${item.id}")
                    )
                }

                val res = item.result
                if (res != null) {
                    val kbText = String.format(Locale.US, "%.1f KB", res.estimatedSvgBytes / 1024f)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${res.totalNodes} nodes (-${res.reductionPercent}%) • ${res.visibleLayers.size} layer • $kbText",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldNodeOptimized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (item.errorMessage != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberTangentSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Re-trace individual item button
            IconButton(
                onClick = onReprocessItem,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("retrace_item_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Proses Ulang Item",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("delete_item_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Hapus",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Buka di Studio",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ItemStatusTag(status: BatchItemStatus) {
    val (label, color) = when (status) {
        BatchItemStatus.COMPLETED -> "SVG" to EmeraldNodeOptimized
        BatchItemStatus.PROCESSING -> "PROSES" to CyanVectorPrimary
        BatchItemStatus.PENDING -> "ANTRE" to Color(0xFF94A3B8)
        BatchItemStatus.FAILED -> "GAGAL" to AmberTangentSecondary
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}
