package com.kryptkode.facedetection.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.toRect

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

    fun cropBitmap(bitmap: Bitmap, rect: Rect, scale: Float = 1f): Bitmap {
        return Bitmap.createBitmap(
            bitmap,
            rect.left,
            rect.top,
            rect.width(),
            rect.height()
        )
    }

    fun Bitmap.mapRect(rect: RectF, viewWidth: Int, viewHeight: Int): Rect {
        val matrix = Matrix()
        val destRect = RectF()
        matrix.setScale(
            width.toFloat() / viewWidth.toFloat(),
            height.toFloat() / viewHeight.toFloat()
        )
        matrix.mapRect(destRect, rect)
        return destRect.toRect()
    }


    /** Holds bitmap instance and the sample size that the bitmap was loaded/cropped with.  */
    data class BitmapSampled(
        /** The bitmap instance  */
        val bitmap: Bitmap,
        /** The sample size used to lower the size of the bitmap (1,2,4,8,...)  */
        val sampleSize: Int
    )
}