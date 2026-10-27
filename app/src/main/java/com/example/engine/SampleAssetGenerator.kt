package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.model.BatchImageItem
import com.example.model.TraceConfig
import com.example.model.TraceMode
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object SampleAssetGenerator {

    fun createSampleBatchPack(): List<BatchImageItem> {
        return listOf(
            BatchImageItem(
                id = "sample_geometric_logo",
                title = "HexaCore Tech Badge",
                subtitle = "Bentuk simpel dengan anti-aliasing & noise (Uji Mode Anti-Mbulet)",
                sourceBitmap = createHexaCoreLogoBitmap(),
                useGlobalConfig = false,
                customConfig = TraceConfig.defaultForMode(TraceMode.GEOMETRIC_SHARP),
                recommendedMode = TraceMode.GEOMETRIC_SHARP
            ),
            BatchImageItem(
                id = "sample_potrace_crest",
                title = "Monstera Leaf Emblem",
                subtitle = "Siluet organik lengkung halus (Uji Mode Potrace B/W)",
                sourceBitmap = createOrganicCrestBitmap(),
                useGlobalConfig = true,
                customConfig = TraceConfig.defaultForMode(TraceMode.POTRACE_BW),
                recommendedMode = TraceMode.POTRACE_BW
            ),
            BatchImageItem(
                id = "sample_vtracer_poster",
                title = "Nordic Sunset Poster",
                subtitle = "Ilustrasi 5 warna datar berlapis (Uji Mode VTracer Multicolor)",
                sourceBitmap = createMulticolorPosterBitmap(),
                useGlobalConfig = false,
                customConfig = TraceConfig.defaultForMode(TraceMode.VTRACER_MULTICOLOR),
                recommendedMode = TraceMode.VTRACER_MULTICOLOR
            ),
            BatchImageItem(
                id = "sample_pixel_art",
                title = "Cyber Blade 16-Bit",
                subtitle = "Sprite pixel art tajam tanpa kurva melengkung (Uji Mode Pixel Polygon)",
                sourceBitmap = createPixelArtSwordBitmap(),
                useGlobalConfig = false,
                customConfig = TraceConfig.defaultForMode(TraceMode.PIXEL_POLYGON),
                recommendedMode = TraceMode.PIXEL_POLYGON
            ),
            BatchImageItem(
                id = "sample_centerline_sketch",
                title = "Isometric CAD Sketch",
                subtitle = "Garis sketsa arsitektur untuk stroke tunggal (Uji Mode Centerline)",
                sourceBitmap = createCenterlineSketchBitmap(),
                useGlobalConfig = false,
                customConfig = TraceConfig.defaultForMode(TraceMode.CENTERLINE_SKETCH),
                recommendedMode = TraceMode.CENTERLINE_SKETCH
            )
        )
    }

    /**
     * Generates a simple geometric logo that intentionally contains mild edge blur
     * and stray compression speckles to demonstrate how Anti-Mbulet mode keeps straight
     * lines and corners razor-sharp while cleaning speckles.
     */
    private fun createHexaCoreLogoBitmap(): Bitmap {
        val size = 240
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 24, 38)
            style = Paint.Style.FILL
        }

        // Outer hexagon with inner geometric cutout and lightning bolt
        val cx = size * 0.5f
        val cy = size * 0.5f
        val hexPath = Path()
        val rOut = 92f
        for (i in 0..5) {
            val angle = Math.toRadians((60.0 * i) - 30.0)
            val x = cx + rOut * cos(angle).toFloat()
            val y = cy + rOut * sin(angle).toFloat()
            if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
        }
        hexPath.close()
        canvas.drawPath(hexPath, paint)

        // Inner white cutout square rotated / geometric bars
        paint.color = Color.WHITE
        canvas.drawRect(cx - 46f, cy - 46f, cx + 46f, cy + 46f, paint)

        // Central sharp arrow / chevron
        paint.color = Color.rgb(18, 24, 38)
        val arrow = Path().apply {
            moveTo(cx, cy - 32f)
            lineTo(cx + 30f, cy + 24f)
            lineTo(cx + 10f, cy + 24f)
            lineTo(cx, cy + 4f)
            lineTo(cx - 10f, cy + 24f)
            lineTo(cx - 30f, cy + 24f)
            close()
        }
        canvas.drawPath(arrow, paint)

        // Add intentional small compression speckles ("turds") that naive tracing turns into tangled nodes
        val rng = Random(42)
        for (i in 0 until 18) {
            val sx = rng.nextInt(12, size - 12).toFloat()
            val sy = rng.nextInt(12, size - 12).toFloat()
            if (sx < 38f || sx > size - 38f || sy < 38f || sy > size - 38f) {
                canvas.drawRect(sx, sy, sx + 2f, sy + 2f, paint)
            }
        }

        return bmp
    }

    private fun createOrganicCrestBitmap(): Bitmap {
        val size = 240
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            style = Paint.Style.FILL
        }

        val cx = size * 0.5f
        val cy = size * 0.52f

        // Smooth botanical emblem
        val leaf = Path().apply {
            moveTo(cx, cy - 88f)
            cubicTo(cx + 75f, cy - 65f, cx + 92f, cy + 15f, cx, cy + 84f)
            cubicTo(cx - 92f, cy + 15f, cx - 75f, cy - 65f, cx, cy - 88f)
            close()
        }
        canvas.drawPath(leaf, paint)

        // Cutout leaf ribs
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(cx - 4f, cy - 55f, cx + 4f, cy + 88f), 4f, 4f, paint)
        canvas.drawOval(RectF(cx - 65f, cy - 32f, cx - 14f, cy - 14f), paint)
        canvas.drawOval(RectF(cx + 14f, cy - 32f, cx + 65f, cy - 14f), paint)
        canvas.drawOval(RectF(cx - 58f, cy + 6f, cx - 14f, cy + 24f), paint)
        canvas.drawOval(RectF(cx + 14f, cy + 6f, cx + 58f, cy + 24f), paint)

        return bmp
    }

    private fun createMulticolorPosterBitmap(): Bitmap {
        val size = 240
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        // Background sky
        paint.color = Color.rgb(254, 243, 199)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        // Warm sunset sun disc
        paint.color = Color.rgb(249, 115, 22)
        canvas.drawCircle(size * 0.5f, size * 0.42f, 58f, paint)

        // Back mountain range (Teal)
        paint.color = Color.rgb(13, 148, 136)
        val mtnBack = Path().apply {
            moveTo(0f, size * 0.72f)
            lineTo(size * 0.32f, size * 0.38f)
            lineTo(size * 0.62f, size * 0.64f)
            lineTo(size * 0.82f, size * 0.42f)
            lineTo(size.toFloat(), size * 0.65f)
            lineTo(size.toFloat(), size.toFloat())
            lineTo(0f, size.toFloat())
            close()
        }
        canvas.drawPath(mtnBack, paint)

        // Front mountain silhouette (Deep Navy)
        paint.color = Color.rgb(30, 41, 59)
        val mtnFront = Path().apply {
            moveTo(0f, size * 0.80f)
            lineTo(size * 0.25f, size * 0.58f)
            lineTo(size * 0.55f, size * 0.76f)
            lineTo(size * 0.78f, size * 0.54f)
            lineTo(size.toFloat(), size * 0.78f)
            lineTo(size.toFloat(), size.toFloat())
            lineTo(0f, size.toFloat())
            close()
        }
        canvas.drawPath(mtnFront, paint)

        return bmp
    }

    private fun createPixelArtSwordBitmap(): Bitmap {
        val grid = 16
        val cell = 14
        val size = grid * cell
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            isAntiAlias = false
            style = Paint.Style.FILL
        }

        // 0 = white, 1 = dark outline, 2 = cyan blade, 3 = amber hilt
        val rows = arrayOf(
            "0000000000001110",
            "0000000000012210",
            "0000000000122210",
            "0000000001222100",
            "0000000012221000",
            "0000000122210000",
            "0001101222100000",
            "0001312221000000",
            "0000133210000000",
            "0000133310000000",
            "0001313331000000",
            "0013101131000000",
            "0131000011000000",
            "1310000000000000",
            "1100000000000000",
            "0000000000000000"
        )

        for (y in 0 until grid) {
            for (x in 0 until grid) {
                val ch = rows[y][x]
                if (ch == '0') continue
                paint.color = when (ch) {
                    '1' -> Color.rgb(15, 23, 42)
                    '2' -> Color.rgb(0, 210, 255)
                    '3' -> Color.rgb(255, 109, 0)
                    else -> Color.WHITE
                }
                canvas.drawRect(
                    (x * cell).toFloat(),
                    (y * cell).toFloat(),
                    ((x + 1) * cell).toFloat(),
                    ((y + 1) * cell).toFloat(),
                    paint
                )
            }
        }
        return bmp
    }

    private fun createCenterlineSketchBitmap(): Bitmap {
        val size = 240
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 28, 44)
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Draw an isometric cube + wave sketch line
        val cx = size * 0.5f
        val cy = size * 0.48f
        val r = 68f

        val topX = cx
        val topY = cy - r
        val rightX = cx + r * 0.86f
        val rightY = cy - r * 0.5f
        val botRightX = cx + r * 0.86f
        val botRightY = cy + r * 0.5f
        val botX = cx
        val botY = cy + r
        val botLeftX = cx - r * 0.86f
        val botLeftY = cy + r * 0.5f
        val leftX = cx - r * 0.86f
        val leftY = cy - r * 0.5f

        val hex = Path().apply {
            moveTo(topX, topY)
            lineTo(rightX, rightY)
            lineTo(botRightX, botRightY)
            lineTo(botX, botY)
            lineTo(botLeftX, botLeftY)
            lineTo(leftX, leftY)
            close()
        }
        canvas.drawPath(hex, strokePaint)

        canvas.drawLine(cx, cy, topX, topY, strokePaint)
        canvas.drawLine(cx, cy, botRightX, botRightY, strokePaint)
        canvas.drawLine(cx, cy, botLeftX, botLeftY, strokePaint)

        return bmp
    }
}
