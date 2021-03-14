package com.kryptkode.facedetection

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kryptkode.facedetection.databinding.ActivityTakenBinding
import com.otaliastudios.cameraview.PictureResult

class PictureTakenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTakenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTakenBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        val result = pictureResult ?: run {
            finish()
            return
        }

        try {
            result.toBitmap(1000, 1000) { bitmap ->  binding.image.setImageBitmap(bitmap) }
        } catch (e: UnsupportedOperationException) {
            binding.image.setImageDrawable(ColorDrawable(Color.GREEN))
            Toast.makeText(this, "Can't preview this format: " + result.format, Toast.LENGTH_LONG).show()
        }

    }

    companion object {
        var pictureResult: PictureResult? = null

        private const val FILE_PATH = "path"
        fun getStartIntent(context: Context, path: String=""): Intent {
            val intent = Intent(context, PictureTakenActivity::class.java)
            intent.putExtra(FILE_PATH, path)
            return intent
        }
    }
}