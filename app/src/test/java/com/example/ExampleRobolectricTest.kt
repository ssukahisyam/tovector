package com.example

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.example.engine.ExportHelper
import com.example.engine.SampleAssetGenerator
import com.example.engine.VectorTracingEngine
import com.example.model.BatchItemStatus
import com.example.model.NodeSubPoint
import com.example.model.SelectedPathNode
import com.example.model.TraceConfig
import com.example.model.TraceMode
import com.example.model.ZipArchiveOptions
import com.example.ui.components.hitTestNearestBezierNode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VectoForge", appName)
    }

    @Test
    fun `trace all 5 vector modes produces valid SVG paths`() {
        val samples = SampleAssetGenerator.createSampleBatchPack()
        assertEquals(5, samples.size)

        for (mode in TraceMode.entries) {
            val config = TraceConfig.defaultForMode(mode)
            val result = VectorTracingEngine.traceBitmap(samples.first().sourceBitmap, config)
            assertTrue("Mode $mode should produce layers", result.layers.isNotEmpty())
            val svg = result.buildSvgString()
            assertTrue("Mode $mode should output <svg> root tag", svg.contains("<svg"))
            assertTrue("Mode $mode should output <path> elements", svg.contains("<path"))
        }
    }

    @Test
    fun `batch zip export packages selected SVGs and manifest into single archive`() {
        val globalCfg = TraceConfig.defaultForMode(TraceMode.GEOMETRIC_SHARP)
        val rawSamples = SampleAssetGenerator.createSampleBatchPack().take(3)
        val processedItems = rawSamples.mapIndexed { index, item ->
            val cfg = item.effectiveConfig(globalCfg)
            val traceResult = VectorTracingEngine.traceBitmap(item.sourceBitmap, cfg)
            item.copy(
                status = BatchItemStatus.COMPLETED,
                result = traceResult,
                includedInExport = index != 1
            )
        }

        val options = ZipArchiveOptions(
            archiveBaseName = "test_batch_bundle",
            organizeByModeSubfolders = true,
            includeSummaryReport = true,
            onlySelectedItems = true
        )

        val planned = ExportHelper.buildPlannedZipEntries(processedItems, globalCfg, options)
        assertEquals(2, planned.size)

        val baos = ByteArrayOutputStream()
        ExportHelper.writeBatchZipToStream(
            items = processedItems,
            globalConfig = globalCfg,
            outputStream = baos,
            options = options
        )

        val entryNames = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(baos.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertEquals(4, entryNames.size)
        assertTrue(entryNames.contains("vectoforge_batch_report.txt"))
        assertTrue(entryNames.contains("manifest.json"))
        assertTrue(entryNames.count { it.endsWith(".svg") && it.contains("/") } == 2)
    }

    @Test
    fun `node inspector selects shifts converts and deletes bezier path nodes`() {
        val samples = SampleAssetGenerator.createSampleBatchPack()
        val config = TraceConfig.defaultForMode(TraceMode.POTRACE_BW)
        val initialResult = VectorTracingEngine.traceBitmap(samples.first().sourceBitmap, config)

        val editableNodes = initialResult.listEditableNodes()
        assertTrue("Should have editable nodes", editableNodes.size > 3)

        val firstNodeInfo = editableNodes[1]
        val target = SelectedPathNode(
            layerId = firstNodeInfo.layerId,
            commandIndex = firstNodeInfo.commandIndex,
            subPoint = NodeSubPoint.ANCHOR
        )

        // 1. Test Hit-Testing on Canvas at 1x and when Zoomed (4.0x) & Panned
        val hit = hitTestNearestBezierNode(
            tapOffset = Offset(firstNodeInfo.anchor.x, firstNodeInfo.anchor.y),
            canvasWidth = 400f,
            canvasHeight = 400f,
            panOffset = Offset.Zero,
            zoom = 1f,
            originX = 0f,
            originY = 0f,
            fitScale = 1f,
            layers = initialResult.visibleLayers,
            showHandles = true,
            maxHitDistanceScreenPx = 20f
        )
        assertNotNull("Hit test should locate Bezier node at its coordinates", hit)

        val zoomLevel = 4.0f
        val pan = Offset(115f, -72f)
        val originX = 36f
        val originY = 24f
        val fitScale = 1.25f
        val canvasW = 500f
        val canvasH = 500f
        val artboardX = originX + firstNodeInfo.anchor.x * fitScale
        val artboardY = originY + firstNodeInfo.anchor.y * fitScale
        val zoomedScreenTap = Offset(
            x = canvasW * 0.5f + (artboardX - canvasW * 0.5f) * zoomLevel + pan.x,
            y = canvasH * 0.5f + (artboardY - canvasH * 0.5f) * zoomLevel + pan.y
        )
        val zoomedHit = hitTestNearestBezierNode(
            tapOffset = zoomedScreenTap,
            canvasWidth = canvasW,
            canvasHeight = canvasH,
            panOffset = pan,
            zoom = zoomLevel,
            originX = originX,
            originY = originY,
            fitScale = fitScale,
            layers = initialResult.visibleLayers,
            showHandles = true,
            maxHitDistanceScreenPx = 24f
        )
        assertNotNull("Hit test should locate Bezier node when zoomed in 4x and panned", zoomedHit)
        assertEquals(firstNodeInfo.layerId, zoomedHit?.layerId)
        assertEquals(firstNodeInfo.commandIndex, zoomedHit?.commandIndex)

        // 2. Test Shifting Node Coordinates
        val shiftedResult = initialResult.shiftNode(target, dx = 5f, dy = -3f)
        val shiftedNodeInfo = shiftedResult.listEditableNodes().find {
            it.layerId == target.layerId && it.commandIndex == target.commandIndex
        }!!
        assertEquals(
            (firstNodeInfo.anchor.x + 5f).coerceIn(0f, initialResult.width.toFloat()),
            shiftedNodeInfo.anchor.x,
            0.05f
        )
        assertEquals(
            (firstNodeInfo.anchor.y - 3f).coerceIn(0f, initialResult.height.toFloat()),
            shiftedNodeInfo.anchor.y,
            0.05f
        )

        // 3. Test Deleting Node reduces totalNodes by 1 and keeps valid SVG
        val initialTotalNodes = shiftedResult.totalNodes
        val afterDeleteResult = shiftedResult.deleteNode(target)
        assertEquals(initialTotalNodes - 1, afterDeleteResult.totalNodes)
        assertTrue(afterDeleteResult.buildSvgString().contains("<path"))
    }
}
