package com.kryptkode.facedetection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.kryptkode.facedetection.databinding.ActivityMainBinding
import com.kryptkode.facedetection.detection.FaceDetector
import com.kryptkode.facedetection.detection.Frame
import com.kryptkode.facedetection.detection.LensFacing
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

    private fun initCamera(){
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupCamera()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(KEY_LENS_FACING, binding.viewfinder.facing)
        super.onSaveInstanceState(outState)
    }

    private fun setupCamera() {
        binding.facePosition.setOnOutLineShownListener {
            binding.captureImageBtn.isVisible = it
        }

        binding.viewfinder.setLifecycleOwner(this)
        binding.viewfinder.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(result: PictureResult) {
                PictureTakenActivity.pictureResult = result
                binding.captureImageBtn.isEnabled = true
                startActivity(PictureTakenActivity.getStartIntent(this@MainActivity))
            }
        })

        binding.captureImageBtn.setOnClickListener {
            if(binding.viewfinder.isTakingPicture.not()){
                binding.captureImageBtn.isEnabled = false
                binding.viewfinder.takePicture()
            }
        }

        val faceDetector = FaceDetector(binding.facePosition)
        binding.viewfinder.facing = Facing.FRONT
        binding.viewfinder.addFrameProcessor {
            faceDetector.process(
                Frame(
                    data = it.data,
                    rotation = it.rotation,
                    size = Size(it.size.width, it.size.height),
                    format = it.format,
                    lensFacing = if (binding.viewfinder.facing == Facing.BACK) LensFacing.BACK else LensFacing.FRONT
                )
            )
        }
    }

    companion object {
        private const val KEY_LENS_FACING = "key-lens-facing"
        private const val FOLDER = "FaceRecognition"
    }
}