package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.SampleAssetGenerator
import com.example.model.TraceMode
import com.example.ui.theme.CyanVectorPrimary
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StagedGalleryImage(
    val id: String,
    val title: String,
    val bitmap: Bitmap,
    val isFromDevice: Boolean,
    val isSelected: Boolean = true
)

/**
 * Minimalist Multi-Image Batch Gallery Component.
 * Combines zero-permission Android Photo Picker, Document Storage Picker,
 * and an interactive multi-select staging grid with numbered selection badges
 * and preset assignment before importing into the batch queue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimalBatchGallerySheet(
    globalMode: TraceMode,
    onDismiss: () -> Unit,
    onConfirmImport: (List<Pair<String, Bitmap>>, TraceMode?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Pre-populate staging grid with sample assets + any device images picked by user
    val stagedItems = remember {
        val initialSamples = SampleAssetGenerator.createSampleBatchPack().map { sample ->
            StagedGalleryImage(
                id = "staged_${sample.id}",
                title = sample.title,
                bitmap = sample.sourceBitmap,
                isFromDevice = false,
                isSelected = false
            )
        }
        mutableStateListOf<StagedGalleryImage>().apply { addAll(initialSamples) }
    }

    var isDecoding by remember { mutableStateOf(false) }
    // null = Follow Global Preset, non-null = assign specific TraceMode override to imported batch
    var assignedModeOverride by remember { mutableStateOf<TraceMode?>(null) }

    val handlePickedUris: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isDecoding = true
                val decoded = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri -> decodeUriToStagedImage(context, uri) }
                }
                stagedItems.addAll(0, decoded)
                isDecoding = false
            }
        }
    }

    // 1. Android Photo Picker (Multiple Visual Media)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25),
        onResult = handlePickedUris
    )

    // 2. Storage Document Browser (PNG, JPG, WebP from Files/Drive/Downloads)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = handlePickedUris
    )

    val selectedItems = stagedItems.filter { it.isSelected }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("minimal_batch_gallery_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Galeri Impor Batch",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Pilih banyak gambar dari perangkat untuk dikonversi sekaligus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("close_gallery_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup Galeri",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Device Storage Action Buttons (Photo Gallery vs File Manager)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("gallery_pick_photos_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Galeri Foto",
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Galeri Foto")
                }

                OutlinedButton(
                    onClick = {
                        documentPickerLauncher.launch(arrayOf("image/*"))
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("gallery_browse_files_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "File Penyimpanan",
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Folder File")
                }
            }

            // Selection Controls Bar (Select All / Deselect All)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${selectedItems.size} dipilih dari ${stagedItems.size} gambar",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selectedItems.isNotEmpty()) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (isDecoding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.8.dp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val allSelected = stagedItems.isNotEmpty() && stagedItems.all { it.isSelected }
                    TextButton(
                        onClick = {
                            val targetState = !allSelected
                            for (i in stagedItems.indices) {
                                stagedItems[i] = stagedItems[i].copy(isSelected = targetState)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("gallery_select_all_button")
                    ) {
                        Text(
                            text = if (allSelected) "Batal Pilih Semua" else "Pilih Semua",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // 3-Column Minimalist Multi-Select Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 310.dp)
                    .testTag("gallery_staging_grid"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(stagedItems, key = { _, item -> item.id }) { index, item ->
                    val selectionOrder = if (item.isSelected) {
                        selectedItems.indexOfFirst { it.id == item.id } + 1
                    } else {
                        0
                    }

                    MinimalGalleryGridCell(
                        item = item,
                        selectionNumber = selectionOrder,
                        onToggleSelect = {
                            stagedItems[index] = item.copy(isSelected = !item.isSelected)
                        }
                    )
                }
            }

            // Optional Batch Mode Assignment Strip before importing
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Terapkan Mode ke Gambar Terpilih",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ModeAssignChip(
                        label = "Ikuti Global (${globalMode.badge})",
                        selected = assignedModeOverride == null,
                        onClick = { assignedModeOverride = null }
                    )
                    TraceMode.entries.forEach { mode ->
                        ModeAssignChip(
                            label = mode.title,
                            selected = assignedModeOverride == mode,
                            onClick = { assignedModeOverride = mode }
                        )
                    }
                }
            }

            // Confirm Batch Import CTA
            Button(
                onClick = {
                    val toImport = selectedItems.map { it.title to it.bitmap }
                    onConfirmImport(toImport, assignedModeOverride)
                    onDismiss()
                },
                enabled = selectedItems.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("confirm_batch_import_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Impor Batch",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedItems.isEmpty()) {
                        "Pilih Gambar untuk Diimpor"
                    } else {
                        "Impor & Proses ${selectedItems.size} Gambar"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun MinimalGalleryGridCell(
    item: StagedGalleryImage,
    selectionNumber: Int,
    onToggleSelect: () -> Unit
) {
    val isSelected = selectionNumber > 0
    val imageBitmap = remember(item.bitmap) { item.bitmap.asImageBitmap() }
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        label = "cellBorder"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(
                width = if (isSelected) 2.dp else 0.8.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onToggleSelect)
            .testTag("gallery_cell_${item.id}")
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = item.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        )

        // Subtle dark scrim when selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.08f))
            )
        }

        // Top-Left Source Tag (Perangkat vs Sampel)
        if (item.isFromDevice) {
            Surface(
                color = Color(0xCC0A0B0E),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = "LOKAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanVectorPrimary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        // Top-Right Numbered Multi-Select Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(22.dp)
                .background(
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color(0x990A0B0E)
                    },
                    shape = CircleShape
                )
                .border(
                    width = 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Text(
                    text = "$selectionNumber",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // Bottom Title & Dimensions Bar
        Surface(
            color = Color(0xCC0A0B0E),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${item.bitmap.width}×${item.bitmap.height}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun ModeAssignChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .border(
                0.8.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(18.dp)
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

fun decodeUriToStagedImage(context: Context, uri: Uri): StagedGalleryImage? {
    return try {
        var displayName = "Gambar_Perangkat"
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

        StagedGalleryImage(
            id = "device_${UUID.randomUUID().toString().take(8)}",
            title = displayName.substringBeforeLast('.'),
            bitmap = bmp,
            isFromDevice = true,
            isSelected = true
        )
    } catch (_: Exception) {
        null
    }
}
