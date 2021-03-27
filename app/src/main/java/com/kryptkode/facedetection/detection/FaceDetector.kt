package com.kryptkode.facedetection.detection

import android.graphics.Rect
import android.graphics.RectF
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.annotation.GuardedBy
import com.google.android.gms.common.util.concurrent.HandlerExecutor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceDetector(
    private val facePositionView: FacePositionView
) {

    private val mlkitFaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(MIN_FACE_SIZE)
            .enableTracking()
            .build()
    )

    private val detectedFaces = mutableMapOf<Int, String>()
    private var smiled = false

    /** Listener that gets notified when a face detection result is ready. */
    private var listener: FaceDetectionResultListener? = null

    /** [Executor] used to run the face detection on a background thread.  */
    private lateinit var faceDetectionExecutor: ExecutorService

    /** [Executor] used to trigger the rendering of the detected face bounds on the UI thread. */
    private val mainExecutor = HandlerExecutor(Looper.getMainLooper())

    /** Controls access to [isProcessing], since it can be accessed from different threads. */
    private val lock = Object()

    @GuardedBy("lock")
    private var isProcessing = false
    var faceBounds = Rect()

    init {
        facePositionView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View?) {
                faceDetectionExecutor = Executors.newSingleThreadExecutor()
            }

            override fun onViewDetachedFromWindow(view: View?) {
                if (::faceDetectionExecutor.isInitialized) {
                    faceDetectionExecutor.shutdown()
                }
            }
        })
    }

    /** Sets a listener to receive face detection result callbacks. */
    fun setonFaceDetectionFailureListener(listener: FaceDetectionResultListener) {
        this.listener = listener
    }

    /**
     * Kick-starts a face detection operation on a camera frame. If a previous face detection
     * operation is still ongoing, the frame is dropped until the face detector is no longer busy.
     */
    fun process(frame: Frame) {
        synchronized(lock) {
            if (!isProcessing) {
                isProcessing = true
                if (!::faceDetectionExecutor.isInitialized) {
                    val exception =
                        IllegalStateException(
                            "Cannot run face detection. Make sure the face " +
                                    "bounds overlay is attached to the current window."
                        )
                    onError(exception)
                } else {
                    faceDetectionExecutor.execute { frame.detectFaces() }
                }
            }
        }
    }

    private fun Frame.detectFaces() {
        val data = data ?: return
        val inputImage = InputImage.fromByteArray(data, size.width, size.height, rotation, format)
        mlkitFaceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                synchronized(lock) {
                    isProcessing = false
                }

                // Correct the detected faces so that they're correctly rendered on the UI, then
                // pass them to [faceBoundsOverlay] to be drawn.
                val faceBounds = faces.map { face -> face.toFaceBounds(this) }
                mainExecutor.execute {
                    facePositionView.updateFaces(faceBounds)
                    if (facePositionView.isWithinBounds()) {
                        faces.detectBlinking()
                    } else {
                        smiled = false
                        detectedFaces.clear()
                    }
                }
            }
            .addOnFailureListener { exception ->
                synchronized(lock) {
                    isProcessing = false
                }
                onError(exception)
            }
    }

    /**
     * Converts a [Face] to an instance of [FaceBounds] while correctly transforming the face's
     * bounding box by scaling it to match the overlay and mirroring it when the lens facing
     * represents the front facing camera.
     */
    private fun Face.toFaceBounds(frame: Frame): FaceBounds {
        // In order to correctly display the face bounds, the orientation of the processed image
        // (frame) and that of the overlay have to match. Which is why the dimensions of
        // the analyzed image are reversed if its rotation is 90 or 270.
        val reverseDimens = frame.rotation == 90 || frame.rotation == 270
        val width = if (reverseDimens) frame.size.height else frame.size.width
        val height = if (reverseDimens) frame.size.width else frame.size.height

        // Since the analyzed image (frame) probably has a different resolution (width and height)
        // compared to the overlay view, we compute by how much we have to scale the bounding box
        // so that it is displayed correctly on the overlay.

        val scaleX = facePositionView.width.toFloat() / width
        val scaleY = facePositionView.height.toFloat() / height

        // If the front camera lens is being used, reverse the right/left coordinates
        val isFrontLens = frame.lensFacing == LensFacing.FRONT
        val flippedLeft = if (isFrontLens) width - boundingBox.right else boundingBox.left
        val flippedRight = if (isFrontLens) width - boundingBox.left else boundingBox.right

        // Scale all coordinates to match the overlay
        val scaledLeft = scaleX * flippedLeft
        val scaledTop = scaleY * boundingBox.top
        val scaledRight = scaleX * flippedRight
        val scaledBottom = scaleY * boundingBox.bottom
        val scaledBoundingBox = RectF(scaledLeft, scaledTop, scaledRight, scaledBottom)

        // Return the scaled bounding box and a tracking id of the detected face. The tracking id
        // remains the same as long as the same face continues to be detected.
        return FaceBounds(
            trackingId,
            scaledBoundingBox
        )
    }

    private fun onError(exception: Exception) {
        listener?.onFailure(exception)
        Log.e(TAG, "An error occurred while running a face detection", exception)
    }

    private fun List<Face>.detectBlinking() {
        forEach { face ->
            face.detectBlinkingThenTakePicture()
        }
    }

    private fun Face.isLeftEyeOpen(): Boolean {
        val probability = leftEyeOpenProbability
        return (probability != null &&
                probability >= MAXIMUM_EYE_OPEN_CONFIDENCE)
    }

    private fun Face.isLeftEyeClosed(): Boolean {
        val probability = leftEyeOpenProbability
        return (probability != null &&
                probability <= MINIMUM_EYE_OPEN_CONFIDENCE)
    }

    private fun Face.isRightEyeOpen(): Boolean {
        val probability = rightEyeOpenProbability
        return (probability != null &&
                probability >= MAXIMUM_EYE_OPEN_CONFIDENCE)
    }

    private fun Face.isRightEyeClosed(): Boolean {
        val probability = rightEyeOpenProbability
        return (probability != null &&
                probability <= MINIMUM_EYE_OPEN_CONFIDENCE)
    }

    private fun Face.areEyesOpen(): Boolean {
        return isLeftEyeOpen() && isRightEyeOpen()
    }

    private fun Face.areEyesClosed(): Boolean {
        return isLeftEyeClosed() && isRightEyeClosed()
    }

    private fun Face.isSmilingFace(): Boolean {
        val probability = smilingProbability
        return (probability != null &&
                probability >= MAXIMUM_SMILING_CONFIDENCE)
    }

//    private fun isBlinking(history: String): Boolean {
//        return BLINKING_PATTERN in history
//    }

    private fun isBlinking(history: String): Boolean {
        for (i in 2 downTo 0) {
            val pattern = "11" + "0".repeat(i + 1) + "1111"
            if (pattern in history) {
                return true
            }
        }
        return false
    }

    private fun Face.detectSmiling(): Boolean {
        val faceId = trackingId
        if (faceId != null) {
            if (detectedFaces.containsKey(faceId)) {
                if (isSmilingFace()) {
                    smiled = true
                    listener?.blink()
                    return true
                } else {
                    listener?.smileOO()
                }
            } else {
                detectedFaces.clear()
                detectedFaces[faceId] = "1"
                listener?.smile()
            }
        }

        return false
    }

    private fun Face.detectBlinkingThenTakePicture() {
        val faceId = trackingId
        if (faceId != null) {
            var eyeStatus = "1" // we suppose the eyes are open

            val leftEyeOpenProbability = leftEyeOpenProbability
            val rightEyeOpenProbability = rightEyeOpenProbability

            if (leftEyeOpenProbability != null && rightEyeOpenProbability != null) {
                if (areEyesClosed()) {
                    Log.e(
                        TAG,
                        "detectBlinkingThenTakePicture: left: $leftEyeOpenProbability -- right: $rightEyeOpenProbability"
                    )
                    eyeStatus = "0"
                } else if ((leftEyeOpenProbability >= MINIMUM_EYE_OPEN_CONFIDENCE && leftEyeOpenProbability < MAXIMUM_EYE_OPEN_CONFIDENCE) ||
                    rightEyeOpenProbability >= MINIMUM_EYE_OPEN_CONFIDENCE && rightEyeOpenProbability < MAXIMUM_EYE_OPEN_CONFIDENCE
                ) {
                    listener?.blinkSlowly()
                }
            }

            if (detectedFaces.containsKey(faceId)) {
                var currentVal = detectedFaces[faceId]
                currentVal += eyeStatus
                detectedFaces[faceId] = currentVal!!
            } else {
                detectedFaces.clear()
                detectedFaces[faceId] = eyeStatus
            }

            val history = detectedFaces[faceId]!!

            if (history.length >= BLINKING_THRESHOLD && isBlinking(history) && areEyesOpen()) {
                faceBounds = boundingBox
                listener?.takePicture()
                detectedFaces.clear()
            }

            Log.e(TAG, "detectBlinking: $history")
        }
    }

    /**
     * Interface containing callbacks that are invoked when the face detection process succeeds or
     * fails.
     */
    interface FaceDetectionResultListener {
        /**
         * Signals that the face detection process has successfully completed for a camera frame.
         * It also provides the result of the face detection for further potential processing.
         *
         * @param faceBounds Detected faces from a camera frame
         */
        fun onSuccess(faceBounds: List<FaceBounds>) {}

        /**
         * Invoked when an error is encountered while attempting to detect faces in a camera frame.
         *
         * @param exception Encountered [Exception] while attempting to detect faces in a camera
         * frame.
         */
        fun onFailure(exception: Exception) {}

        fun blinkSlowly() {}
        fun smile() {}
        fun smileOO() {}
        fun takePicture() {}
        fun blink() {}

    }

    companion object {
        private const val TAG = "FaceDetector"
        private const val MIN_FACE_SIZE = 0.3f
        private const val MINIMUM_EYE_OPEN_CONFIDENCE = 0.1
        private const val MAXIMUM_EYE_OPEN_CONFIDENCE = 0.6
        private const val MAXIMUM_SMILING_CONFIDENCE = 0.7
        private const val BLINKING_PATTERN = "1101111"
        private const val BLINKING_THRESHOLD = 6
    }
}