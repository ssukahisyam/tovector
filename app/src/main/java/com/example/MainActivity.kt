package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.engine.ExportHelper
import com.example.ui.VectoForgeUiState
import com.example.ui.VectoForgeViewModel
import com.example.ui.screens.BatchQueueScreen
import com.example.ui.screens.SvgExportScreen
import com.example.ui.screens.VectorStudioScreen
import com.example.ui.theme.CyanVectorPrimary
import com.example.ui.theme.EmeraldNodeOptimized
import com.example.ui.theme.VectoForgeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: VectoForgeViewModel = viewModel()
            val uiState by vm.uiState.collectAsStateWithLifecycle()

            VectoForgeTheme(darkTheme = uiState.darkTheme) {
                VectoForgeApp(
                    uiState = uiState,
                    viewModel = vm
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VectoForgeApp(
    uiState: VectoForgeUiState,
    viewModel: VectoForgeViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAlgorithmGuideDialog by remember { mutableStateOf(false) }
    var showBatchGallerySheet by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    // 1. Single SVG CreateDocument Launcher
    val createSvgDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/svg+xml")
    ) { uri: Uri? ->
        val activeResult = uiState.selectedItem?.result
        if (uri != null && activeResult != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        ExportHelper.writeSingleSvgToStream(activeResult.buildSvgString(), out)
                    }
                    viewModel.showToast("File .SVG berhasil disimpan!")
                } catch (e: Exception) {
                    viewModel.showToast("Gagal menyimpan SVG: ${e.localizedMessage}")
                }
            }
        }
    }

    // 2. Batch ZIP CreateDocument Launcher
    val createZipDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        ExportHelper.writeBatchZipToStream(
                            items = uiState.batchItems,
                            globalConfig = uiState.globalConfig,
                            outputStream = out,
                            options = uiState.zipOptions
                        )
                    }
                    viewModel.showToast("Paket Batch .ZIP (${uiState.exportableCount} SVG) berhasil disimpan!")
                } catch (e: Exception) {
                    viewModel.showToast("Gagal menyimpan ZIP: ${e.localizedMessage}")
                }
            }
        }
    }

    val triggerPickImages = {
        showBatchGallerySheet = true
    }

    val triggerSaveSingleSvg = {
        val active = uiState.selectedItem
        if (active != null && active.result != null) {
            val fileName = "${ExportHelper.sanitizeFileName(active.title)}.svg"
            createSvgDocumentLauncher.launch(fileName)
        } else {
            viewModel.showToast("Belum ada vektor untuk disimpan")
        }
    }

    val triggerShareSingleSvg = {
        val active = uiState.selectedItem
        if (active != null && active.result != null) {
            try {
                ExportHelper.shareSingleSvg(
                    context = context,
                    title = active.title,
                    svgContent = active.result.buildSvgString()
                )
            } catch (e: Exception) {
                viewModel.showToast("Gagal membagikan SVG: ${e.localizedMessage}")
            }
        }
    }

    val triggerSaveBatchZip = {
        if (uiState.exportableCount > 0) {
            val baseName = ExportHelper.sanitizeFileName(uiState.zipOptions.archiveBaseName)
                .ifEmpty { "vectoforge_batch_${uiState.exportableCount}_vectors" }
            createZipDocumentLauncher.launch("$baseName.zip")
        } else {
            viewModel.showToast("Pilih minimal 1 gambar selesai untuk diekspor ke .ZIP")
        }
    }

    val triggerShareBatchZip = {
        if (uiState.exportableCount > 0) {
            try {
                ExportHelper.shareBatchZip(
                    context = context,
                    items = uiState.batchItems,
                    globalConfig = uiState.globalConfig,
                    options = uiState.zipOptions
                )
            } catch (e: Exception) {
                viewModel.showToast("Gagal membagikan ZIP: ${e.localizedMessage}")
            }
        } else {
            viewModel.showToast("Pilih minimal 1 gambar selesai untuk dibagikan sebagai .ZIP")
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 740.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Polyline,
                                contentDescription = "VectoForge Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "VectoForge",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    actions = {
                        Text(
                            text = "${uiState.completedCount}/${uiState.batchItems.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        IconButton(
                            onClick = { showAlgorithmGuideDialog = true },
                            modifier = Modifier.testTag("algorithm_guide_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = "Panduan Algoritma Tracing",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.toggleDarkTheme() },
                            modifier = Modifier.testTag("toggle_theme_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.darkTheme) {
                                    Icons.Default.LightMode
                                } else {
                                    Icons.Default.DarkMode
                                },
                                contentDescription = "Ubah Tema",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            selected = uiState.activeTab == 0,
                            onClick = { viewModel.setActiveTab(0) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesomeMotion,
                                    contentDescription = "Antrean Batch"
                                )
                            },
                            label = { Text("Antrean Batch") },
                            modifier = Modifier.testTag("nav_tab_batch")
                        )
                        NavigationBarItem(
                            selected = uiState.activeTab == 1,
                            onClick = { viewModel.setActiveTab(1) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Vector Studio"
                                )
                            },
                            label = { Text("Vector Studio") },
                            modifier = Modifier.testTag("nav_tab_studio")
                        )
                        NavigationBarItem(
                            selected = uiState.activeTab == 2,
                            onClick = { viewModel.setActiveTab(2) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "Kode & Ekspor"
                                )
                            },
                            label = { Text("Kode & ZIP") },
                            modifier = Modifier.testTag("nav_tab_export")
                        )
                    }
                }
            }
        ) { innerPadding ->
            if (isExpandedScreen) {
                // Canonical Tablet / Foldable Split Workbench
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    BatchQueueScreen(
                        uiState = uiState,
                        onPickGalleryImages = triggerPickImages,
                        onLoadSamplePack = { viewModel.loadSamplePack(autoTrace = true) },
                        onSelectGlobalMode = viewModel::selectGlobalMode,
                        onUpdateGlobalConfig = viewModel::updateGlobalConfig,
                        onProcessAllBatch = { viewModel.processBatch(onlyPendingOrGlobal = false) },
                        onCancelBatch = viewModel::cancelBatchProcessing,
                        onApplyGlobalToAll = viewModel::applyGlobalToAllItems,
                        onSelectAllForExport = viewModel::selectAllForExport,
                        onToggleItemExportSelection = viewModel::toggleItemExportSelection,
                        onReprocessSingleItem = viewModel::reprocessSingleItem,
                        onUpdateZipOptions = viewModel::updateZipOptions,
                        onExportBatchZip = triggerSaveBatchZip,
                        onShareBatchZip = triggerShareBatchZip,
                        onOpenItemInStudio = viewModel::selectItem,
                        onToggleItemUseGlobal = { id, useGlobal ->
                            viewModel.selectItem(id)
                            viewModel.toggleActiveItemUseGlobal(useGlobal)
                        },
                        onRemoveItem = viewModel::removeBatchItem,
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                    )

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outline)
                    )

                    VectorStudioScreen(
                        uiState = uiState,
                        enableBackToQueue = false,
                        onNavigateBackToQueue = {},
                        onSelectBatchItem = viewModel::selectItem,
                        onSetCanvasViewMode = viewModel::setCanvasViewMode,
                        onSetSplitFraction = viewModel::setSplitFraction,
                        onToggleWireframeOverlay = viewModel::toggleWireframeOverlay,
                        onToggleBezierHandles = viewModel::toggleBezierHandles,
                        onSetRasterBackdropOpacity = viewModel::setRasterBackdropOpacity,
                        onToggleNodeInspector = viewModel::toggleNodeInspector,
                        onSelectPathNode = viewModel::selectPathNode,
                        onSelectNodeSubPoint = viewModel::selectNodeSubPoint,
                        onStepSelectedNode = viewModel::stepSelectedNode,
                        onShiftSelectedNode = viewModel::shiftSelectedNode,
                        onDeleteSelectedNode = viewModel::deleteSelectedNode,
                        onToggleSelectedNodeCurveType = viewModel::toggleSelectedNodeCurveType,
                        onToggleUseGlobal = viewModel::toggleActiveItemUseGlobal,
                        onSelectModeForActive = viewModel::selectActiveItemMode,
                        onUpdateActiveConfig = viewModel::updateActiveItemConfig,
                        onToggleLayerVisibility = viewModel::toggleLayerVisibility,
                        onDeleteLayer = viewModel::deleteLayer,
                        onSaveSingleSvg = triggerSaveSingleSvg,
                        onShareSingleSvg = triggerShareSingleSvg,
                        onOpenSvgInspector = { viewModel.setActiveTab(2) },
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                    )
                }
            } else {
                when (uiState.activeTab) {
                    0 -> BatchQueueScreen(
                        uiState = uiState,
                        onPickGalleryImages = triggerPickImages,
                        onLoadSamplePack = { viewModel.loadSamplePack(autoTrace = true) },
                        onSelectGlobalMode = viewModel::selectGlobalMode,
                        onUpdateGlobalConfig = viewModel::updateGlobalConfig,
                        onProcessAllBatch = { viewModel.processBatch(onlyPendingOrGlobal = false) },
                        onCancelBatch = viewModel::cancelBatchProcessing,
                        onApplyGlobalToAll = viewModel::applyGlobalToAllItems,
                        onSelectAllForExport = viewModel::selectAllForExport,
                        onToggleItemExportSelection = viewModel::toggleItemExportSelection,
                        onReprocessSingleItem = viewModel::reprocessSingleItem,
                        onUpdateZipOptions = viewModel::updateZipOptions,
                        onExportBatchZip = triggerSaveBatchZip,
                        onShareBatchZip = triggerShareBatchZip,
                        onOpenItemInStudio = viewModel::openItemInStudio,
                        onToggleItemUseGlobal = { id, useGlobal ->
                            viewModel.selectItem(id)
                            viewModel.toggleActiveItemUseGlobal(useGlobal)
                        },
                        onRemoveItem = viewModel::removeBatchItem,
                        modifier = Modifier.padding(innerPadding)
                    )
                    1 -> VectorStudioScreen(
                        uiState = uiState,
                        enableBackToQueue = true,
                        onNavigateBackToQueue = { viewModel.setActiveTab(0) },
                        onSelectBatchItem = viewModel::selectItem,
                        onSetCanvasViewMode = viewModel::setCanvasViewMode,
                        onSetSplitFraction = viewModel::setSplitFraction,
                        onToggleWireframeOverlay = viewModel::toggleWireframeOverlay,
                        onToggleBezierHandles = viewModel::toggleBezierHandles,
                        onSetRasterBackdropOpacity = viewModel::setRasterBackdropOpacity,
                        onToggleNodeInspector = viewModel::toggleNodeInspector,
                        onSelectPathNode = viewModel::selectPathNode,
                        onSelectNodeSubPoint = viewModel::selectNodeSubPoint,
                        onStepSelectedNode = viewModel::stepSelectedNode,
                        onShiftSelectedNode = viewModel::shiftSelectedNode,
                        onDeleteSelectedNode = viewModel::deleteSelectedNode,
                        onToggleSelectedNodeCurveType = viewModel::toggleSelectedNodeCurveType,
                        onToggleUseGlobal = viewModel::toggleActiveItemUseGlobal,
                        onSelectModeForActive = viewModel::selectActiveItemMode,
                        onUpdateActiveConfig = viewModel::updateActiveItemConfig,
                        onToggleLayerVisibility = viewModel::toggleLayerVisibility,
                        onDeleteLayer = viewModel::deleteLayer,
                        onSaveSingleSvg = triggerSaveSingleSvg,
                        onShareSingleSvg = triggerShareSingleSvg,
                        onOpenSvgInspector = { viewModel.setActiveTab(2) },
                        modifier = Modifier.padding(innerPadding)
                    )
                    else -> SvgExportScreen(
                        uiState = uiState,
                        enableBackToQueue = true,
                        onNavigateBackToQueue = { viewModel.setActiveTab(0) },
                        onSaveSingleSvg = triggerSaveSingleSvg,
                        onShareSingleSvg = triggerShareSingleSvg,
                        onUpdateZipOptions = viewModel::updateZipOptions,
                        onToggleItemExportSelection = viewModel::toggleItemExportSelection,
                        onSelectAllForExport = viewModel::selectAllForExport,
                        onSaveBatchZip = triggerSaveBatchZip,
                        onShareBatchZip = triggerShareBatchZip,
                        onShowToast = viewModel::showToast,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    if (showBatchGallerySheet) {
        com.example.ui.components.MinimalBatchGallerySheet(
            globalMode = uiState.globalConfig.mode,
            onDismiss = { showBatchGallerySheet = false },
            onConfirmImport = { bitmaps, modeOverride ->
                viewModel.addGalleryBitmaps(bitmaps, modeOverride)
            }
        )
    }

    if (showAlgorithmGuideDialog) {
        AlertDialog(
            onDismissRequest = { showAlgorithmGuideDialog = false },
            title = {
                Text(
                    text = "Arsitektur Algoritma Tracing & Anti-Mbulet",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Mengapa gambar simpel sering menjadi vektor yang 'mbulet' (kusut)? Karena anti-aliasing tepi dan kompresi JPG dibaca sebagai lekukan kurva Bezier.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    GuideBullet(
                        title = "1. Geometric Anti-Mbulet",
                        desc = "Menggunakan 3x3 Median Edge Cleaning + Douglas-Peucker ε agresif + Orthogonal Line Snapping untuk mengunci garis lurus dan sudut siku."
                    )
                    GuideBullet(
                        title = "2. Potrace B/W Clean",
                        desc = "Mengikuti pipeline Peter Selinger: CCL Despeckle (turdsize), Optimal Polygon, AlphaMax Corner Detection, dan kurva Bezier halus."
                    )
                    GuideBullet(
                        title = "3. VTracer Multicolor",
                        desc = "Mengelompokkan layer warna dengan K-Means++, menghapus tangga piksel (staircase removal), dan meratakan kontur tiap layer."
                    )
                    GuideBullet(
                        title = "4. Pixel Art & 5. Centerline",
                        desc = "Pixel Art menjaga poligon siku murni, sedangkan Centerline memakai penipisan morfologi Zhang-Suen untuk menghasilkan stroke tunggal."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAlgorithmGuideDialog = false }) {
                    Text("Mengerti")
                }
            }
        )
    }
}

@Composable
private fun GuideBullet(title: String, desc: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = CyanVectorPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun decodeSampledBitmapFromUri(context: Context, uri: Uri): Pair<String, Bitmap>? {
    return try {
        var displayName = "Gambar_Galeri"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && cursor.moveToFirst()) {
                displayName = cursor.getString(nameIdx) ?: displayName
            }
        }

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        val maxDim = 640
        var sampleSize = 1
        while (boundsOptions.outWidth / sampleSize > maxDim ||
            boundsOptions.outHeight / sampleSize > maxDim
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        Pair(displayName, bmp)
    } catch (_: Exception) {
        null
    }
}
