package com.kryptkode.facedetection

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Size
import androidx.core.view.isVisible
import com.kryptkode.facedetection.databinding.ActivityMainBinding
import com.kryptkode.facedetection.detection.FaceDetector
import com.kryptkode.facedetection.detection.Frame
import com.kryptkode.facedetection.detection.LensFacing
import com.otaliastudios.cameraview.Facing

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

    override fun onResume() {
        super.onResume()
        binding.viewfinder.start()
    }

    override fun onPause() {
        super.onPause()
        binding.viewfinder.stop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(KEY_LENS_FACING, binding.viewfinder.facing)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.viewfinder.destroy()
    }

    private fun setupCamera(lensFacing: Facing) {
        binding.facePosition.setOnOutLineShownListener {
            binding.captureImageBtn.isVisible = it
        }

        binding.captureImageBtn.setOnClickListener {
            binding.viewfinder.capturePicture()
        }

        val faceDetector = FaceDetector(binding.facePosition)
        binding.viewfinder.facing = lensFacing
        binding.viewfinder.addFrameProcessor {
            if(it.size != null){
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
    }

    companion object {
        private const val KEY_LENS_FACING = "key-lens-facing"
    }
}