package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.model.BatchImageItem
import com.example.model.TraceConfig
import com.example.model.ZipArchiveOptions
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ZipPlannedEntry(
    val itemId: String,
    val title: String,
    val zipPath: String,
    val modeTitle: String,
    val isCustomOverride: Boolean,
    val nodeCount: Int,
    val reductionPercent: Int,
    val layerCount: Int,
    val svgSizeBytes: Int
)

object ExportHelper {

    fun sanitizeFileName(title: String): String {
        return title.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "vector_output" }
    }

    fun buildPlannedZipEntries(
        items: List<BatchImageItem>,
        globalConfig: TraceConfig,
        options: ZipArchiveOptions = ZipArchiveOptions()
    ): List<ZipPlannedEntry> {
        val eligible = items.filter { item ->
            item.result != null && (!options.onlySelectedItems || item.includedInExport)
        }
        val usedPaths = mutableSetOf<String>()
        val entries = mutableListOf<ZipPlannedEntry>()

        for ((index, item) in eligible.withIndex()) {
            val res = item.result ?: continue
            val cfg = item.effectiveConfig(globalConfig)
            val folderPrefix = if (options.organizeByModeSubfolders) {
                "${cfg.mode.id}/"
            } else {
                ""
            }
            var baseName = sanitizeFileName(item.title)
            var candidatePath = "$folderPrefix$baseName.svg"
            if (!usedPaths.add(candidatePath)) {
                baseName = "${baseName}_${index + 1}"
                candidatePath = "$folderPrefix$baseName.svg"
                usedPaths.add(candidatePath)
            }

            entries.add(
                ZipPlannedEntry(
                    itemId = item.id,
                    title = item.title,
                    zipPath = candidatePath,
                    modeTitle = cfg.mode.title,
                    isCustomOverride = !item.useGlobalConfig,
                    nodeCount = res.totalNodes,
                    reductionPercent = res.reductionPercent,
                    layerCount = res.visibleLayers.size,
                    svgSizeBytes = res.estimatedSvgBytes
                )
            )
        }
        return entries
    }

    fun writeSingleSvgToStream(svgContent: String, outputStream: OutputStream) {
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(svgContent)
            writer.flush()
        }
    }

    fun writeBatchZipToStream(
        items: List<BatchImageItem>,
        globalConfig: TraceConfig,
        outputStream: OutputStream,
        options: ZipArchiveOptions = ZipArchiveOptions()
    ) {
        val eligible = items.filter { item ->
            item.result != null && (!options.onlySelectedItems || item.includedInExport)
        }

        ZipOutputStream(outputStream.buffered()).use { zip ->
            val reportSb = StringBuilder()
            reportSb.appendLine("==========================================================")
            reportSb.appendLine("  VECTOFORGE STUDIO - LAPORAN PEMROSESAN BATCH VEKTOR SVG ")
            reportSb.appendLine("==========================================================")
            reportSb.appendLine("Total File Diekspor : ${eligible.size} dari ${items.size} antrean")
            reportSb.appendLine("Preset Global       : ${globalConfig.mode.title}")
            reportSb.appendLine("Struktur Subfolder  : ${if (options.organizeByModeSubfolders) "Per Algoritma Mode" else "Flat (Root)"}")
            reportSb.appendLine("----------------------------------------------------------")

            val jsonManifestSb = StringBuilder()
            jsonManifestSb.appendLine("{")
            jsonManifestSb.appendLine("  \"generator\": \"VectoForge Studio\",")
            jsonManifestSb.appendLine("  \"globalPreset\": \"${globalConfig.mode.id}\",")
            jsonManifestSb.appendLine("  \"exportedFileCount\": ${eligible.size},")
            jsonManifestSb.appendLine("  \"files\": [")

            val usedPaths = mutableSetOf<String>()
            var totalBytesWritten = 0

            for ((index, item) in eligible.withIndex()) {
                val res = item.result ?: continue
                val cfg = item.effectiveConfig(globalConfig)
                val folderPrefix = if (options.organizeByModeSubfolders) {
                    "${cfg.mode.id}/"
                } else {
                    ""
                }

                var baseName = sanitizeFileName(item.title)
                var zipEntryPath = "$folderPrefix$baseName.svg"
                if (!usedPaths.add(zipEntryPath)) {
                    baseName = "${baseName}_${index + 1}"
                    zipEntryPath = "$folderPrefix$baseName.svg"
                    usedPaths.add(zipEntryPath)
                }

                val entry = ZipEntry(zipEntryPath)
                zip.putNextEntry(entry)
                val svgBytes = res.buildSvgString().toByteArray(Charsets.UTF_8)
                zip.write(svgBytes)
                zip.closeEntry()
                totalBytesWritten += svgBytes.size

                reportSb.appendLine(
                    String.format(
                        Locale.US,
                        "%d. %-34s | Mode: %-22s | Layers: %2d | Nodes: %4d (-%d%%) | Ukuran: %.1f KB | Waktu: %dms",
                        index + 1,
                        zipEntryPath,
                        cfg.mode.title + if (item.useGlobalConfig) " (Global)" else " (Custom)",
                        res.visibleLayers.size,
                        res.totalNodes,
                        res.reductionPercent,
                        svgBytes.size / 1024f,
                        res.processingTimeMs
                    )
                )

                val comma = if (index < eligible.lastIndex) "," else ""
                jsonManifestSb.appendLine(
                    "    {\"path\": \"$zipEntryPath\", \"mode\": \"${cfg.mode.id}\", \"customOverride\": ${!item.useGlobalConfig}, \"layers\": ${res.visibleLayers.size}, \"nodes\": ${res.totalNodes}, \"reductionPercent\": ${res.reductionPercent}, \"sizeBytes\": ${svgBytes.size}}$comma"
                )
            }

            jsonManifestSb.appendLine("  ],")
            jsonManifestSb.appendLine("  \"totalSvgBytes\": $totalBytesWritten")
            jsonManifestSb.appendLine("}")

            if (options.includeSummaryReport) {
                val reportEntry = ZipEntry("vectoforge_batch_report.txt")
                zip.putNextEntry(reportEntry)
                zip.write(reportSb.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                val manifestEntry = ZipEntry("manifest.json")
                zip.putNextEntry(manifestEntry)
                zip.write(jsonManifestSb.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            zip.finish()
        }
    }

    fun shareSingleSvg(context: Context, title: String, svgContent: String) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "${sanitizeFileName(title)}.svg"
        val file = File(exportDir, fileName)
        FileOutputStream(file).use { writeSingleSvgToStream(svgContent, it) }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/svg+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "$title.svg")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, "Bagikan File Vektor SVG")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun shareBatchZip(
        context: Context,
        items: List<BatchImageItem>,
        globalConfig: TraceConfig,
        options: ZipArchiveOptions = ZipArchiveOptions()
    ) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val baseArchiveName = sanitizeFileName(options.archiveBaseName).ifEmpty { "vectoforge_batch_vectors" }
        val file = File(exportDir, "$baseArchiveName.zip")
        FileOutputStream(file).use { writeBatchZipToStream(items, globalConfig, it, options) }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "$baseArchiveName.zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, "Bagikan Paket ZIP Batch SVG")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
