package com.kryptkode.facedetection

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.*
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.kryptkode.facedetection.databinding.FragmentCameraBinding
import com.kryptkode.facedetection.detection.FaceDetectionImageAnalysis
import com.kryptkode.facedetection.detection.FaceDetector
import com.kryptkode.facedetection.utils.ThreadExecutor
import com.kryptkode.facedetection.utils.mainExecutor
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraFragment : Fragment() {

    // An instance for display manager to get display change callbacks
    private val displayManager by lazy { requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }

    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null

    // A lazy instance of the current fragment's view binding
    private lateinit var binding: FragmentCameraBinding

    private var displayId = -1

    // Selector showing which camera is selected (front or back)
    private var lensFacing = CameraSelector.DEFAULT_FRONT_CAMERA

    // Selector showing which flash mode is selected (on, off or auto)
    private var flashMode = FLASH_MODE_ON

    // The Folder location where all the files will be stored
    private val outputDirectory: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${Environment.DIRECTORY_DCIM}/FaceRecognition/"
        } else {
            "${requireContext().getExternalFilesDir(Environment.DIRECTORY_DCIM)?.path}/FaceRecognition/"
        }
    }

    // The permissions we need for the app to work properly
    private val permissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_EXTERNAL_STORAGE,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                onPermissionGranted()
            } else {
                view?.let { v ->
                    Toast.makeText(requireContext(), "Grant permissions", Toast.LENGTH_SHORT).show()
                }
            }
        }

    /**
     * A display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        @SuppressLint("UnsafeExperimentalUsageError")
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                preview?.targetRotation = view.display.rotation
                imageCapture?.targetRotation = view.display.rotation
                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    /**
     * Check for the permissions
     */
    private fun allPermissionsGranted() = permissions.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        displayManager.registerDisplayListener(displayListener, null)

        binding.run {
            viewFinder.addOnAttachStateChangeListener(object :
                View.OnAttachStateChangeListener {
                override fun onViewDetachedFromWindow(v: View) =
                    displayManager.registerDisplayListener(displayListener, null)

                override fun onViewAttachedToWindow(v: View) =
                    displayManager.unregisterDisplayListener(displayListener)
            })
            btnTakePicture.setOnClickListener { takePicture() }

            facePosition.setOnOutLineShownListener { showing ->
                btnTakePicture.isVisible = showing
            }
        }

        if (allPermissionsGranted()) {
            onPermissionGranted()
        } else {
            permissionRequest.launch(permissions.toTypedArray())
        }
    }


    private fun onPermissionGranted() {
        // Each time apps is coming to foreground the need permission check is being processed
        binding.viewFinder.let { vf ->
            vf.post {
                // Setting current display ID
                displayId = vf.display.displayId
                startCamera()
            }
        }
    }

    /**
     * Unbinds all the lifecycles from CameraX, then creates new with new parameters
     * */
    private fun startCamera() {
        // This is the CameraX PreviewView where the camera will be rendered
        val viewFinder = binding.viewFinder

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // The display information
            val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
            // The ratio for the output image and preview
            val aspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
            // The display rotation
            val rotation = viewFinder.display.rotation

            val localCameraProvider = cameraProvider
                ?: throw IllegalStateException("Camera initialization failed.")

            // The Configuration of camera preview
            preview = Preview.Builder()
                .setTargetAspectRatio(aspectRatio) // set the camera aspect ratio
                .setTargetRotation(rotation) // set the camera rotation
                .build()

            // The Configuration of image capture
            imageCapture = Builder()
                .setFlashMode(flashMode) // set capture flash
                .setTargetAspectRatio(aspectRatio) // set the capture aspect ratio
                .setTargetRotation(rotation) // set the capture rotation
                .build()

            // The Configuration of image analyzing
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(aspectRatio) // set the analyzer aspect ratio
                .setTargetRotation(rotation) // set the analyzer rotation
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // in our analysis, we care about the latest image
                .build()
                .apply {
                    // Use a worker thread for image analysis to prevent glitches
                    val analyzerThread = HandlerThread("FaceRecognition").apply { start() }
                    setAnalyzer(
                        ThreadExecutor(Handler(analyzerThread.looper)),
                        FaceDetectionImageAnalysis(FaceDetector(binding.facePosition))
                    )
                }

            localCameraProvider.unbindAll() // unbind the use-cases before rebinding them

            try {
                // Bind all use cases to the camera with lifecycle
                localCameraProvider.bindToLifecycle(
                    viewLifecycleOwner, // current lifecycle owner
                    lensFacing, // either front or back facing
                    preview, // camera preview use case
                    imageCapture, // image capture use case
                    imageAnalyzer, // image analyzer use case
                )

                // Attach the viewfinder's surface provider to preview use case
                preview?.setSurfaceProvider(viewFinder.surfaceProvider)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind use cases", e)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /**
     *  Detecting the most suitable aspect ratio for current dimensions
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    @Suppress("NON_EXHAUSTIVE_WHEN")
    private fun takePicture() {
        captureImage()
    }

    private fun captureImage() {
        val localImageCapture =
            imageCapture ?: throw IllegalStateException("Camera initialization failed.")

        // Setup image capture metadata
        val metadata = Metadata().apply {
            // Mirror image when using the front camera
            isReversedHorizontal = lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA
        }

        // Options fot the output image file
        val outputOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, System.currentTimeMillis())
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, outputDirectory)
            }

            val contentResolver = requireContext().contentResolver

            // Create the output uri
            val contentUri =
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            OutputFileOptions.Builder(contentResolver, contentUri, contentValues)
        } else {
            File(outputDirectory).mkdirs()
            val file = File(outputDirectory, "${System.currentTimeMillis()}.jpg")

            OutputFileOptions.Builder(file)
        }.setMetadata(metadata).build()

        localImageCapture.takePicture(
            outputOptions, // the options needed for the final image
            requireContext().mainExecutor(), // the executor, on which the task will run
            object : OnImageSavedCallback { // the callback, about the result of capture process
                override fun onImageSaved(outputFileResults: OutputFileResults) {
                    // This function is called if capture is successfully completed
                    outputFileResults.savedUri
                        ?.let { uri ->

                            Log.d(TAG, "Photo saved in $uri")
                        }
                }

                override fun onError(exception: ImageCaptureException) {
                    // This function is called if there is an errors during capture process
                    val msg = "Photo capture failed: ${exception.message}"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    Log.e(TAG, msg)
                    exception.printStackTrace()
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        displayManager.unregisterDisplayListener(displayListener)
    }

    companion object {
        private const val TAG = "CameraXDemo"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0 // aspect ratio 4x3
        private const val RATIO_16_9_VALUE = 16.0 / 9.0 // aspect ratio 16x9
    }
}

