package com.kryptkode.facedetection.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect

object BitmapUtils {

    fun cropBitmapHandleOOMs(bitmap: Bitmap, rect: Rect): BitmapSampled {
        var scale = 1
        while (true) {
            try {
                val cropBitmap = cropBitmap(
                    bitmap,
                    rect,
                    1 / scale.toFloat(),
                )
                return BitmapSampled(cropBitmap, scale)
            } catch (e: OutOfMemoryError) {
                scale *= 2
                if (scale > 8) {
                    throw e
                }
            }
        }
    }

    fun cropBitmap(bitmap: Bitmap, rect: Rect, scale: Float): Bitmap {
        val matrix = Matrix()
        matrix.postScale(-scale, scale) //required to flip the image horizontally
        return Bitmap.createBitmap(
            bitmap,
            rect.left,
            rect.top,
            rect.width(),
            rect.height(),
            matrix,
            true
        )
    }

    // region: Inner class: BitmapSampled
    /** Holds bitmap instance and the sample size that the bitmap was loaded/cropped with.  */
    class BitmapSampled(
        /** The bitmap instance  */
        val bitmap: Bitmap,
        /** The sample size used to lower the size of the bitmap (1,2,4,8,...)  */
        val sampleSize: Int
    )
}