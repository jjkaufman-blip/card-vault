package com.cardvault.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

object ImageUtils {
    /** Card proportions (2.5" x 3.5"). The camera screen draws a guide with this shape. */
    const val CARD_ASPECT = 2.5f / 3.5f
    /** Fraction of the photo's width the guide covers. Must match the on-screen guide. */
    const val GUIDE_WIDTH_FRACTION = 0.8f

    /** Converts a camera frame to an upright bitmap, cropped to the on-screen card guide. */
    fun toCroppedBitmap(image: ImageProxy): Bitmap {
        var raw = image.toBitmap()
        // Keep only what the on-screen preview showed (the camera captures a wider area).
        val crop = image.cropRect
        if ((raw.width != crop.width() || raw.height != crop.height()) &&
            crop.left >= 0 && crop.top >= 0 && crop.right <= raw.width && crop.bottom <= raw.height &&
            crop.width() > 0 && crop.height() > 0
        ) {
            raw = Bitmap.createBitmap(raw, crop.left, crop.top, crop.width(), crop.height())
        }
        val rotation = image.imageInfo.rotationDegrees
        val upright = if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        } else raw
        return cropToGuide(upright)
    }

    fun cropToGuide(src: Bitmap): Bitmap {
        var w = (src.width * GUIDE_WIDTH_FRACTION).roundToInt()
        var h = (w / CARD_ASPECT).roundToInt()
        if (h > src.height * 0.95f) {
            h = (src.height * 0.95f).roundToInt()
            w = (h * CARD_ASPECT).roundToInt()
        }
        val x = (src.width - w) / 2
        val y = (src.height - h) / 2
        return Bitmap.createBitmap(src, x, y, w, h)
    }

    /** Saves a photo at a sensible size (about 300-500 KB) and returns its path. */
    fun save(bitmap: Bitmap, dir: File, maxSide: Int = 1600): String {
        val scale = maxSide.toFloat() / max(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt(),
                (bitmap.height * scale).roundToInt(),
                true,
            )
        } else bitmap
        val file = File(dir, "card_${UUID.randomUUID()}.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return file.absolutePath
    }
}

/** Loads a photo picked from the gallery, upright and no larger than [maxSide]. */
fun loadPhoto(context: android.content.Context, uri: android.net.Uri, maxSide: Int = 2000): Bitmap? {
    val resolver = context.contentResolver
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) } ?: return null
    val rotation = resolver.openInputStream(uri)?.use {
        when (androidx.exifinterface.media.ExifInterface(it).getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
        )) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } ?: 0
    if (rotation == 0) return bmp
    val m = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}
