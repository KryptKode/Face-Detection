package com.kryptkode.facedetection.detection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.kryptkode.facedetection.R
import com.sdsmdg.harjot.vectormaster.VectorMasterDrawable

class FacePositionView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val outlinePaint = Paint().apply {
        color = R.color.blue.toColorInt(context)
        style = Paint.Style.STROKE
        strokeWidth = R.dimen.stroke_width.resToPx(context)
        isAntiAlias = true
    }

    private val backgroundPaint = Paint().apply {
        color = R.color.background.toColorInt(context)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val outlineDrawable = VectorMasterDrawable(context, R.drawable.ic_border)
    private val backgroundPath = outlineDrawable.fullPath
    private val outlinePath = Path(backgroundPath)
    private val outlineMatrix = Matrix()
    private val outlineBounds = RectF()

    private val facesBounds = mutableListOf<FaceBounds>()

    private var showOutline = false
    private var callback: ((Boolean) -> Unit)? = null

    init {
        outlineMatrix.reset()
        val scale = R.dimen.stroke_scale.resToPx(context)
        outlineMatrix.setScale(scale, scale)
        backgroundPath.transform(outlineMatrix)
        outlinePath.transform(outlineMatrix)
    }

    internal fun updateFaces(bounds: List<FaceBounds>) {
        facesBounds.clear()
        facesBounds.addAll(bounds)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        backgroundPath.computeBounds(outlineBounds, true)
        backgroundPath.offset(
            (width / 2) - outlineBounds.centerX(),
            (height / 2) - outlineBounds.centerY()
        )

        outlinePath.computeBounds(outlineBounds, true)
        outlinePath.offset(
            (width / 2) - outlineBounds.centerX(),
            (height / 2) - outlineBounds.centerY()
        )

        backgroundPath.fillType = Path.FillType.INVERSE_EVEN_ODD

        canvas.drawPath(backgroundPath, backgroundPaint)

        facesBounds.forEach { faceBounds ->
            if (outlineBounds.contains(faceBounds.box)) {
                canvas.drawPath(outlinePath, outlinePaint)
                callback?.invoke(true)
            } else {
                callback?.invoke(false)
            }
        }
    }

    fun setShowOutline(show: Boolean) {
        showOutline = show
        invalidate()
    }

    fun isWithinOutline(rectF: RectF): Boolean {
        return outlineBounds.contains(rectF)
    }

    fun setOnOutLineShownListener(listener: (Boolean) -> Unit) {
        callback = listener
    }

}

fun Int.resToPx(context: Context): Float = context.resources.getDimension(this)

@ColorInt
fun @receiver:ColorRes Int.toColorInt(context: Context): Int = ContextCompat.getColor(context, this)
