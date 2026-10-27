package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.example.model.PathCommand
import com.example.model.TraceConfig
import com.example.model.TraceMode
import com.example.model.VecPoint
import com.example.model.VectorPathLayer
import com.example.model.VectorTraceResult
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object VectorTracingEngine {

    private const val MAX_WORKING_DIMENSION = 320

    fun traceBitmap(
        source: Bitmap,
        config: TraceConfig
    ): VectorTraceResult {
        val startTime = System.currentTimeMillis()

        val origW = source.width.coerceAtLeast(1)
        val origH = source.height.coerceAtLeast(1)
        val scale = min(
            1f,
            MAX_WORKING_DIMENSION.toFloat() / max(origW, origH).toFloat()
        )
        val workW = (origW * scale).roundToInt().coerceAtLeast(16)
        val workH = (origH * scale).roundToInt().coerceAtLeast(16)

        val scaledBitmap = if (workW != origW || workH != origH) {
            Bitmap.createScaledBitmap(source, workW, workH, config.mode != TraceMode.PIXEL_POLYGON)
        } else {
            source
        }

        val pixels = IntArray(workW * workH)
        scaledBitmap.getPixels(pixels, 0, workW, 0, 0, workW, workH)

        val invScaleX = origW.toFloat() / workW.toFloat()
        val invScaleY = origH.toFloat() / workH.toFloat()

        val rawCounter = IntArray(1)

        val layers = when (config.mode) {
            TraceMode.POTRACE_BW -> traceBinaryMode(
                pixels = pixels,
                w = workW,
                h = workH,
                scaleX = invScaleX,
                scaleY = invScaleY,
                config = config,
                antiMbuletPreFilter = false,
                rawPointCounter = rawCounter
            )
            TraceMode.GEOMETRIC_SHARP -> traceBinaryMode(
                pixels = pixels,
                w = workW,
                h = workH,
                scaleX = invScaleX,
                scaleY = invScaleY,
                config = config,
                antiMbuletPreFilter = true,
                rawPointCounter = rawCounter
            )
            TraceMode.VTRACER_MULTICOLOR -> traceMulticolorMode(
                pixels = pixels,
                w = workW,
                h = workH,
                scaleX = invScaleX,
                scaleY = invScaleY,
                config = config,
                strictPixelGrid = false,
                rawPointCounter = rawCounter
            )
            TraceMode.PIXEL_POLYGON -> traceMulticolorMode(
                pixels = pixels,
                w = workW,
                h = workH,
                scaleX = invScaleX,
                scaleY = invScaleY,
                config = config,
                strictPixelGrid = config.smoothness <= 0.05f,
                rawPointCounter = rawCounter
            )
            TraceMode.CENTERLINE_SKETCH -> traceCenterlineMode(
                pixels = pixels,
                w = workW,
                h = workH,
                scaleX = invScaleX,
                scaleY = invScaleY,
                config = config,
                rawPointCounter = rawCounter
            )
        }

        val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
        return VectorTraceResult(
            width = origW,
            height = origH,
            layers = layers,
            processingTimeMs = elapsed,
            rawContourPointsCount = rawCounter[0].coerceAtLeast(1),
            configUsed = config
        )
    }

    // =========================================================================
    // 1. POTRACE B/W & GEOMETRIC ANTI-MBULET PIPELINE
    // =========================================================================
    private fun traceBinaryMode(
        pixels: IntArray,
        w: Int,
        h: Int,
        scaleX: Float,
        scaleY: Float,
        config: TraceConfig,
        antiMbuletPreFilter: Boolean,
        rawPointCounter: IntArray
    ): List<VectorPathLayer> {
        val lum = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = Color.alpha(c)
            if (a < 40) {
                lum[i] = 255
            } else {
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                lum[i] = ((r * 77 + g * 150 + b * 29) shr 8)
            }
        }

        val filteredLum = if (antiMbuletPreFilter) {
            majorityEdgeCleanLuminance(lum, w, h)
        } else {
            lum
        }

        val mask = BooleanArray(w * h)
        for (i in filteredLum.indices) {
            val isDark = filteredLum[i] < config.threshold
            mask[i] = if (config.invertBw) !isDark else isDark
        }

        // Despeckle both foreground speckles and tiny background pinholes
        despeckleMask(mask, w, h, config.despeckleSize, true)
        despeckleMask(mask, w, h, (config.despeckleSize / 2).coerceAtLeast(2), false)

        if (antiMbuletPreFilter) {
            morphologicalSmoothMask(mask, w, h)
        }

        val contours = extractContoursFromMask(mask, w, h)
        if (contours.isEmpty()) return emptyList()

        val allCommands = mutableListOf<PathCommand>()
        var totalNodes = 0
        var totalCurves = 0
        var totalLines = 0
        var totalRaw = 0
        var totalArea = 0

        for (contour in contours) {
            if (contour.size < 4) continue
            totalRaw += contour.size
            val area = polygonAreaPx(contour)
            totalArea += area

            val cmds = fitContourToCommands(
                rawContour = contour,
                scaleX = scaleX,
                scaleY = scaleY,
                config = config,
                strictPixelGrid = false
            )
            for (cmd in cmds) {
                when (cmd) {
                    is PathCommand.LineTo -> {
                        totalNodes++
                        totalLines++
                    }
                    is PathCommand.CubicTo -> {
                        totalNodes++
                        totalCurves++
                    }
                    else -> {}
                }
            }
            allCommands.addAll(cmds)
        }

        rawPointCounter[0] += totalRaw
        if (allCommands.isEmpty()) return emptyList()

        val fgHex = if (config.invertBw) "#F8FAFC" else "#111827"
        return listOf(
            VectorPathLayer(
                id = "layer_bw_main",
                name = if (antiMbuletPreFilter) "Geometric Shape Path" else "Potrace Silhouette Path",
                commands = allCommands,
                fillColorHex = fgHex,
                strokeColorHex = null,
                nodeCount = totalNodes,
                curveCount = totalCurves,
                lineCount = totalLines,
                rawPointCount = totalRaw,
                areaPx = totalArea,
                isBackgroundCandidate = false,
                isVisible = true
            )
        )
    }

    // =========================================================================
    // 2. VTRACER MULTICOLOR & PIXEL ART POLYGON PIPELINE
    // =========================================================================
    private fun traceMulticolorMode(
        pixels: IntArray,
        w: Int,
        h: Int,
        scaleX: Float,
        scaleY: Float,
        config: TraceConfig,
        strictPixelGrid: Boolean,
        rawPointCounter: IntArray
    ): List<VectorPathLayer> {
        val k = config.colorCount.coerceIn(2, 12)
        val quantized = quantizeColorsKMeans(pixels, w, h, k)
        val palette = quantized.first
        val labels = quantized.second

        // Detect border color to flag background candidate
        val borderCounts = IntArray(palette.size)
        for (x in 0 until w) {
            val top = labels[x]
            val bot = labels[(h - 1) * w + x]
            if (top >= 0) borderCounts[top]++
            if (bot >= 0) borderCounts[bot]++
        }
        for (y in 0 until h) {
            val left = labels[y * w]
            val right = labels[y * w + (w - 1)]
            if (left >= 0) borderCounts[left]++
            if (right >= 0) borderCounts[right]++
        }
        val dominantBorderCluster = borderCounts.indices.maxByOrNull { borderCounts[it] } ?: -1
        val borderPerimeter = 2 * (w + h)

        val resultLayers = mutableListOf<VectorPathLayer>()

        for (clusterIdx in palette.indices) {
            val colorInt = palette[clusterIdx]
            if (Color.alpha(colorInt) < 40) continue

            val mask = BooleanArray(w * h)
            var pixelArea = 0
            for (i in labels.indices) {
                if (labels[i] == clusterIdx) {
                    mask[i] = true
                    pixelArea++
                }
            }
            if (pixelArea <= config.despeckleSize) continue

            despeckleMask(mask, w, h, config.despeckleSize, true)
            if (!strictPixelGrid) {
                despeckleMask(mask, w, h, (config.despeckleSize / 2).coerceAtLeast(2), false)
            }

            val contours = extractContoursFromMask(mask, w, h)
            if (contours.isEmpty()) continue

            val layerCommands = mutableListOf<PathCommand>()
            var layerNodes = 0
            var layerCurves = 0
            var layerLines = 0
            var layerRaw = 0

            for (contour in contours) {
                if (contour.size < 4) continue
                layerRaw += contour.size
                val cmds = fitContourToCommands(
                    rawContour = contour,
                    scaleX = scaleX,
                    scaleY = scaleY,
                    config = config,
                    strictPixelGrid = strictPixelGrid
                )
                for (cmd in cmds) {
                    when (cmd) {
                        is PathCommand.LineTo -> {
                            layerNodes++
                            layerLines++
                        }
                        is PathCommand.CubicTo -> {
                            layerNodes++
                            layerCurves++
                        }
                        else -> {}
                    }
                }
                layerCommands.addAll(cmds)
            }

            if (layerCommands.isEmpty()) continue
            rawPointCounter[0] += layerRaw

            val hex = String.format(
                Locale.US,
                "#%02X%02X%02X",
                Color.red(colorInt),
                Color.green(colorInt),
                Color.blue(colorInt)
            )
            val isBgCandidate = clusterIdx == dominantBorderCluster &&
                borderCounts[clusterIdx] > (borderPerimeter * 0.45f)

            resultLayers.add(
                VectorPathLayer(
                    id = "layer_color_${clusterIdx + 1}_${hex.removePrefix("#")}",
                    name = if (isBgCandidate) "Layer $hex (Background)" else "Layer $hex",
                    commands = layerCommands,
                    fillColorHex = hex,
                    strokeColorHex = null,
                    nodeCount = layerNodes,
                    curveCount = layerCurves,
                    lineCount = layerLines,
                    rawPointCount = layerRaw,
                    areaPx = pixelArea,
                    isBackgroundCandidate = isBgCandidate,
                    isVisible = !(config.removeBackground && isBgCandidate && palette.size > 1)
                )
            )
        }

        // Order from largest area (bottom layer) to smallest detail (top layer)
        val sorted = resultLayers.sortedByDescending { it.areaPx }
        return if (sorted.isNotEmpty() && sorted.none { it.isVisible }) {
            sorted.mapIndexed { idx, layer ->
                if (idx == sorted.lastIndex) layer.copy(isVisible = true) else layer
            }
        } else {
            sorted
        }
    }

    // =========================================================================
    // 3. ZHANG-SUEN CENTERLINE SKELETONIZATION PIPELINE
    // =========================================================================
    private fun traceCenterlineMode(
        pixels: IntArray,
        w: Int,
        h: Int,
        scaleX: Float,
        scaleY: Float,
        config: TraceConfig,
        rawPointCounter: IntArray
    ): List<VectorPathLayer> {
        val mask = BooleanArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = Color.alpha(c)
            if (a < 40) continue
            val lum = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) shr 8
            val isDark = lum < config.threshold
            mask[i] = if (config.invertBw) !isDark else isDark
        }

        despeckleMask(mask, w, h, config.despeckleSize, true)
        val skeleton = zhangSuenThinning(mask, w, h)
        val chains = traceSkeletonChains(skeleton, w, h)

        val allCommands = mutableListOf<PathCommand>()
        var totalNodes = 0
        var totalCurves = 0
        var totalLines = 0
        var totalRaw = 0

        for (chain in chains) {
            if (chain.size < 3) continue
            totalRaw += chain.size
            val simplified = ramerDouglasPeuckerOpen(chain, config.simplifyTolerance)
            if (simplified.size < 2) continue

            val scaledPts = simplified.map { VecPoint(it.x * scaleX, it.y * scaleY) }
            allCommands.add(PathCommand.MoveTo(scaledPts.first()))
            totalNodes++

            if (scaledPts.size == 2 || config.smoothness <= 0.1f) {
                for (i in 1 until scaledPts.size) {
                    allCommands.add(PathCommand.LineTo(scaledPts[i]))
                    totalNodes++
                    totalLines++
                }
            } else {
                val tension = (config.smoothness * 0.32f).coerceIn(0.08f, 0.45f)
                for (i in 0 until scaledPts.size - 1) {
                    val p0 = if (i == 0) scaledPts[0] else scaledPts[i - 1]
                    val p1 = scaledPts[i]
                    val p2 = scaledPts[i + 1]
                    val p3 = if (i + 2 < scaledPts.size) scaledPts[i + 2] else scaledPts[i + 1]

                    val angle1 = interiorAngleDeg(p0, p1, p2)
                    if (angle1 < config.cornerThresholdDeg && i > 0) {
                        allCommands.add(PathCommand.LineTo(p2))
                        totalNodes++
                        totalLines++
                    } else {
                        val c1 = VecPoint(
                            p1.x + (p2.x - p0.x) * tension,
                            p1.y + (p2.y - p0.y) * tension
                        )
                        val c2 = VecPoint(
                            p2.x - (p3.x - p1.x) * tension,
                            p2.y - (p3.y - p1.y) * tension
                        )
                        allCommands.add(PathCommand.CubicTo(c1, c2, p2))
                        totalNodes++
                        totalCurves++
                    }
                }
            }
        }

        rawPointCounter[0] += totalRaw
        if (allCommands.isEmpty()) return emptyList()

        return listOf(
            VectorPathLayer(
                id = "layer_centerline_strokes",
                name = "Centerline Skeleton (${chains.size} strokes)",
                commands = allCommands,
                fillColorHex = null,
                strokeColorHex = if (config.invertBw) "#F8FAFC" else "#111827",
                strokeWidth = config.strokeWidth,
                nodeCount = totalNodes,
                curveCount = totalCurves,
                lineCount = totalLines,
                rawPointCount = totalRaw,
                areaPx = totalRaw,
                isBackgroundCandidate = false,
                isVisible = true
            )
        )
    }

    // =========================================================================
    // CONTOUR TO BEZIER / POLYGON FITTING (POTRACE + ANTI-MBULET LOGIC)
    // =========================================================================
    private fun fitContourToCommands(
        rawContour: List<VecPoint>,
        scaleX: Float,
        scaleY: Float,
        config: TraceConfig,
        strictPixelGrid: Boolean
    ): List<PathCommand> {
        if (rawContour.size < 3) return emptyList()

        // Step 1: Merge collinear grid steps along raw pixel contour
        val collinearReduced = mergeCollinearLoop(rawContour, 0.05f)
        if (collinearReduced.size < 3) return emptyList()

        if (strictPixelGrid) {
            val cmds = mutableListOf<PathCommand>()
            val first = VecPoint(collinearReduced[0].x * scaleX, collinearReduced[0].y * scaleY)
            cmds.add(PathCommand.MoveTo(first))
            for (i in 1 until collinearReduced.size) {
                val pt = collinearReduced[i]
                cmds.add(PathCommand.LineTo(VecPoint(pt.x * scaleX, pt.y * scaleY)))
            }
            cmds.add(PathCommand.Close)
            return cmds
        }

        // Step 2: VTracer-style staircase removal on 1px diagonal steps
        val deStaircased = removePixelStaircases(collinearReduced)

        // Step 3: Closed-loop Ramer-Douglas-Peucker simplification
        val rdpPoints = ramerDouglasPeuckerClosed(deStaircased, config.simplifyTolerance)
        if (rdpPoints.size < 3) return emptyList()

        // Step 4: Anti-Mbulet Orthogonal & Collinear Line Snapping
        val snappedPoints = if (config.orthogonalSnap || config.mode == TraceMode.GEOMETRIC_SHARP) {
            snapOrthogonalAndMergeCollinear(rdpPoints, snapAngleDeg = 6.5f)
        } else {
            mergeCollinearLoop(rdpPoints, 0.35f)
        }
        if (snappedPoints.size < 3) return emptyList()

        val scaled = snappedPoints.map { VecPoint(it.x * scaleX, it.y * scaleY) }
        val n = scaled.size

        // Step 5: Corner Detection (Potrace alphamax + Anti-Mbulet straight segment lock)
        val isCorner = BooleanArray(n)
        val avgScale = (scaleX + scaleY) * 0.5f
        val longSegmentThreshold = 14f * avgScale

        for (i in 0 until n) {
            val prev = scaled[(i - 1 + n) % n]
            val curr = scaled[i]
            val next = scaled[(i + 1) % n]

            val angle = interiorAngleDeg(prev, curr, next)
            val lenPrev = hypot(curr.x - prev.x, curr.y - prev.y)
            val lenNext = hypot(next.x - curr.x, next.y - curr.y)

            val sharpTurn = angle < config.cornerThresholdDeg
            val geometricLongEdgeCorner =
                (config.mode == TraceMode.GEOMETRIC_SHARP || config.orthogonalSnap) &&
                    (lenPrev > longSegmentThreshold || lenNext > longSegmentThreshold) &&
                    angle < 158f

            isCorner[i] = sharpTurn || geometricLongEdgeCorner || config.smoothness <= 0.08f
        }

        // Step 6: Build SVG PathCommands (LineTo for sharp/geometric edges, CubicTo for smooth arcs)
        val commands = mutableListOf<PathCommand>()
        commands.add(PathCommand.MoveTo(scaled[0]))

        val tension = (config.smoothness * 0.30f).coerceIn(0.05f, 0.42f)

        for (i in 0 until n) {
            val p0 = scaled[(i - 1 + n) % n]
            val p1 = scaled[i]
            val p2 = scaled[(i + 1) % n]
            val p3 = scaled[(i + 2) % n]

            val segLen = hypot(p2.x - p1.x, p2.y - p1.y)
            val keepStraightLine =
                (isCorner[i] && isCorner[(i + 1) % n]) ||
                    ((config.mode == TraceMode.GEOMETRIC_SHARP || config.orthogonalSnap) &&
                        segLen > longSegmentThreshold)

            if (keepStraightLine) {
                if (i < n - 1) {
                    commands.add(PathCommand.LineTo(p2))
                }
            } else {
                // Smooth cubic Bezier segment with corner-aware tangent clamping
                val t1x = if (isCorner[i]) (p2.x - p1.x) * 0.25f else (p2.x - p0.x) * tension
                val t1y = if (isCorner[i]) (p2.y - p1.y) * 0.25f else (p2.y - p0.y) * tension

                val nextCorner = isCorner[(i + 1) % n]
                val t2x = if (nextCorner) (p2.x - p1.x) * 0.25f else (p3.x - p1.x) * tension
                val t2y = if (nextCorner) (p2.y - p1.y) * 0.25f else (p3.y - p1.y) * tension

                val c1 = VecPoint(p1.x + t1x, p1.y + t1y)
                val c2 = VecPoint(p2.x - t2x, p2.y - t2y)
                commands.add(PathCommand.CubicTo(c1, c2, p2))
            }
        }

        commands.add(PathCommand.Close)
        return commands
    }

    // =========================================================================
    // DIRECTED PIXEL BOUNDARY CONTOUR WALKER (POTRACE / MARCHING SQUARES)
    // =========================================================================
    private fun extractContoursFromMask(mask: BooleanArray, w: Int, h: Int): List<List<VecPoint>> {
        // Horizontal and vertical directed edges between pixel cells
        // A foreground pixel at (x, y) owns 4 CCW boundary half-edges:
        // Top: (x+1, y) -> (x, y) [dir 2: Left]
        // Left: (x, y) -> (x, y+1) [dir 1: Down]
        // Bottom: (x, y+1) -> (x+1, y+1) [dir 0: Right]
        // Right: (x+1, y+1) -> (x+1, y) [dir 3: Up]
        val gw = w + 1
        val gh = h + 1
        val maxEdges = gw * gh * 4
        val usedEdge = BooleanArray(maxEdges)

        fun isFg(px: Int, py: Int): Boolean {
            if (px < 0 || px >= w || py < 0 || py >= h) return false
            return mask[py * w + px]
        }

        fun hasDirectedEdge(vx: Int, vy: Int, dir: Int): Boolean {
            return when (dir) {
                0 -> isFg(vx, vy - 1) && !isFg(vx, vy)         // Right: fg above, bg below
                1 -> isFg(vx, vy) && !isFg(vx - 1, vy)         // Down: fg right, bg left
                2 -> isFg(vx - 1, vy) && !isFg(vx - 1, vy - 1) // Left: fg below, bg above
                3 -> isFg(vx - 1, vy - 1) && !isFg(vx, vy - 1) // Up: fg left, bg right
                else -> false
            }
        }

        val dx = intArrayOf(1, 0, -1, 0)
        val dy = intArrayOf(0, 1, 0, -1)

        val contours = mutableListOf<List<VecPoint>>()

        for (vy in 0..h) {
            for (vx in 0..w) {
                for (startDir in 0..3) {
                    val startKey = (vy * gw + vx) * 4 + startDir
                    if (usedEdge[startKey] || !hasDirectedEdge(vx, vy, startDir)) continue

                    val loop = mutableListOf<VecPoint>()
                    var cx = vx
                    var cy = vy
                    var dir = startDir
                    var safety = 0
                    val maxSteps = w * h * 4

                    while (safety++ < maxSteps) {
                        val edgeKey = (cy * gw + cx) * 4 + dir
                        if (usedEdge[edgeKey]) break
                        usedEdge[edgeKey] = true
                        loop.add(VecPoint(cx.toFloat(), cy.toFloat()))

                        cx += dx[dir]
                        cy += dy[dir]
                        if (cx == vx && cy == vy) break

                        // Potrace right-hand turn policy to stay on coherent boundary
                        val tryDirs = intArrayOf(
                            (dir + 3) and 3, // Turn right
                            dir,             // Straight
                            (dir + 1) and 3, // Turn left
                            (dir + 2) and 3  // Back
                        )
                        var foundNext = -1
                        for (cand in tryDirs) {
                            val candKey = (cy * gw + cx) * 4 + cand
                            if (!usedEdge[candKey] && hasDirectedEdge(cx, cy, cand)) {
                                foundNext = cand
                                break
                            }
                        }
                        if (foundNext == -1) break
                        dir = foundNext
                    }

                    if (loop.size >= 4) {
                        contours.add(loop)
                    }
                }
            }
        }

        return contours
    }

    // =========================================================================
    // GEOMETRY SIMPLIFICATION & ANTI-MBULET HELPERS
    // =========================================================================
    private fun removePixelStaircases(points: List<VecPoint>): List<VecPoint> {
        if (points.size < 6) return points
        val n = points.size
        val result = mutableListOf<VecPoint>()
        var i = 0
        while (i < n) {
            val prev = points[(i - 1 + n) % n]
            val curr = points[i]
            val next = points[(i + 1) % n]
            val d1 = hypot(curr.x - prev.x, curr.y - prev.y)
            val d2 = hypot(next.x - curr.x, next.y - curr.y)
            // If two adjacent 1-pixel steps form a tiny right-angle staircase, use midpoint
            if (d1 <= 1.5f && d2 <= 1.5f) {
                result.add(VecPoint((prev.x + curr.x * 2f + next.x) * 0.25f, (prev.y + curr.y * 2f + next.y) * 0.25f))
            } else {
                result.add(curr)
            }
            i++
        }
        return result
    }

    private fun ramerDouglasPeuckerClosed(points: List<VecPoint>, epsilon: Float): List<VecPoint> {
        if (points.size <= 4) return points
        // Find two farthest points on the loop to split into two open chains
        var idxA = 0
        var idxB = points.size / 2
        var maxDistSq = -1f
        val step = (points.size / 24).coerceAtLeast(1)
        for (i in points.indices step step) {
            for (j in i + 1 until points.size step step) {
                val dx = points[i].x - points[j].x
                val dy = points[i].y - points[j].y
                val dSq = dx * dx + dy * dy
                if (dSq > maxDistSq) {
                    maxDistSq = dSq
                    idxA = i
                    idxB = j
                }
            }
        }
        if (idxA > idxB) {
            val tmp = idxA
            idxA = idxB
            idxB = tmp
        }

        val chain1 = points.subList(idxA, idxB + 1)
        val chain2 = points.subList(idxB, points.size) + points.subList(0, idxA + 1)

        val simp1 = ramerDouglasPeuckerOpen(chain1, epsilon)
        val simp2 = ramerDouglasPeuckerOpen(chain2, epsilon)

        return simp1.dropLast(1) + simp2.dropLast(1)
    }

    private fun ramerDouglasPeuckerOpen(points: List<VecPoint>, epsilon: Float): List<VecPoint> {
        if (points.size <= 2) return points
        var maxDist = 0f
        var index = 0
        val start = points.first()
        val end = points.last()

        for (i in 1 until points.size - 1) {
            val d = perpendicularDistance(points[i], start, end)
            if (d > maxDist) {
                index = i
                maxDist = d
            }
        }

        return if (maxDist > epsilon) {
            val left = ramerDouglasPeuckerOpen(points.subList(0, index + 1), epsilon)
            val right = ramerDouglasPeuckerOpen(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(start, end)
        }
    }

    private fun perpendicularDistance(pt: VecPoint, lineStart: VecPoint, lineEnd: VecPoint): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        val mag = hypot(dx, dy)
        if (mag < 1e-5f) return hypot(pt.x - lineStart.x, pt.y - lineStart.y)
        return abs(dy * pt.x - dx * pt.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x) / mag
    }

    private fun mergeCollinearLoop(points: List<VecPoint>, tolerance: Float): List<VecPoint> {
        if (points.size <= 3) return points
        val out = points.toMutableList()
        var changed = true
        while (changed && out.size > 3) {
            changed = false
            var i = 0
            while (i < out.size && out.size > 3) {
                val prev = out[(i - 1 + out.size) % out.size]
                val curr = out[i]
                val next = out[(i + 1) % out.size]
                if (perpendicularDistance(curr, prev, next) <= tolerance) {
                    out.removeAt(i)
                    changed = true
                } else {
                    i++
                }
            }
        }
        return out
    }

    private fun snapOrthogonalAndMergeCollinear(
        points: List<VecPoint>,
        snapAngleDeg: Float
    ): List<VecPoint> {
        if (points.size < 3) return points
        val snapped = points.toMutableList()
        val tanThreshold = kotlin.math.tan(Math.toRadians(snapAngleDeg.toDouble())).toFloat()

        for (pass in 0..1) {
            for (i in snapped.indices) {
                val j = (i + 1) % snapped.size
                val a = snapped[i]
                val b = snapped[j]
                val adx = abs(b.x - a.x)
                val ady = abs(b.y - a.y)
                if (adx > 3f && ady / adx < tanThreshold) {
                    val avgY = (a.y + b.y) * 0.5f
                    snapped[i] = VecPoint(a.x, avgY)
                    snapped[j] = VecPoint(b.x, avgY)
                } else if (ady > 3f && adx / ady < tanThreshold) {
                    val avgX = (a.x + b.x) * 0.5f
                    snapped[i] = VecPoint(avgX, a.y)
                    snapped[j] = VecPoint(avgX, b.y)
                }
            }
        }
        return mergeCollinearLoop(snapped, 0.75f)
    }

    private fun interiorAngleDeg(a: VecPoint, b: VecPoint, c: VecPoint): Float {
        val v1x = a.x - b.x
        val v1y = a.y - b.y
        val v2x = c.x - b.x
        val v2y = c.y - b.y
        val m1 = hypot(v1x, v1y)
        val m2 = hypot(v2x, v2y)
        if (m1 < 1e-4f || m2 < 1e-4f) return 180f
        val cosTheta = ((v1x * v2x + v1y * v2y) / (m1 * m2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosTheta.toDouble())).toFloat()
    }

    private fun polygonAreaPx(pts: List<VecPoint>): Int {
        var sum = 0f
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            sum += (a.x * b.y - b.x * a.y)
        }
        return (abs(sum) * 0.5f).roundToInt()
    }

    // =========================================================================
    // CONNECTED-COMPONENT DESPECKLE (POTRACE TURDSIZE) & PRE-FILTERS
    // =========================================================================
    private fun despeckleMask(
        mask: BooleanArray,
        w: Int,
        h: Int,
        minSize: Int,
        targetValue: Boolean
    ) {
        if (minSize <= 0) return
        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        val component = IntArray(w * h)

        for (i in mask.indices) {
            if (visited[i] || mask[i] != targetValue) continue
            var head = 0
            var tail = 0
            var compSize = 0

            visited[i] = true
            queue[tail++] = i

            while (head < tail) {
                val curr = queue[head++]
                component[compSize++] = curr
                val cx = curr % w
                val cy = curr / w

                if (cx > 0) {
                    val nb = curr - 1
                    if (!visited[nb] && mask[nb] == targetValue) {
                        visited[nb] = true
                        queue[tail++] = nb
                    }
                }
                if (cx < w - 1) {
                    val nb = curr + 1
                    if (!visited[nb] && mask[nb] == targetValue) {
                        visited[nb] = true
                        queue[tail++] = nb
                    }
                }
                if (cy > 0) {
                    val nb = curr - w
                    if (!visited[nb] && mask[nb] == targetValue) {
                        visited[nb] = true
                        queue[tail++] = nb
                    }
                }
                if (cy < h - 1) {
                    val nb = curr + w
                    if (!visited[nb] && mask[nb] == targetValue) {
                        visited[nb] = true
                        queue[tail++] = nb
                    }
                }
            }

            if (compSize <= minSize) {
                val flip = !targetValue
                for (k in 0 until compSize) {
                    mask[component[k]] = flip
                }
            }
        }
    }

    private fun majorityEdgeCleanLuminance(lum: IntArray, w: Int, h: Int): IntArray {
        val out = lum.clone()
        val window = IntArray(9)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var idx = 0
                for (dy in -1..1) {
                    val row = (y + dy) * w
                    for (dx in -1..1) {
                        window[idx++] = lum[row + x + dx]
                    }
                }
                window.sort()
                out[y * w + x] = window[4] // 3x3 Median filter removes anti-aliasing edge ringing
            }
        }
        return out
    }

    private fun morphologicalSmoothMask(mask: BooleanArray, w: Int, h: Int) {
        val copy = mask.clone()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var fgCount = 0
                for (dy in -1..1) {
                    val r = (y + dy) * w
                    for (dx in -1..1) {
                        if (copy[r + x + dx]) fgCount++
                    }
                }
                val i = y * w + x
                if (fgCount >= 6) mask[i] = true
                else if (fgCount <= 3) mask[i] = false
            }
        }
    }

    // =========================================================================
    // K-MEANS++ COLOR QUANTIZATION (VTRACER MULTICOLOR LAYER CLUSTERING)
    // =========================================================================
    private fun quantizeColorsKMeans(
        pixels: IntArray,
        w: Int,
        h: Int,
        k: Int
    ): Pair<IntArray, IntArray> {
        val n = w * h
        val labels = IntArray(n) { -1 }

        val centerR = FloatArray(k)
        val centerG = FloatArray(k)
        val centerB = FloatArray(k)

        // Initialize centers evenly across non-transparent image samples
        val validIndices = mutableListOf<Int>()
        for (i in 0 until n step 3) {
            if (Color.alpha(pixels[i]) >= 40) validIndices.add(i)
        }
        if (validIndices.isEmpty()) {
            return Pair(intArrayOf(Color.BLACK), IntArray(n))
        }

        // First center: darkest pixel; second: brightest; others spread by max distance (K-Means++)
        var minLumIdx = validIndices[0]
        var minLum = 1000
        for (idx in validIndices) {
            val c = pixels[idx]
            val l = Color.red(c) + Color.green(c) + Color.blue(c)
            if (l < minLum) {
                minLum = l
                minLumIdx = idx
            }
        }
        val firstColor = pixels[minLumIdx]
        centerR[0] = Color.red(firstColor).toFloat()
        centerG[0] = Color.green(firstColor).toFloat()
        centerB[0] = Color.blue(firstColor).toFloat()

        for (cIdx in 1 until k) {
            var bestPixelIdx = validIndices[0]
            var bestMinDist = -1f
            for (idx in validIndices) {
                val p = pixels[idx]
                val pr = Color.red(p).toFloat()
                val pg = Color.green(p).toFloat()
                val pb = Color.blue(p).toFloat()
                var minDistToExisting = Float.MAX_VALUE
                for (prev in 0 until cIdx) {
                    val dr = pr - centerR[prev]
                    val dg = pg - centerG[prev]
                    val db = pb - centerB[prev]
                    val d = dr * dr + dg * dg + db * db
                    if (d < minDistToExisting) minDistToExisting = d
                }
                if (minDistToExisting > bestMinDist) {
                    bestMinDist = minDistToExisting
                    bestPixelIdx = idx
                }
            }
            val chosen = pixels[bestPixelIdx]
            centerR[cIdx] = Color.red(chosen).toFloat()
            centerG[cIdx] = Color.green(chosen).toFloat()
            centerB[cIdx] = Color.blue(chosen).toFloat()
        }

        val sumR = FloatArray(k)
        val sumG = FloatArray(k)
        val sumB = FloatArray(k)
        val counts = IntArray(k)

        // 6 fast iterations of K-Means
        for (iter in 0 until 6) {
            sumR.fill(0f)
            sumG.fill(0f)
            sumB.fill(0f)
            counts.fill(0)

            for (i in 0 until n) {
                val c = pixels[i]
                if (Color.alpha(c) < 40) {
                    labels[i] = -1
                    continue
                }
                val r = Color.red(c).toFloat()
                val g = Color.green(c).toFloat()
                val b = Color.blue(c).toFloat()

                var bestCluster = 0
                var bestDist = Float.MAX_VALUE
                for (j in 0 until k) {
                    val dr = r - centerR[j]
                    val dg = g - centerG[j]
                    val db = b - centerB[j]
                    val dist = dr * dr + dg * dg + db * db
                    if (dist < bestDist) {
                        bestDist = dist
                        bestCluster = j
                    }
                }
                labels[i] = bestCluster
                sumR[bestCluster] += r
                sumG[bestCluster] += g
                sumB[bestCluster] += b
                counts[bestCluster]++
            }

            for (j in 0 until k) {
                if (counts[j] > 0) {
                    centerR[j] = sumR[j] / counts[j]
                    centerG[j] = sumG[j] / counts[j]
                    centerB[j] = sumB[j] / counts[j]
                }
            }
        }

        val palette = IntArray(k) { j ->
            if (counts[j] == 0) {
                Color.TRANSPARENT
            } else {
                Color.rgb(
                    centerR[j].roundToInt().coerceIn(0, 255),
                    centerG[j].roundToInt().coerceIn(0, 255),
                    centerB[j].roundToInt().coerceIn(0, 255)
                )
            }
        }

        return Pair(palette, labels)
    }

    // =========================================================================
    // ZHANG-SUEN MORPHOLOGICAL SKELETONIZATION & CHAIN WALKER
    // =========================================================================
    private fun zhangSuenThinning(input: BooleanArray, w: Int, h: Int): BooleanArray {
        val grid = input.clone()
        val toWhite = IntArray(w * h)
        var maxIter = 45

        fun p(x: Int, y: Int): Int = if (grid[y * w + x]) 1 else 0

        while (maxIter-- > 0) {
            var changed = false

            // Sub-iteration 1
            var count = 0
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val idx = y * w + x
                    if (!grid[idx]) continue
                    val p2 = p(x, y - 1)
                    val p3 = p(x + 1, y - 1)
                    val p4 = p(x + 1, y)
                    val p5 = p(x + 1, y + 1)
                    val p6 = p(x, y + 1)
                    val p7 = p(x - 1, y + 1)
                    val p8 = p(x - 1, y)
                    val p9 = p(x - 1, y - 1)

                    val b = p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9
                    if (b < 2 || b > 6) continue

                    var a = 0
                    if (p2 == 0 && p3 == 1) a++
                    if (p3 == 0 && p4 == 1) a++
                    if (p4 == 0 && p5 == 1) a++
                    if (p5 == 0 && p6 == 1) a++
                    if (p6 == 0 && p7 == 1) a++
                    if (p7 == 0 && p8 == 1) a++
                    if (p8 == 0 && p9 == 1) a++
                    if (p9 == 0 && p2 == 1) a++

                    if (a == 1 && (p2 * p4 * p6 == 0) && (p4 * p6 * p8 == 0)) {
                        toWhite[count++] = idx
                    }
                }
            }
            if (count > 0) {
                changed = true
                for (i in 0 until count) grid[toWhite[i]] = false
            }

            // Sub-iteration 2
            count = 0
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val idx = y * w + x
                    if (!grid[idx]) continue
                    val p2 = p(x, y - 1)
                    val p3 = p(x + 1, y - 1)
                    val p4 = p(x + 1, y)
                    val p5 = p(x + 1, y + 1)
                    val p6 = p(x, y + 1)
                    val p7 = p(x - 1, y + 1)
                    val p8 = p(x - 1, y)
                    val p9 = p(x - 1, y - 1)

                    val b = p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9
                    if (b < 2 || b > 6) continue

                    var a = 0
                    if (p2 == 0 && p3 == 1) a++
                    if (p3 == 0 && p4 == 1) a++
                    if (p4 == 0 && p5 == 1) a++
                    if (p5 == 0 && p6 == 1) a++
                    if (p6 == 0 && p7 == 1) a++
                    if (p7 == 0 && p8 == 1) a++
                    if (p8 == 0 && p9 == 1) a++
                    if (p9 == 0 && p2 == 1) a++

                    if (a == 1 && (p2 * p4 * p8 == 0) && (p2 * p6 * p8 == 0)) {
                        toWhite[count++] = idx
                    }
                }
            }
            if (count > 0) {
                changed = true
                for (i in 0 until count) grid[toWhite[i]] = false
            }

            if (!changed) break
        }

        return grid
    }

    private fun traceSkeletonChains(skeleton: BooleanArray, w: Int, h: Int): List<List<VecPoint>> {
        val visited = BooleanArray(w * h)
        val chains = mutableListOf<List<VecPoint>>()
        val nbx = intArrayOf(0, 1, 0, -1, 1, 1, -1, -1)
        val nby = intArrayOf(-1, 0, 1, 0, -1, 1, 1, -1)

        fun countNeighbors(x: Int, y: Int): Int {
            var c = 0
            for (k in 0 until 8) {
                val nx = x + nbx[k]
                val ny = y + nby[k]
                if (nx in 0 until w && ny in 0 until h && skeleton[ny * w + nx]) c++
            }
            return c
        }

        // Start from endpoints (neighbor count == 1) first, then any remaining skeleton pixels
        for (pass in 0..1) {
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val startIdx = y * w + x
                    if (!skeleton[startIdx] || visited[startIdx]) continue
                    if (pass == 0 && countNeighbors(x, y) != 1) continue

                    val chain = mutableListOf<VecPoint>()
                    var cx = x
                    var cy = y

                    while (true) {
                        val currIdx = cy * w + cx
                        visited[currIdx] = true
                        chain.add(VecPoint(cx + 0.5f, cy + 0.5f))

                        var foundNext = false
                        for (k in 0 until 8) {
                            val nx = cx + nbx[k]
                            val ny = cy + nby[k]
                            if (nx in 0 until w && ny in 0 until h) {
                                val nIdx = ny * w + nx
                                if (skeleton[nIdx] && !visited[nIdx]) {
                                    cx = nx
                                    cy = ny
                                    foundNext = true
                                    break
                                }
                            }
                        }
                        if (!foundNext) break
                    }

                    if (chain.size >= 3) {
                        chains.add(chain)
                    }
                }
            }
        }

        return chains
    }
}
