package com.example.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.SampleAssetGenerator
import com.example.engine.VectorTracingEngine
import com.example.model.BatchImageItem
import com.example.model.BatchItemStatus
import com.example.model.CanvasViewMode
import com.example.model.EditableNodeInfo
import com.example.model.NodeSubPoint
import com.example.model.SelectedPathNode
import com.example.model.TraceConfig
import com.example.model.TraceMode
import com.example.model.ZipArchiveOptions
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VectoForgeUiState(
    val batchItems: List<BatchImageItem> = emptyList(),
    val globalConfig: TraceConfig = TraceConfig.defaultForMode(TraceMode.GEOMETRIC_SHARP),
    val selectedItemId: String? = null,
    val activeTab: Int = 0,
    val canvasViewMode: CanvasViewMode = CanvasViewMode.SPLIT_COMPARE,
    val splitFraction: Float = 0.52f,
    val showWireframeOverlay: Boolean = true,
    val showBezierHandles: Boolean = true,
    val rasterBackdropOpacity: Float = 0.55f,
    val isNodeInspectorEnabled: Boolean = false,
    val selectedPathNode: SelectedPathNode? = null,
    val isBatchProcessing: Boolean = false,
    val batchProcessedCount: Int = 0,
    val batchTargetCount: Int = 0,
    val currentlyProcessingItemTitle: String? = null,
    val zipOptions: ZipArchiveOptions = ZipArchiveOptions(),
    val darkTheme: Boolean = true,
    val toastMessage: String? = null
) {
    val selectedItem: BatchImageItem?
        get() = batchItems.find { it.id == selectedItemId } ?: batchItems.firstOrNull()

    val completedCount: Int
        get() = batchItems.count { it.status == BatchItemStatus.COMPLETED && it.result != null }

    val pendingCount: Int
        get() = batchItems.count { it.status == BatchItemStatus.PENDING || it.status == BatchItemStatus.PROCESSING }

    val failedCount: Int
        get() = batchItems.count { it.status == BatchItemStatus.FAILED }

    val customOverrideCount: Int
        get() = batchItems.count { !it.useGlobalConfig }

    val exportableItems: List<BatchImageItem>
        get() = batchItems.filter {
            it.result != null && (!zipOptions.onlySelectedItems || it.includedInExport)
        }

    val exportableCount: Int
        get() = exportableItems.size

    val totalExportSvgBytes: Int
        get() = exportableItems.sumOf { it.result?.estimatedSvgBytes ?: 0 }

    val batchProgressFraction: Float
        get() = if (batchTargetCount <= 0) {
            if (batchItems.isEmpty()) 0f else completedCount.toFloat() / batchItems.size.toFloat()
        } else {
            (batchProcessedCount.toFloat() / batchTargetCount.toFloat()).coerceIn(0f, 1f)
        }

    val activeEditableNodeInfo: EditableNodeInfo?
        get() {
            val res = selectedItem?.result ?: return null
            val target = selectedPathNode ?: return null
            return res.listEditableNodes().find {
                it.layerId == target.layerId && it.commandIndex == target.commandIndex
            }
        }
}

class VectoForgeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(VectoForgeUiState())
    val uiState: StateFlow<VectoForgeUiState> = _uiState.asStateFlow()

    private var liveTraceJob: Job? = null
    private var batchJob: Job? = null

    init {
        loadSamplePack(autoTrace = true)
    }

    fun loadSamplePack(autoTrace: Boolean = true) {
        val samples = SampleAssetGenerator.createSampleBatchPack()
        _uiState.update { state ->
            state.copy(
                batchItems = samples,
                selectedItemId = samples.firstOrNull()?.id,
                selectedPathNode = null,
                toastMessage = "Paket sampel 5 mode siap diuji"
            )
        }
        if (autoTrace) {
            processBatch(onlyPendingOrGlobal = false)
        }
    }

    fun addGalleryBitmaps(
        namedBitmaps: List<Pair<String, Bitmap>>,
        modeOverride: TraceMode? = null
    ) {
        if (namedBitmaps.isEmpty()) return
        val currentGlobal = _uiState.value.globalConfig
        val useGlobal = modeOverride == null
        val itemConfig = if (modeOverride != null) {
            TraceConfig.defaultForMode(modeOverride)
        } else {
            currentGlobal
        }
        val newItems = namedBitmaps.mapIndexed { idx, (name, bmp) ->
            BatchImageItem(
                id = "user_img_${UUID.randomUUID().toString().take(8)}_$idx",
                title = name.substringBeforeLast('.').ifBlank { "Gambar #${idx + 1}" },
                subtitle = if (useGlobal) {
                    "Resolusi ${bmp.width}×${bmp.height}px • Mengikuti Global"
                } else {
                    "Resolusi ${bmp.width}×${bmp.height}px • Mode ${modeOverride?.title}"
                },
                sourceBitmap = bmp,
                useGlobalConfig = useGlobal,
                customConfig = itemConfig,
                status = BatchItemStatus.PENDING,
                recommendedMode = modeOverride ?: TraceMode.GEOMETRIC_SHARP,
                includedInExport = true
            )
        }
        _uiState.update { state ->
            val updated = newItems + state.batchItems
            state.copy(
                batchItems = updated,
                selectedItemId = newItems.first().id,
                selectedPathNode = null,
                toastMessage = "${newItems.size} gambar ditambahkan ke antrean Batch"
            )
        }
        processBatch(onlyPendingOrGlobal = false)
    }

    fun setActiveTab(tabIndex: Int) {
        _uiState.update { it.copy(activeTab = tabIndex.coerceIn(0, 2)) }
    }

    fun openItemInStudio(itemId: String) {
        _uiState.update {
            it.copy(
                selectedItemId = itemId,
                selectedPathNode = null,
                activeTab = 1
            )
        }
    }

    fun selectItem(itemId: String) {
        _uiState.update { state ->
            val nextItem = state.batchItems.find { it.id == itemId }
            val defaultNode = if (state.isNodeInspectorEnabled) {
                nextItem?.result?.listEditableNodes()?.firstOrNull()?.let {
                    SelectedPathNode(it.layerId, it.commandIndex, NodeSubPoint.ANCHOR)
                }
            } else {
                null
            }
            state.copy(
                selectedItemId = itemId,
                selectedPathNode = defaultNode
            )
        }
    }

    fun setCanvasViewMode(mode: CanvasViewMode) {
        _uiState.update {
            it.copy(
                canvasViewMode = mode,
                showWireframeOverlay = if (mode == CanvasViewMode.WIREFRAME_NODES) true else it.showWireframeOverlay
            )
        }
    }

    fun setSplitFraction(fraction: Float) {
        _uiState.update { it.copy(splitFraction = fraction.coerceIn(0.05f, 0.95f)) }
    }

    fun toggleWireframeOverlay() {
        _uiState.update { it.copy(showWireframeOverlay = !it.showWireframeOverlay) }
    }

    fun setRasterBackdropOpacity(opacity: Float) {
        _uiState.update { it.copy(rasterBackdropOpacity = opacity.coerceIn(0.0f, 1.0f)) }
    }

    fun toggleBezierHandles() {
        _uiState.update { it.copy(showBezierHandles = !it.showBezierHandles) }
    }

    fun toggleNodeInspector() {
        _uiState.update { state ->
            val enabling = !state.isNodeInspectorEnabled
            val firstNode = if (enabling) {
                state.selectedPathNode ?: state.selectedItem?.result?.listEditableNodes()?.firstOrNull()?.let {
                    SelectedPathNode(it.layerId, it.commandIndex, NodeSubPoint.ANCHOR)
                }
            } else {
                null
            }
            state.copy(
                isNodeInspectorEnabled = enabling,
                showWireframeOverlay = if (enabling) true else state.showWireframeOverlay,
                showBezierHandles = if (enabling) true else state.showBezierHandles,
                selectedPathNode = firstNode
            )
        }
    }

    fun selectPathNode(node: SelectedPathNode?) {
        _uiState.update { state ->
            state.copy(
                isNodeInspectorEnabled = if (node != null) true else state.isNodeInspectorEnabled,
                showWireframeOverlay = if (node != null) true else state.showWireframeOverlay,
                selectedPathNode = node
            )
        }
    }

    fun selectNodeSubPoint(subPoint: NodeSubPoint) {
        _uiState.update { state ->
            val current = state.selectedPathNode ?: return@update state
            state.copy(selectedPathNode = current.copy(subPoint = subPoint))
        }
    }

    fun stepSelectedNode(delta: Int) {
        _uiState.update { state ->
            val nodes = state.selectedItem?.result?.listEditableNodes().orEmpty()
            if (nodes.isEmpty()) return@update state
            val current = state.selectedPathNode
            val currentIdx = if (current == null) {
                0
            } else {
                nodes.indexOfFirst {
                    it.layerId == current.layerId && it.commandIndex == current.commandIndex
                }.takeIf { it >= 0 } ?: 0
            }
            val nextIdx = (currentIdx + delta).mod(nodes.size)
            val nextNode = nodes[nextIdx]
            state.copy(
                isNodeInspectorEnabled = true,
                showWireframeOverlay = true,
                selectedPathNode = SelectedPathNode(
                    layerId = nextNode.layerId,
                    commandIndex = nextNode.commandIndex,
                    subPoint = NodeSubPoint.ANCHOR
                )
            )
        }
    }

    fun shiftSelectedNode(dx: Float, dy: Float) {
        _uiState.update { state ->
            val active = state.selectedItem ?: return@update state
            val traceResult = active.result ?: return@update state
            val targetNode = state.selectedPathNode ?: return@update state

            val updatedResult = traceResult.shiftNode(targetNode, dx, dy)
            state.copy(
                batchItems = state.batchItems.map { item ->
                    if (item.id == active.id) item.copy(result = updatedResult) else item
                }
            )
        }
    }

    fun deleteSelectedNode() {
        _uiState.update { state ->
            val active = state.selectedItem ?: return@update state
            val traceResult = active.result ?: return@update state
            val targetNode = state.selectedPathNode ?: return@update state

            val nodesBefore = traceResult.listEditableNodes()
            val oldIndex = nodesBefore.indexOfFirst {
                it.layerId == targetNode.layerId && it.commandIndex == targetNode.commandIndex
            }
            val nodeInfo = nodesBefore.getOrNull(oldIndex)
            if (nodeInfo != null && !nodeInfo.canDelete) {
                return@update state.copy(
                    toastMessage = "Path minimal membutuhkan 2 titik simpul agar tetap valid"
                )
            }

            val updatedResult = traceResult.deleteNode(targetNode)
            val nodesAfter = updatedResult.listEditableNodes()
            val nextSelected = if (nodesAfter.isEmpty()) {
                null
            } else {
                val clampedIdx = oldIndex.coerceIn(0, nodesAfter.lastIndex)
                val replacement = nodesAfter[clampedIdx]
                SelectedPathNode(replacement.layerId, replacement.commandIndex, NodeSubPoint.ANCHOR)
            }

            state.copy(
                selectedPathNode = nextSelected,
                batchItems = state.batchItems.map { item ->
                    if (item.id == active.id) item.copy(result = updatedResult) else item
                },
                toastMessage = "Titik simpul Bezier dihapus dari path SVG"
            )
        }
    }

    fun toggleSelectedNodeCurveType() {
        _uiState.update { state ->
            val active = state.selectedItem ?: return@update state
            val traceResult = active.result ?: return@update state
            val targetNode = state.selectedPathNode ?: return@update state

            val updatedResult = traceResult.toggleNodeCurveType(targetNode)
            state.copy(
                selectedPathNode = targetNode.copy(subPoint = NodeSubPoint.ANCHOR),
                batchItems = state.batchItems.map { item ->
                    if (item.id == active.id) item.copy(result = updatedResult) else item
                },
                toastMessage = "Tipe segmen titik diubah (Sudut Lurus ↔ Kurva Bezier)"
            )
        }
    }

    fun toggleDarkTheme() {
        _uiState.update { it.copy(darkTheme = !it.darkTheme) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    fun updateZipOptions(transform: (ZipArchiveOptions) -> ZipArchiveOptions) {
        _uiState.update { state ->
            state.copy(zipOptions = transform(state.zipOptions))
        }
    }

    fun toggleItemExportSelection(itemId: String) {
        _uiState.update { state ->
            state.copy(
                batchItems = state.batchItems.map { item ->
                    if (item.id == itemId) {
                        item.copy(includedInExport = !item.includedInExport)
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun selectAllForExport(selected: Boolean) {
        _uiState.update { state ->
            state.copy(
                batchItems = state.batchItems.map { it.copy(includedInExport = selected) },
                toastMessage = if (selected) {
                    "Semua item dipilih untuk arsip .ZIP"
                } else {
                    "Pilihan ekspor .ZIP dikosongkan"
                }
            )
        }
    }

    fun applyGlobalToAllItems() {
        val globalCfg = _uiState.value.globalConfig
        _uiState.update { state ->
            state.copy(
                batchItems = state.batchItems.map { item ->
                    item.copy(
                        useGlobalConfig = true,
                        customConfig = globalCfg,
                        status = BatchItemStatus.PENDING
                    )
                },
                toastMessage = "Seluruh antrean disamakan ke Preset Global (${globalCfg.mode.title})"
            )
        }
        processBatch(onlyPendingOrGlobal = false)
    }

    fun cancelBatchProcessing() {
        batchJob?.cancel()
        batchJob = null
        _uiState.update { state ->
            state.copy(
                isBatchProcessing = false,
                currentlyProcessingItemTitle = null,
                batchItems = state.batchItems.map { item ->
                    if (item.status == BatchItemStatus.PROCESSING) {
                        item.copy(status = if (item.result != null) BatchItemStatus.COMPLETED else BatchItemStatus.PENDING)
                    } else {
                        item
                    }
                },
                toastMessage = "Pemrosesan antrean batch dihentikan"
            )
        }
    }

    fun selectGlobalMode(mode: TraceMode) {
        val newGlobal = TraceConfig.defaultForMode(mode)
        _uiState.update { state ->
            val updatedItems = state.batchItems.map { item ->
                if (item.useGlobalConfig) {
                    item.copy(status = BatchItemStatus.PENDING)
                } else {
                    item
                }
            }
            state.copy(
                globalConfig = newGlobal,
                batchItems = updatedItems,
                toastMessage = "Preset Global diubah ke ${mode.title}"
            )
        }
        processBatch(onlyPendingOrGlobal = true)
    }

    fun updateGlobalConfig(transform: (TraceConfig) -> TraceConfig) {
        val updatedGlobal = transform(_uiState.value.globalConfig)
        _uiState.update { state ->
            val updatedItems = state.batchItems.map { item ->
                if (item.useGlobalConfig) item.copy(status = BatchItemStatus.PENDING) else item
            }
            state.copy(
                globalConfig = updatedGlobal,
                batchItems = updatedItems
            )
        }
        liveTraceJob?.cancel()
        liveTraceJob = viewModelScope.launch {
            delay(160)
            processBatch(onlyPendingOrGlobal = true)
        }
    }

    fun processBatch(onlyPendingOrGlobal: Boolean = false) {
        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            val snapshot = _uiState.value
            val targets = snapshot.batchItems.filter { item ->
                !onlyPendingOrGlobal ||
                    item.status == BatchItemStatus.PENDING ||
                    item.status == BatchItemStatus.FAILED ||
                    item.useGlobalConfig
            }
            if (targets.isEmpty()) return@launch

            _uiState.update {
                it.copy(
                    isBatchProcessing = true,
                    batchProcessedCount = 0,
                    batchTargetCount = targets.size,
                    currentlyProcessingItemTitle = targets.firstOrNull()?.title
                )
            }

            var doneCount = 0
            for (target in targets) {
                _uiState.update { st ->
                    st.copy(
                        currentlyProcessingItemTitle = target.title,
                        batchItems = st.batchItems.map {
                            if (it.id == target.id) {
                                it.copy(status = BatchItemStatus.PROCESSING, errorMessage = null)
                            } else {
                                it
                            }
                        }
                    )
                }

                val latestItem = _uiState.value.batchItems.find { it.id == target.id } ?: continue
                val cfg = latestItem.effectiveConfig(_uiState.value.globalConfig)

                try {
                    val traceResult = withContext(Dispatchers.Default) {
                        VectorTracingEngine.traceBitmap(latestItem.sourceBitmap, cfg)
                    }

                    doneCount++
                    _uiState.update { st ->
                        st.copy(
                            batchProcessedCount = doneCount,
                            batchItems = st.batchItems.map {
                                if (it.id == target.id) {
                                    it.copy(
                                        status = BatchItemStatus.COMPLETED,
                                        result = traceResult,
                                        errorMessage = null
                                    )
                                } else {
                                    it
                                }
                            }
                        )
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    doneCount++
                    _uiState.update { st ->
                        st.copy(
                            batchProcessedCount = doneCount,
                            batchItems = st.batchItems.map {
                                if (it.id == target.id) {
                                    it.copy(
                                        status = BatchItemStatus.FAILED,
                                        errorMessage = e.localizedMessage ?: "Gagal memproses vektor"
                                    )
                                } else {
                                    it
                                }
                            }
                        )
                    }
                }
            }

            _uiState.update { state ->
                val defaultSelectedNode = if (state.isNodeInspectorEnabled && state.selectedPathNode == null) {
                    state.selectedItem?.result?.listEditableNodes()?.firstOrNull()?.let {
                        SelectedPathNode(it.layerId, it.commandIndex, NodeSubPoint.ANCHOR)
                    }
                } else {
                    state.selectedPathNode
                }
                state.copy(
                    isBatchProcessing = false,
                    currentlyProcessingItemTitle = null,
                    selectedPathNode = defaultSelectedNode,
                    toastMessage = "Batch selesai: ${targets.size} gambar berhasil dikonversi ke SVG"
                )
            }
        }
    }

    fun reprocessSingleItem(itemId: String) {
        retraceSingleItemDebounced(itemId, immediate = true)
    }

    fun toggleActiveItemUseGlobal(useGlobal: Boolean) {
        val active = _uiState.value.selectedItem ?: return
        val globalCfg = _uiState.value.globalConfig
        val updatedCustom = if (useGlobal) globalCfg else active.effectiveConfig(globalCfg)

        _uiState.update { st ->
            st.copy(
                batchItems = st.batchItems.map { item ->
                    if (item.id == active.id) {
                        item.copy(
                            useGlobalConfig = useGlobal,
                            customConfig = updatedCustom
                        )
                    } else {
                        item
                    }
                },
                toastMessage = if (useGlobal) {
                    "${active.title} dikembalikan ke Preset Global"
                } else {
                    "${active.title} menggunakan Override Kustom"
                }
            )
        }
        retraceSingleItemDebounced(active.id, immediate = true)
    }

    fun selectActiveItemMode(mode: TraceMode) {
        val active = _uiState.value.selectedItem ?: return
        val newConfig = TraceConfig.defaultForMode(mode)
        _uiState.update { st ->
            st.copy(
                batchItems = st.batchItems.map { item ->
                    if (item.id == active.id) {
                        item.copy(
                            useGlobalConfig = false,
                            customConfig = newConfig
                        )
                    } else {
                        item
                    }
                }
            )
        }
        retraceSingleItemDebounced(active.id, immediate = true)
    }

    fun updateActiveItemConfig(transform: (TraceConfig) -> TraceConfig) {
        val active = _uiState.value.selectedItem ?: return
        val baseCfg = active.effectiveConfig(_uiState.value.globalConfig)
        val newCfg = transform(baseCfg)

        _uiState.update { st ->
            st.copy(
                batchItems = st.batchItems.map { item ->
                    if (item.id == active.id) {
                        item.copy(
                            useGlobalConfig = false,
                            customConfig = newCfg
                        )
                    } else {
                        item
                    }
                }
            )
        }
        retraceSingleItemDebounced(active.id, immediate = false)
    }

    private fun retraceSingleItemDebounced(itemId: String, immediate: Boolean) {
        liveTraceJob?.cancel()
        liveTraceJob = viewModelScope.launch {
            if (!immediate) {
                delay(110)
            }
            val currentItem = _uiState.value.batchItems.find { it.id == itemId } ?: return@launch
            val cfg = currentItem.effectiveConfig(_uiState.value.globalConfig)

            _uiState.update { st ->
                st.copy(
                    batchItems = st.batchItems.map {
                        if (it.id == itemId) it.copy(status = BatchItemStatus.PROCESSING, errorMessage = null) else it
                    }
                )
            }

            try {
                val result = withContext(Dispatchers.Default) {
                    VectorTracingEngine.traceBitmap(currentItem.sourceBitmap, cfg)
                }

                _uiState.update { st ->
                    val nextNode = if (st.isNodeInspectorEnabled && st.selectedItem?.id == itemId) {
                        result.listEditableNodes().firstOrNull()?.let {
                            SelectedPathNode(it.layerId, it.commandIndex, NodeSubPoint.ANCHOR)
                        }
                    } else {
                        st.selectedPathNode
                    }
                    st.copy(
                        selectedPathNode = nextNode,
                        batchItems = st.batchItems.map {
                            if (it.id == itemId) {
                                it.copy(
                                    status = BatchItemStatus.COMPLETED,
                                    result = result,
                                    errorMessage = null
                                )
                            } else {
                                it
                            }
                        }
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _uiState.update { st ->
                    st.copy(
                        batchItems = st.batchItems.map {
                            if (it.id == itemId) {
                                it.copy(
                                    status = BatchItemStatus.FAILED,
                                    errorMessage = e.localizedMessage
                                )
                            } else {
                                it
                            }
                        }
                    )
                }
            }
        }
    }

    fun toggleLayerVisibility(itemId: String, layerId: String) {
        _uiState.update { st ->
            st.copy(
                batchItems = st.batchItems.map { item ->
                    if (item.id == itemId && item.result != null) {
                        val updatedLayers = item.result.layers.map { layer ->
                            if (layer.id == layerId) layer.copy(isVisible = !layer.isVisible) else layer
                        }
                        item.copy(result = item.result.copy(layers = updatedLayers))
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun deleteLayer(itemId: String, layerId: String) {
        _uiState.update { st ->
            st.copy(
                batchItems = st.batchItems.map { item ->
                    if (item.id == itemId && item.result != null) {
                        val updatedLayers = item.result.layers.filterNot { it.id == layerId }
                        item.copy(result = item.result.copy(layers = updatedLayers))
                    } else {
                        item
                    }
                },
                toastMessage = "Layer/path dihapus dari output SVG"
            )
        }
    }

    fun removeBatchItem(itemId: String) {
        _uiState.update { st ->
            val remaining = st.batchItems.filterNot { it.id == itemId }
            val nextSelected = if (st.selectedItemId == itemId) {
                remaining.firstOrNull()?.id
            } else {
                st.selectedItemId
            }
            st.copy(
                batchItems = remaining,
                selectedItemId = nextSelected,
                selectedPathNode = null,
                toastMessage = "Item dihapus dari antrean"
            )
        }
    }
}
