package com.kryptkode.facedetection

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Size
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val lensFacing =
            savedInstanceState?.getSerializable(KEY_LENS_FACING) as Facing? ?: Facing.FRONT
        setupCamera(lensFacing)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(KEY_LENS_FACING, binding.viewfinder.facing)
        super.onSaveInstanceState(outState)
    }

    private fun setupCamera(lensFacing: Facing) {
        binding.facePosition.setOnOutLineShownListener {
            binding.captureImageBtn.isVisible = it
        }

        binding.viewfinder.setLifecycleOwner(this)
        binding.viewfinder.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(result: PictureResult) {
                Toast.makeText(this@MainActivity, "Picture captured", Toast.LENGTH_SHORT).show()
            }
        })

        binding.captureImageBtn.setOnClickListener {
            if(binding.viewfinder.isTakingPicture.not()){
                binding.viewfinder.takePicture()
            }
        }

        val faceDetector = FaceDetector(binding.facePosition)
        binding.viewfinder.facing = lensFacing
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
    }
}