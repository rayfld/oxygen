package app.oxygen.data
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import app.oxygen.core.model.Attachment
import app.oxygen.core.util.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
class AttachmentStore(private val context: Context) {
    private val dir: File by lazy {
        File(context.filesDir, "attachments").apply { mkdirs() }
    }
    suspend fun importImage(uri: Uri): Attachment? = withContext(Dispatchers.IO) {
        runCatching {
            val id = newId()
            val bitmap = decodeScaled(uri) ?: return@runCatching null
            val file = File(dir, "$id.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            bitmap.recycle()
            Attachment(
                id = id,
                kind = Attachment.Kind.IMAGE,
                uri = file.absolutePath,
                mimeType = "image/jpeg",
                name = null,
            )
        }.getOrNull()
    }
    suspend fun importPdf(uri: Uri): Attachment? = withContext(Dispatchers.IO) {
        runCatching {
            val id = newId()
            val name = queryDisplayName(uri) ?: "document.pdf"
            val file = File(dir, "$id.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            Attachment(
                id = id,
                kind = Attachment.Kind.PDF,
                uri = file.absolutePath,
                mimeType = "application/pdf",
                name = name,
            )
        }.getOrNull()
    }
    suspend fun toDataUrl(attachment: Attachment): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(attachment.uri)
            if (!file.exists()) return@runCatching null
            if (file.length() > 32L * 1024 * 1024) return@runCatching null
            val mime = when (attachment.kind) {
                Attachment.Kind.IMAGE -> "image/jpeg"
                Attachment.Kind.PDF -> "application/pdf"
            }
            val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            "data:$mime;base64,$encoded"
        }.getOrNull()
    }
    fun delete(attachment: Attachment) {
        runCatching { File(attachment.uri).delete() }
    }
    suspend fun saveToGallery(attachment: Attachment): Boolean = withContext(Dispatchers.IO) {
        if (!canSaveToGallery()) return@withContext false
        runCatching {
            val file = File(attachment.uri)
            if (!file.exists()) return@runCatching false
            val values = android.content.ContentValues().apply {
                put(
                    android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                    "oxygen_${System.currentTimeMillis()}.${file.extension.ifBlank { "jpg" }}",
                )
                put(
                    android.provider.MediaStore.Images.Media.MIME_TYPE,
                    attachment.mimeType.takeIf { it.startsWith("image/") } ?: "image/jpeg",
                )
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Oxygen")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver
                .insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
            values.clear()
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            true
        }.getOrDefault(false)
    }
    suspend fun attachmentFromUrl(url: String): Attachment? = withContext(Dispatchers.IO) {
        runCatching {
            if (!url.startsWith("data:")) {
                return@runCatching Attachment(
                    id = newId(),
                    kind = Attachment.Kind.IMAGE,
                    uri = url,
                    mimeType = "image/*",
                    name = null,
                )
            }
            val header = url.substringAfter("data:").substringBefore(",")
            val mime = header.substringBefore(";").ifBlank { "image/png" }
            val payload = url.substringAfter(',', "")
            if (payload.isBlank()) return@runCatching null
            val bytes = Base64.decode(payload, Base64.DEFAULT)
            if (bytes.isEmpty() || bytes.size > 24 * 1024 * 1024) return@runCatching null
            val extension = when {
                mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                mime.contains("webp") -> "webp"
                mime.contains("gif") -> "gif"
                else -> "png"
            }
            val id = newId()
            val file = File(dir, "$id.$extension")
            file.writeBytes(bytes)
            Attachment(
                id = id,
                kind = Attachment.Kind.IMAGE,
                uri = file.absolutePath,
                mimeType = mime,
                name = null,
            )
        }.getOrNull()
    }
    private fun decodeScaled(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val largest = max(width, height)
                if (largest > MAX_IMAGE_DIMENSION) {
                    val scale = MAX_IMAGE_DIMENSION.toFloat() / largest
                    decoder.setTargetSize(
                        (width * scale).roundToInt().coerceAtLeast(1),
                        (height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_DIMENSION) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }
    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
    private companion object {
        const val MAX_IMAGE_DIMENSION = 1568
    }
    fun canSaveToGallery(): Boolean = android.os.Build.VERSION.SDK_INT >= 29
}
