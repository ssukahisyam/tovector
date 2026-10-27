package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ZipArchiveOptions
import com.example.ui.VectoForgeUiState
import com.example.ui.components.BatchZipArchiveBuilderContent
import com.example.ui.theme.CyanVectorPrimary
import java.util.Locale

@Composable
fun SvgExportScreen(
    uiState: VectoForgeUiState,
    enableBackToQueue: Boolean,
    onNavigateBackToQueue: () -> Unit,
    onSaveSingleSvg: () -> Unit,
    onShareSingleSvg: () -> Unit,
    onUpdateZipOptions: ((ZipArchiveOptions) -> ZipArchiveOptions) -> Unit,
    onToggleItemExportSelection: (String) -> Unit,
    onSelectAllForExport: (Boolean) -> Unit,
    onSaveBatchZip: () -> Unit,
    onShareBatchZip: () -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (enableBackToQueue) {
        BackHandler {
            onNavigateBackToQueue()
        }
    }

    val context = LocalContext.current
    val activeItem = uiState.selectedItem
    val svgCode = remember(activeItem?.result) {
        activeItem?.result?.buildSvgString() ?: "<!-- Belum ada hasil tracing -->"
    }
    val svgSizeKb = remember(svgCode) {
        String.format(Locale.US, "%.1f KB", svgCode.toByteArray().size / 1024f)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Interactive Batch ZIP Archive Builder & Manifest Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        ) {
            BatchZipArchiveBuilderContent(
                items = uiState.batchItems,
                globalConfig = uiState.globalConfig,
                zipOptions = uiState.zipOptions,
                onUpdateZipOptions = onUpdateZipOptions,
                onToggleItemSelection = onToggleItemExportSelection,
                onSelectAllForExport = onSelectAllForExport,
                onSaveZipArchive = onSaveBatchZip,
                onShareZipArchive = onShareBatchZip,
                modifier = Modifier.padding(14.dp)
            )
        }

        // 2. Single Active SVG File Export & Raw XML Inspector
        if (activeItem != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "EKSPOR & KODE SVG AKTIF",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanVectorPrimary
                            )
                            Text(
                                text = "${activeItem.title}.svg",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "$svgSizeKb • ${activeItem.result?.totalNodes ?: 0} Nodes",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanVectorPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSaveSingleSvg,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_save_svg_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Simpan SVG",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan .SVG")
                        }

                        FilledTonalButton(
                            onClick = {
                                copyToClipboard(context, svgCode)
                                onShowToast("Kode SVG disalin ke Clipboard")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("copy_svg_code_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Salin SVG",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salin Kode")
                        }

                        OutlinedButton(
                            onClick = onShareSingleSvg,
                            modifier = Modifier.testTag("export_share_svg_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Bagikan",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Raw SVG Code Viewer Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(Color(0xFF090D12), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = svgCode,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA5F3FC)
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("VectoForge SVG", text)
    clipboard?.setPrimaryClip(clip)
}
