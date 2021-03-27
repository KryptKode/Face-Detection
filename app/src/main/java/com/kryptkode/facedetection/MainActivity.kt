package com.kryptkode.facedetection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.kryptkode.facedetection.databinding.ActivityMainBinding
import com.kryptkode.facedetection.detection.FaceBounds
import com.kryptkode.facedetection.detection.FaceDetector
import com.kryptkode.facedetection.detection.Frame
import com.kryptkode.facedetection.detection.LensFacing
import com.kryptkode.facedetection.utils.BitmapUtils
import com.kryptkode.facedetection.utils.BitmapUtils.mapRect
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.PictureResult
import com.otaliastudios.cameraview.controls.Facing

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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
                initCamera()
            } else {
                Toast.makeText(this, "Grant permissions", Toast.LENGTH_SHORT).show()
            }
        }

    private fun allPermissionsGranted() = permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (allPermissionsGranted()) {
            initCamera()
        } else {
            permissionRequest.launch(permissions.toTypedArray())
        }
    }

    private fun initCamera() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupCamera()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(KEY_LENS_FACING, binding.viewfinder.facing)
        super.onSaveInstanceState(outState)
    }

    private fun setupCamera() {
        binding.captureImageBtn.isGone = true

        binding.facePosition.setOnOutLineShownListener {
            binding.cameraDisplayText.isVisible = it
        }

        binding.captureImageBtn.setOnClickListener {
            takePicture()
        }

        val faceDetector = FaceDetector(binding.facePosition)
        binding.viewfinder.setLifecycleOwner(this)
        binding.viewfinder.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(result: PictureResult) {
                result.toBitmap {
                    if (it == null) {
                        Toast.makeText(
                            this@MainActivity,
                            "Error while converting to bitmap",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val cropped = BitmapUtils.cropBitmapHandleOOMs(
                            it,
                            it.mapRect(
                                binding.facePosition.getFaceBounds(),
                                binding.facePosition.width,
                                binding.facePosition.height,
                            )
                        )
                        Log.e(TAG, "cropped image: $cropped")
                        PictureTakenActivity.pictureResult = cropped.bitmap
                        binding.captureImageBtn.isEnabled = true
                        startActivity(PictureTakenActivity.getStartIntent(this@MainActivity))
                    }
                }
            }
        })


        faceDetector.setonFaceDetectionFailureListener(object :
            FaceDetector.FaceDetectionResultListener {

            override fun blinkSlowly() {
                binding.cameraDisplayText.text = getString(R.string.blink_slowly)
            }

            override fun takePicture() {
                this@MainActivity.takePicture()
            }

            override fun onFailure(exception: Exception) {
                Log.e(TAG, "onFailure: ", exception)
            }

            override fun onSuccess(faceBounds: List<FaceBounds>) {

            }

            override fun smile() {
                binding.cameraDisplayText.text = getString(R.string.smile_msg)
            }

            override fun smileOO() {
                binding.cameraDisplayText.text = getString(R.string.smile_oo_msg)
            }

            override fun blink() {
                binding.cameraDisplayText.text = getString(R.string.blink_when_you_re_ready)
            }
        })
        binding.viewfinder.facing = Facing.FRONT
        binding.viewfinder.addFrameProcessor {
            faceDetector.process(
                Frame(
                    data = it.getData(),
                    rotation = it.rotationToUser,
                    size = Size(it.size.width, it.size.height),
                    format = it.format,
                    lensFacing = if (binding.viewfinder.facing == Facing.BACK) LensFacing.BACK else LensFacing.FRONT
                )
            )
        }
    }

    private fun takePicture() {
        binding.captureImageBtn.isEnabled = false
        binding.viewfinder.takePicture()
        binding.cameraDisplayText.text = getString(R.string.camera_capturing)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val KEY_LENS_FACING = "key-lens-facing"
        private const val FOLDER = "FaceRecognition"
    }
}