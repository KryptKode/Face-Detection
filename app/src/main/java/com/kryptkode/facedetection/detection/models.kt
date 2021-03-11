package com.kryptkode.facedetection.detection

import android.media.Image
import android.util.Size
import androidx.camera.core.ImageProxy

data class Frame(
    @Suppress("ArrayInDataClass") val data: ImageProxy,
    val rotation: Int,
    val size: Size,
    val format: Int,
    val lensFacing: LensFacing
)