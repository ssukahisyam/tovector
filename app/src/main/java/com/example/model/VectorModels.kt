package com.example.model

import android.graphics.Bitmap
import java.util.Locale

enum class TraceMode(
    val id: String,
    val title: String,
    val badge: String,
    val shortSummary: String,
    val technicalDetail: String
) {
    POTRACE_BW(
        id = "potrace_bw",
        title = "Potrace B/W Clean",
        badge = "POTRACE",
        shortSummary = "Siluet & logo hitam-putih halus dengan aproksimasi kurva Bezier.",
        technicalDetail = "Binarisasi + CCL Despeckle (turdsize) + Optimal Polygon + AlphaMax Corner + Cubic Bezier"
    ),
    GEOMETRIC_SHARP(
        id = "geometric_sharp",
        title = "Geometric Anti-Mbulet",
        badge = "ANTI-MBULET",
        shortSummary = "Khusus ikon/bentuk simpel agar garis lurus & sudut tidak kusut/melengkung liar.",
        technicalDetail = "Edge Pre-Smoothing + Douglas-Peucker Agresif + Orthogonal/Collinear Line Snap + Strict Corners"
    ),
    VTRACER_MULTICOLOR(
        id = "vtracer_multicolor",
        title = "VTracer Multicolor",
        badge = "MULTICOLOR",
        shortSummary = "Ilustrasi & poster berwarna dengan pengelompokan layer warna datar.",
        technicalDetail = "Hierarchical Color Quantization (K-Means) + Layer Decomposition + Staircase Removal + Bezier Fit"
    ),
    PIXEL_POLYGON(
        id = "pixel_polygon",
        title = "Pixel Art Polygon",
        badge = "PIXEL",
        shortSummary = "Pertahankan ketajaman blok piksel menjadi poligon siku tanpa pelengkungan.",
        technicalDetail = "Strict Grid Boundary Walk + Collinear Vertex Merging + Zero Bezier Rounding"
    ),
    CENTERLINE_SKETCH(
        id = "centerline_sketch",
        title = "Centerline Stroke",
        badge = "SKELETON",
        shortSummary = "Ekstrak garis tulang tengah untuk sketsa/tulisan (path stroke tunggal, bukan fill ganda).",
        technicalDetail = "Zhang-Suen Morphological Skeletonization + Junction Chain Walk + Single-Stroke Bezier"
    )
}

data class TraceConfig(
    val mode: TraceMode = TraceMode.GEOMETRIC_SHARP,
    val threshold: Int = 140,              // 10..245 Luminance threshold
    val colorCount: Int = 5,               // 2..12 for Multicolor & Pixel Art
    val despeckleSize: Int = 14,           // 0..100 px (turdsize filter to drop tiny islands)
    val simplifyTolerance: Float = 2.2f,   // 0.4f..8.0f Douglas-Peucker epsilon (higher = anti-mbulet)
    val cornerThresholdDeg: Float = 115f,  // 45f..165f angle below which vertex stays a sharp corner
    val smoothness: Float = 0.85f,         // 0.0f..1.4f Bezier handle tension
    val strokeWidth: Float = 2.5f,         // 1.0f..10.0f for Centerline mode
    val orthogonalSnap: Boolean = true,    // Snap near-horizontal/vertical lines to prevent wobble
    val removeBackground: Boolean = true,  // Auto-filter outermost background canvas box
    val invertBw: Boolean = false          // Invert foreground/background in B/W & Sketch
) {
    companion object {
        fun defaultForMode(mode: TraceMode): TraceConfig = when (mode) {
            TraceMode.POTRACE_BW -> TraceConfig(
                mode = mode,
                threshold = 138,
                despeckleSize = 12,
                simplifyTolerance = 1.4f,
                cornerThresholdDeg = 105f,
                smoothness = 1.0f,
                orthogonalSnap = false,
                removeBackground = true
            )
            TraceMode.GEOMETRIC_SHARP -> TraceConfig(
                mode = mode,
                threshold = 140,
                despeckleSize = 20,
                simplifyTolerance = 2.8f,
                cornerThresholdDeg = 132f,
                smoothness = 0.65f,
                orthogonalSnap = true,
                removeBackground = true
            )
            TraceMode.VTRACER_MULTICOLOR -> TraceConfig(
                mode = mode,
                colorCount = 6,
                despeckleSize = 16,
                simplifyTolerance = 1.8f,
                cornerThresholdDeg = 115f,
                smoothness = 0.85f,
                orthogonalSnap = false,
                removeBackground = false
            )
            TraceMode.PIXEL_POLYGON -> TraceConfig(
                mode = mode,
                colorCount = 6,
                despeckleSize = 2,
                simplifyTolerance = 0.5f,
                cornerThresholdDeg = 175f,
                smoothness = 0.0f,
                orthogonalSnap = true,
                removeBackground = true
            )
            TraceMode.CENTERLINE_SKETCH -> TraceConfig(
                mode = mode,
                threshold = 145,
                despeckleSize = 10,
                simplifyTolerance = 2.0f,
                cornerThresholdDeg = 110f,
                smoothness = 0.8f,
                strokeWidth = 2.8f,
                orthogonalSnap = false,
                removeBackground = true
            )
        }
    }
}

data class VecPoint(
    val x: Float,
    val y: Float
)

sealed class PathCommand {
    data class MoveTo(val p: VecPoint) : PathCommand()
    data class LineTo(val p: VecPoint) : PathCommand()
    data class CubicTo(
        val c1: VecPoint,
        val c2: VecPoint,
        val p: VecPoint
    ) : PathCommand()
    data object Close : PathCommand()
}

enum class NodeSubPoint(val label: String) {
    ANCHOR("Titik Simpul"),
    CONTROL_1("Handle Masuk (C1)"),
    CONTROL_2("Handle Keluar (C2)")
}

data class SelectedPathNode(
    val layerId: String,
    val commandIndex: Int,
    val subPoint: NodeSubPoint = NodeSubPoint.ANCHOR
)

data class EditableNodeInfo(
    val layerId: String,
    val layerName: String,
    val commandIndex: Int,
    val globalNodeOrdinal: Int,
    val totalEditableNodes: Int,
    val commandTypeLabel: String,
    val anchor: VecPoint,
    val control1: VecPoint?,
    val control2: VecPoint?,
    val canConvertCurveLine: Boolean,
    val canDelete: Boolean
)

data class VectorPathLayer(
    val id: String,
    val name: String,
    val commands: List<PathCommand>,
    val fillColorHex: String?,
    val strokeColorHex: String?,
    val strokeWidth: Float = 1f,
    val nodeCount: Int,
    val curveCount: Int,
    val lineCount: Int,
    val rawPointCount: Int,
    val areaPx: Int,
    val isBackgroundCandidate: Boolean = false,
    val isVisible: Boolean = true
) {
    fun toSvgPathString(): String {
        val sb = StringBuilder()
        for (cmd in commands) {
            when (cmd) {
                is PathCommand.MoveTo -> sb.append(
                    String.format(Locale.US, "M%.1f %.1f ", cmd.p.x, cmd.p.y)
                )
                is PathCommand.LineTo -> sb.append(
                    String.format(Locale.US, "L%.1f %.1f ", cmd.p.x, cmd.p.y)
                )
                is PathCommand.CubicTo -> sb.append(
                    String.format(
                        Locale.US,
                        "C%.1f %.1f, %.1f %.1f, %.1f %.1f ",
                        cmd.c1.x, cmd.c1.y,
                        cmd.c2.x, cmd.c2.y,
                        cmd.p.x, cmd.p.y
                    )
                )
                is PathCommand.Close -> sb.append("Z ")
            }
        }
        return sb.toString().trim()
    }

    fun recalculateCounts(updatedCommands: List<PathCommand>): VectorPathLayer {
        var nodes = 0
        var curves = 0
        var lines = 0
        for (cmd in updatedCommands) {
            when (cmd) {
                is PathCommand.MoveTo -> nodes++
                is PathCommand.LineTo -> {
                    nodes++
                    lines++
                }
                is PathCommand.CubicTo -> {
                    nodes++
                    curves++
                }
                is PathCommand.Close -> {}
            }
        }
        return copy(
            commands = updatedCommands,
            nodeCount = nodes,
            curveCount = curves,
            lineCount = lines
        )
    }
}

data class VectorTraceResult(
    val width: Int,
    val height: Int,
    val layers: List<VectorPathLayer>,
    val processingTimeMs: Long,
    val rawContourPointsCount: Int,
    val configUsed: TraceConfig
) {
    val visibleLayers: List<VectorPathLayer>
        get() = layers.filter { it.isVisible }

    val totalNodes: Int
        get() = visibleLayers.sumOf { it.nodeCount }

    val totalCurves: Int
        get() = visibleLayers.sumOf { it.curveCount }

    val totalLines: Int
        get() = visibleLayers.sumOf { it.lineCount }

    val reductionPercent: Int
        get() {
            if (rawContourPointsCount <= 0) return 0
            val saved = (rawContourPointsCount - totalNodes).coerceAtLeast(0)
            return ((saved * 100f) / rawContourPointsCount).toInt().coerceIn(0, 99)
        }

    val estimatedSvgBytes: Int
        get() = buildSvgString().toByteArray(Charsets.UTF_8).size

    fun buildSvgString(): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $width $height\" width=\"$width\" height=\"$height\">\n"
        )
        sb.append("  <!-- Generated by VectoForge Studio | Mode: ${configUsed.mode.title} | Nodes: $totalNodes -->\n")
        for (layer in visibleLayers) {
            val d = layer.toSvgPathString()
            if (d.isEmpty()) continue
            if (layer.strokeColorHex != null && layer.fillColorHex == null) {
                sb.append(
                    String.format(
                        Locale.US,
                        "  <path id=\"%s\" d=\"%s\" fill=\"none\" stroke=\"%s\" stroke-width=\"%.1f\" stroke-linecap=\"round\" stroke-linejoin=\"round\" />\n",
                        layer.id,
                        d,
                        layer.strokeColorHex,
                        layer.strokeWidth
                    )
                )
            } else {
                val fill = layer.fillColorHex ?: "#000000"
                sb.append(
                    "  <path id=\"${layer.id}\" d=\"$d\" fill=\"$fill\" fill-rule=\"evenodd\" />\n"
                )
            }
        }
        sb.append("</svg>")
        return sb.toString()
    }

    /**
     * Lists all editable anchor nodes across visible layers in sequential order.
     */
    fun listEditableNodes(): List<EditableNodeInfo> {
        val rawList = mutableListOf<Triple<VectorPathLayer, Int, PathCommand>>()
        for (layer in visibleLayers) {
            for ((cmdIdx, cmd) in layer.commands.withIndex()) {
                if (cmd is PathCommand.MoveTo || cmd is PathCommand.LineTo || cmd is PathCommand.CubicTo) {
                    rawList.add(Triple(layer, cmdIdx, cmd))
                }
            }
        }
        val total = rawList.size
        return rawList.mapIndexed { idx, (layer, cmdIdx, cmd) ->
            val anchorCountInLayer = layer.commands.count {
                it is PathCommand.MoveTo || it is PathCommand.LineTo || it is PathCommand.CubicTo
            }
            when (cmd) {
                is PathCommand.MoveTo -> EditableNodeInfo(
                    layerId = layer.id,
                    layerName = layer.name,
                    commandIndex = cmdIdx,
                    globalNodeOrdinal = idx + 1,
                    totalEditableNodes = total,
                    commandTypeLabel = "MoveTo (Titik Awal)",
                    anchor = cmd.p,
                    control1 = null,
                    control2 = null,
                    canConvertCurveLine = false,
                    canDelete = anchorCountInLayer > 3
                )
                is PathCommand.LineTo -> EditableNodeInfo(
                    layerId = layer.id,
                    layerName = layer.name,
                    commandIndex = cmdIdx,
                    globalNodeOrdinal = idx + 1,
                    totalEditableNodes = total,
                    commandTypeLabel = "LineTo (Sudut Lurus)",
                    anchor = cmd.p,
                    control1 = null,
                    control2 = null,
                    canConvertCurveLine = true,
                    canDelete = anchorCountInLayer > 2
                )
                is PathCommand.CubicTo -> EditableNodeInfo(
                    layerId = layer.id,
                    layerName = layer.name,
                    commandIndex = cmdIdx,
                    globalNodeOrdinal = idx + 1,
                    totalEditableNodes = total,
                    commandTypeLabel = "Cubic Bezier (Lengkung)",
                    anchor = cmd.p,
                    control1 = cmd.c1,
                    control2 = cmd.c2,
                    canConvertCurveLine = true,
                    canDelete = anchorCountInLayer > 2
                )
                is PathCommand.Close -> error("Unreachable")
            }
        }
    }

    /**
     * Shifts a specific node (Anchor, Control 1, or Control 2) by (dx, dy) in SVG coordinate space.
     * When shifting an Anchor, adjacent CubicTo control handles are translated in tandem to preserve
     * smooth tangent geometry.
     */
    fun shiftNode(
        target: SelectedPathNode,
        dx: Float,
        dy: Float
    ): VectorTraceResult {
        val maxX = width.toFloat().coerceAtLeast(1f)
        val maxY = height.toFloat().coerceAtLeast(1f)

        fun clampPt(pt: VecPoint, deltaX: Float, deltaY: Float): VecPoint = VecPoint(
            x = (pt.x + deltaX).coerceIn(0f, maxX),
            y = (pt.y + deltaY).coerceIn(0f, maxY)
        )

        val updatedLayers = layers.map { layer ->
            if (layer.id != target.layerId) return@map layer
            val cmds = layer.commands.toMutableList()
            val idx = target.commandIndex
            if (idx !in cmds.indices) return@map layer

            when (val current = cmds[idx]) {
                is PathCommand.MoveTo -> {
                    cmds[idx] = PathCommand.MoveTo(clampPt(current.p, dx, dy))
                    // If the next command is CubicTo, translate its outgoing c1 handle too
                    val nextIdx = idx + 1
                    if (nextIdx in cmds.indices && cmds[nextIdx] is PathCommand.CubicTo) {
                        val nextCubic = cmds[nextIdx] as PathCommand.CubicTo
                        cmds[nextIdx] = nextCubic.copy(c1 = clampPt(nextCubic.c1, dx, dy))
                    }
                }
                is PathCommand.LineTo -> {
                    cmds[idx] = PathCommand.LineTo(clampPt(current.p, dx, dy))
                    val nextIdx = idx + 1
                    if (nextIdx in cmds.indices && cmds[nextIdx] is PathCommand.CubicTo) {
                        val nextCubic = cmds[nextIdx] as PathCommand.CubicTo
                        cmds[nextIdx] = nextCubic.copy(c1 = clampPt(nextCubic.c1, dx, dy))
                    }
                }
                is PathCommand.CubicTo -> {
                    when (target.subPoint) {
                        NodeSubPoint.ANCHOR -> {
                            cmds[idx] = current.copy(
                                p = clampPt(current.p, dx, dy),
                                c2 = clampPt(current.c2, dx, dy)
                            )
                            val nextIdx = idx + 1
                            if (nextIdx in cmds.indices && cmds[nextIdx] is PathCommand.CubicTo) {
                                val nextCubic = cmds[nextIdx] as PathCommand.CubicTo
                                cmds[nextIdx] = nextCubic.copy(c1 = clampPt(nextCubic.c1, dx, dy))
                            }
                        }
                        NodeSubPoint.CONTROL_1 -> {
                            cmds[idx] = current.copy(c1 = clampPt(current.c1, dx, dy))
                        }
                        NodeSubPoint.CONTROL_2 -> {
                            cmds[idx] = current.copy(c2 = clampPt(current.c2, dx, dy))
                        }
                    }
                }
                is PathCommand.Close -> {}
            }
            layer.recalculateCounts(cmds)
        }
        return copy(layers = updatedLayers)
    }

    /**
     * Deletes a specific anchor node at (layerId, commandIndex) and heals the SVG path contour.
     */
    fun deleteNode(target: SelectedPathNode): VectorTraceResult {
        val updatedLayers = layers.mapNotNull { layer ->
            if (layer.id != target.layerId) return@mapNotNull layer
            val cmds = layer.commands.toMutableList()
            val idx = target.commandIndex
            if (idx !in cmds.indices) return@mapNotNull layer

            val anchorCount = cmds.count {
                it is PathCommand.MoveTo || it is PathCommand.LineTo || it is PathCommand.CubicTo
            }
            if (anchorCount <= 2) {
                // Keep at least 2 anchor points so path stays valid
                return@mapNotNull layer
            }

            val removed = cmds.removeAt(idx)
            if (removed is PathCommand.MoveTo && idx < cmds.size) {
                // Promote the new first segment's endpoint to MoveTo so the sub-path starts cleanly
                when (val next = cmds[idx]) {
                    is PathCommand.LineTo -> cmds[idx] = PathCommand.MoveTo(next.p)
                    is PathCommand.CubicTo -> cmds[idx] = PathCommand.MoveTo(next.p)
                    else -> {}
                }
            }
            layer.recalculateCounts(cmds)
        }
        return copy(layers = updatedLayers)
    }

    /**
     * Toggles a node at (layerId, commandIndex) between a sharp LineTo and a smooth CubicTo Bezier curve.
     */
    fun toggleNodeCurveType(target: SelectedPathNode): VectorTraceResult {
        val updatedLayers = layers.map { layer ->
            if (layer.id != target.layerId) return@map layer
            val cmds = layer.commands.toMutableList()
            val idx = target.commandIndex
            if (idx !in cmds.indices) return@map layer

            // Find preceding anchor point for smooth handle interpolation
            var prevPt: VecPoint? = null
            for (i in (idx - 1) downTo 0) {
                when (val prevCmd = cmds[i]) {
                    is PathCommand.MoveTo -> {
                        prevPt = prevCmd.p
                        break
                    }
                    is PathCommand.LineTo -> {
                        prevPt = prevCmd.p
                        break
                    }
                    is PathCommand.CubicTo -> {
                        prevPt = prevCmd.p
                        break
                    }
                    is PathCommand.Close -> {}
                }
            }

            when (val current = cmds[idx]) {
                is PathCommand.CubicTo -> {
                    cmds[idx] = PathCommand.LineTo(current.p)
                }
                is PathCommand.LineTo -> {
                    val start = prevPt ?: current.p
                    val end = current.p
                    val dx = end.x - start.x
                    val dy = end.y - start.y
                    // Create natural cubic handles with slight perpendicular offset
                    val c1 = VecPoint(start.x + dx * 0.33f - dy * 0.12f, start.y + dy * 0.33f + dx * 0.12f)
                    val c2 = VecPoint(start.x + dx * 0.67f - dy * 0.12f, start.y + dy * 0.67f + dx * 0.12f)
                    cmds[idx] = PathCommand.CubicTo(c1 = c1, c2 = c2, p = end)
                }
                else -> {}
            }
            layer.recalculateCounts(cmds)
        }
        return copy(layers = updatedLayers)
    }
}

enum class BatchItemStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

data class BatchImageItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val sourceBitmap: Bitmap,
    val useGlobalConfig: Boolean = true,
    val customConfig: TraceConfig = TraceConfig(),
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val result: VectorTraceResult? = null,
    val recommendedMode: TraceMode = TraceMode.GEOMETRIC_SHARP,
    val includedInExport: Boolean = true,
    val errorMessage: String? = null
) {
    fun effectiveConfig(globalConfig: TraceConfig): TraceConfig =
        if (useGlobalConfig) globalConfig else customConfig
}

data class ZipArchiveOptions(
    val archiveBaseName: String = "vectoforge_batch_vectors",
    val organizeByModeSubfolders: Boolean = false,
    val includeSummaryReport: Boolean = true,
    val onlySelectedItems: Boolean = true
)

enum class CanvasViewMode(val label: String) {
    SPLIT_COMPARE("Split: Before / After"),
    VECTOR_CLEAN("Hasil Vektor"),
    WIREFRAME_NODES("Wireframe & Titik Bezier"),
    ORIGINAL_RASTER("Raster Asli")
}
