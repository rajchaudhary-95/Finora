package com.example.finora.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File

/**
 * Utility functions for safely decoding and downscaling receipt images to avoid OutOfMemoryErrors.
 */
object ImageUtil {

    /**
     * Calculates the optimal inSampleSize factor for downsampling a bitmap to fit the requested dimensions.
     * Keeps memory consumption minimal while preserving aspect ratio.
     */
    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than or equal to the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * Decodes a bitmap from a file path using downsampling and corrects any EXIF rotation.
     * Returns null if the file does not exist, is empty, or cannot be decoded.
     */
    fun decodeSampledBitmapFromFile(filePath: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            return null
        }

        return try {
            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null
            }

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false
            val decodedBitmap = BitmapFactory.decodeFile(filePath, options) ?: return null

            // Correct rotation based on EXIF tag if needed
            rotateBitmapIfRequired(decodedBitmap, filePath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Checks EXIF orientation tag and rotates the decoded bitmap if necessary.
     */
    private fun rotateBitmapIfRequired(bitmap: Bitmap, filePath: String): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val angle = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (angle != 0f) {
                val matrix = Matrix().apply { postRotate(angle) }
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    matrix,
                    true
                )
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                }
                rotatedBitmap
            } else {
                bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }
}
