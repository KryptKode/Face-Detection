package com.kryptkode.facedetection.detection

import android.annotation.SuppressLint
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class FaceDetectionImageAnalysis(
    private val faceDetector: FaceDetector
) : ImageAnalysis.Analyzer {

    @SuppressLint("RestrictedApi", "UnsafeExperimentalUsageError")
    override fun analyze(image: ImageProxy) {
        Log.e(TAG, "analyze: $image" )
        faceDetector.process(
            Frame(
                data = image,
                rotation = image.imageInfo.rotationDegrees,
                size = Size(image.width, image.height),
                format = image.format,
                lensFacing = LensFacing.FRONT
            )
        )
    }

    companion object {
        private const val TAG = "FaceDetectionImageAnaly"
    }
}