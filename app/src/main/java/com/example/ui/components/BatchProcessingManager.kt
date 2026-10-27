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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SyncLock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.ExportHelper
import com.example.engine.ZipPlannedEntry
import com.example.model.BatchImageItem
import com.example.model.BatchItemStatus
import com.example.model.TraceConfig
import com.example.model.ZipArchiveOptions
import com.example.ui.VectoForgeUiState
import com.example.ui.theme.AmberTangentSecondary
import com.example.ui.theme.CyanVectorPrimary
import com.example.ui.theme.EmeraldNodeOptimized
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Minimalist Batch Processing Status & Management Dashboard Card.
 * Tracks queued images, live processing progress, per-status counts, and provides
 * direct 'Export as ZIP' packaging controls.
 */
@Composable
fun BatchProcessingControlDashboard(
    uiState: VectoForgeUiState,
    onPickGalleryImages: () -> Unit,
    onRunBatchAll: () -> Unit,
    onCancelBatch: () -> Unit,
    onApplyGlobalToAll: () -> Unit,
    onSelectAllForExport: (Boolean) -> Unit,
    onOpenZipPackageSheet: () -> Unit,
    onQuickExportZip: () -> Unit,
    onReloadSamples: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCount = uiState.batchItems.size
    val completedCount = uiState.completedCount
    val exportableCount = uiState.exportableCount
    val totalKb = remember(uiState.totalExportSvgBytes) {
        String.format(Locale.US, "%.1f KB", uiState.totalExportSvgBytes / 1024f)
    }
    val allSelectedForExport = remember(uiState.batchItems) {
        uiState.batchItems.isNotEmpty() && uiState.batchItems.all { it.includedInExport }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .testTag("batch_processing_dashboard")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Header Row: Title + Live Status Pill + Reload Samples
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Manajemen Antrean Batch",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StatusPillBadge(
                            isProcessing = uiState.isBatchProcessing,
                            completedCount = completedCount,
                            totalCount = totalCount
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (uiState.isBatchProcessing) {
                            "Memproses: ${uiState.currentlyProcessingItemTitle ?: "..."} (${uiState.batchProcessedCount}/${uiState.batchTargetCount})"
                        } else {
                            "$exportableCount file SVG terpilih siap dikemas ke arsip .ZIP ($totalKb)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.isBatchProcessing) {
                            CyanVectorPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onReloadSamples,
                    modifier = Modifier
                        .size(36.dp)
                        .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .testTag("reload_samples_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Muat Ulang Sampel",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Live Progress Bar during batch execution
            AnimatedVisibility(visible = uiState.isBatchProcessing) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { uiState.batchProgressFraction },
                        color = CyanVectorPrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .testTag("batch_live_progress_bar")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Konversi paralel ke vektor SVG...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(uiState.batchProgressFraction * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyanVectorPrimary
                        )
                    }
                }
            }

            // Telemetry Metrics Strip (Total | Selesai | Siap ZIP | Ukuran SVG)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BatchStatTile(
                    label = "Antrean",
                    value = "$totalCount",
                    accentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                BatchStatTile(
                    label = "Selesai",
                    value = "$completedCount/$totalCount",
                    accentColor = EmeraldNodeOptimized,
                    modifier = Modifier.weight(1f)
                )
                BatchStatTile(
                    label = "Siap ZIP",
                    value = "$exportableCount SVG",
                    accentColor = CyanVectorPrimary,
                    modifier = Modifier.weight(1f)
                )
                BatchStatTile(
                    label = "Ukuran",
                    value = totalKb,
                    accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            // Primary Action Row: Tambah Gambar | Proses / Hentikan | Export as ZIP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPickGalleryImages,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("pick_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tambah Gambar",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah", style = MaterialTheme.typography.labelLarge)
                }

                if (uiState.isBatchProcessing) {
                    OutlinedButton(
                        onClick = onCancelBatch,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("cancel_batch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Hentikan Batch",
                            tint = AmberTangentSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hentikan", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    OutlinedButton(
                        onClick = onRunBatchAll,
                        enabled = totalCount > 0,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("reprocess_batch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Proses Semua",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Proses", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Button(
                    onClick = onOpenZipPackageSheet,
                    enabled = exportableCount > 0 && !uiState.isBatchProcessing,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldNodeOptimized,
                        contentColor = Color(0xFF032217)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("export_batch_zip_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = "Export as ZIP",
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Export as ZIP ($exportableCount)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Secondary Queue Utilities Bar: Select All for ZIP | Reset Overrides to Global | Quick Save ZIP
            if (totalCount > 0) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelectAllForExport(!allSelectedForExport) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .testTag("toggle_select_all_zip")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = if (allSelectedForExport) CyanVectorPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (allSelectedForExport) "Semua Masuk ZIP" else "Pilih Semua ke ZIP",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (allSelectedForExport) CyanVectorPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (uiState.customOverrideCount > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onApplyGlobalToAll() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("sync_all_global_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SyncLock,
                                    contentDescription = null,
                                    tint = CyanVectorPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Samakan Global (${uiState.customOverrideCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanVectorPrimary
                                )
                            }
                        }

                        if (exportableCount > 0) {
                            Text(
                                text = "Simpan Cepat .ZIP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldNodeOptimized,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onQuickExportZip() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("quick_save_zip_link")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPillBadge(
    isProcessing: Boolean,
    completedCount: Int,
    totalCount: Int
) {
    val bgColor = when {
        isProcessing -> CyanVectorPrimary.copy(alpha = 0.14f)
        completedCount == totalCount && totalCount > 0 -> EmeraldNodeOptimized.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        isProcessing -> CyanVectorPrimary
        completedCount == totalCount && totalCount > 0 -> EmeraldNodeOptimized
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = when {
        isProcessing -> "MEMPROSES"
        completedCount == totalCount && totalCount > 0 -> "SIAP ZIP"
        else -> "$completedCount/$totalCount SELESAI"
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(9.dp),
                    strokeWidth = 1.5.dp,
                    color = CyanVectorPrimary
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(textColor)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
private fun BatchStatTile(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(9.dp),
        modifier = modifier.border(
            0.6.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
            RoundedCornerShape(9.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Interactive Modal Bottom Sheet for inspecting & configuring the Batch ZIP Export package.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchZipExportBottomSheet(
    uiState: VectoForgeUiState,
    onDismiss: () -> Unit,
    onUpdateZipOptions: ((ZipArchiveOptions) -> ZipArchiveOptions) -> Unit,
    onToggleItemSelection: (String) -> Unit,
    onSelectAllForExport: (Boolean) -> Unit,
    onSaveZipArchive: () -> Unit,
    onShareZipArchive: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BatchZipArchiveBuilderContent(
                items = uiState.batchItems,
                globalConfig = uiState.globalConfig,
                zipOptions = uiState.zipOptions,
                onUpdateZipOptions = onUpdateZipOptions,
                onToggleItemSelection = onToggleItemSelection,
                onSelectAllForExport = onSelectAllForExport,
                onSaveZipArchive = {
                    onSaveZipArchive()
                    onDismiss()
                },
                onShareZipArchive = {
                    onShareZipArchive()
                    onDismiss()
                }
            )
        }
    }
}

/**
 * Reusable Batch ZIP Archive Builder & Manifest Preview Content.
 * Used inside both the BatchZipExportBottomSheet and SvgExportScreen.
 */
@Composable
fun BatchZipArchiveBuilderContent(
    items: List<BatchImageItem>,
    globalConfig: TraceConfig,
    zipOptions: ZipArchiveOptions,
    onUpdateZipOptions: ((ZipArchiveOptions) -> ZipArchiveOptions) -> Unit,
    onToggleItemSelection: (String) -> Unit,
    onSelectAllForExport: (Boolean) -> Unit,
    onSaveZipArchive: () -> Unit,
    onShareZipArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plannedEntries = remember(items, globalConfig, zipOptions) {
        ExportHelper.buildPlannedZipEntries(items, globalConfig, zipOptions)
    }
    val totalSvgBytes = remember(plannedEntries) {
        plannedEntries.sumOf { it.svgSizeBytes }
    }
    val totalKbFormatted = remember(totalSvgBytes) {
        String.format(Locale.US, "%.1f KB", totalSvgBytes / 1024f)
    }
    val completedItems = remember(items) {
        items.filter { it.result != null }
    }
    val allSelected = remember(completedItems) {
        completedItems.isNotEmpty() && completedItems.all { it.includedInExport }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("batch_zip_builder_content"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PAKET EKSPOR BATCH (.ZIP)",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldNodeOptimized
                )
                Text(
                    text = "${plannedEntries.size} File SVG Siap Dikemas • $totalKbFormatted",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                color = EmeraldNodeOptimized.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = ".ZIP ARCHIVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldNodeOptimized,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Archive File Name Field
        OutlinedTextField(
            value = zipOptions.archiveBaseName,
            onValueChange = { newName ->
                onUpdateZipOptions { it.copy(archiveBaseName = newName) }
            },
            label = { Text("Nama File Arsip (.zip)") },
            singleLine = true,
            suffix = { Text(".zip", style = MaterialTheme.typography.labelMedium) },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("zip_archive_name_input")
        )

        // Packaging Structure Toggles (Subfolder per Mode + Laporan Optimasi)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.6.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            tint = CyanVectorPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "Pisahkan Subfolder per Mode Tracing",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Contoh: geometric_sharp/ikon.svg, potrace_bw/logo.svg",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = zipOptions.organizeByModeSubfolders,
                        onCheckedChange = { enabled ->
                            onUpdateZipOptions { it.copy(organizeByModeSubfolders = enabled) }
                        },
                        modifier = Modifier.testTag("zip_subfolder_switch")
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = EmeraldNodeOptimized,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "Sertakan Laporan & Manifest JSON",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tambahkan vectoforge_batch_report.txt & manifest.json",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = zipOptions.includeSummaryReport,
                        onCheckedChange = { enabled ->
                            onUpdateZipOptions { it.copy(includeSummaryReport = enabled) }
                        },
                        modifier = Modifier.testTag("zip_report_switch")
                    )
                }
            }
        }

        // File Inclusion Checklist Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Daftar File dalam Arsip (${plannedEntries.size}/${completedItems.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(
                onClick = { onSelectAllForExport(!allSelected) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.testTag("zip_sheet_select_all_button")
            ) {
                Text(
                    text = if (allSelected) "Lepas Semua" else "Pilih Semua",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanVectorPrimary
                )
            }
        }

        // Scrollable List of Queued Items with Checkbox & Planned ZIP Path
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .border(0.6.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp)),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(completedItems, key = { it.id }) { item ->
                val planned: ZipPlannedEntry? = plannedEntries.find { it.itemId == item.id }
                val res = item.result
                val sizeKb = String.format(Locale.US, "%.1f KB", (res?.estimatedSvgBytes ?: 0) / 1024f)
                val cfg = item.effectiveConfig(globalConfig)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleItemSelection(item.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("zip_entry_row_${item.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = item.includedInExport,
                        onCheckedChange = { onToggleItemSelection(item.id) },
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = planned?.zipPath ?: "${ExportHelper.sanitizeFileName(item.title)}.svg",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (item.includedInExport) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${cfg.mode.title} • ${res?.totalNodes ?: 0} nodes (-${res?.reductionPercent ?: 0}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = sizeKb,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.includedInExport) EmeraldNodeOptimized else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Action Buttons: Save ZIP & Share ZIP
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSaveZipArchive,
                enabled = plannedEntries.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldNodeOptimized,
                    contentColor = Color(0xFF002116)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("save_batch_zip_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FolderZip,
                    contentDescription = "Simpan ZIP",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Export as ZIP (${plannedEntries.size})",
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onShareZipArchive,
                enabled = plannedEntries.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("share_batch_zip_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Bagikan ZIP",
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Bagikan .ZIP")
            }
        }
    }
}
