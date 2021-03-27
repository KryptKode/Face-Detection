package com.kryptkode.facedetection.detection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
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

    private var isWithinBounds = false
    private var callback: ((Boolean) -> Unit)? = null

    init {
        outlineMatrix.reset()
        val scale = R.dimen.stroke_scale.resToPx(context)
        outlineMatrix.setScale(scale, scale)
        backgroundPath.computeBounds(outlineBounds, true)

        Log.e(TAG, "Hello: $outlineBounds")

        outlinePath.transform(outlineMatrix)
        backgroundPath.transform(outlineMatrix)
        backgroundPath.fillType = Path.FillType.INVERSE_EVEN_ODD
    }

    internal fun updateFaces(bounds: List<FaceBounds>) {
        facesBounds.clear()
        facesBounds.addAll(bounds)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        backgroundPath.computeBounds(outlineBounds, true)

        Log.e(TAG, "onDraw width: $width" )
        Log.e(TAG, "onDraw height: $height" )

        val offSetX = (width.toFloat() / 2) - outlineBounds.centerX()
        val offSetY = (height.toFloat() / 2) - outlineBounds.centerY()

        Log.e(TAG, "onDraw: dx,dy= $offSetX,$offSetY")

        backgroundPath.offset(
            offSetX,
            offSetY
        )

        canvas.drawPath(backgroundPath, backgroundPaint)

        outlinePath.computeBounds(outlineBounds, true)

        outlinePath.offset(
            offSetX,
            offSetY
        )

        Log.e(TAG, "outlineBounds= $outlineBounds")

        val bounds = outlineBounds.inflate(200f)
        Log.e(TAG, "ovalBounds= $bounds")

        facesBounds.forEach { faceBounds ->
            Log.e(TAG, "onDraw: faceBounds= ${faceBounds.box}")
//            Log.e(TAG, "onDraw: dx,dy= $offSetX,$offSetY")
            Log.e(TAG, "onDraw: outlineBounds= $outlineBounds")
            Log.e(TAG, "onDraw: bounds= $bounds")
            if (outlineBounds.contains(faceBounds.box)) {
                canvas.drawPath(outlinePath, outlinePaint)
                isWithinBounds = true
                callback?.invoke(true)
            } else {
                isWithinBounds = false
                callback?.invoke(false)
            }
        }

        if (facesBounds.isEmpty()) {
            isWithinBounds = false
            callback?.invoke(false)
        }
    }

    fun setOnOutLineShownListener(listener: (Boolean) -> Unit) {
        callback = listener
    }

    fun isWithinBounds(): Boolean {
        return isWithinBounds
    }

    fun getBoundsWidth(): Float {
        return outlineBounds.width()
    }


    fun getBoundsHeight(): Float {
        return outlineBounds.height()
    }

    fun getFaceBounds(): RectF {
        return outlineBounds
    }


    companion object {
        const val TAG = "FacePositionView"
    }
}

/**
Returns a new rectangle with edges moved outwards by the given delta.
 */
fun RectF.inflate(delta: Float): RectF {
    return RectF(
        left - delta,
        top - delta,
        right + delta,
        bottom + delta
    )
}

fun Int.resToPx(context: Context): Float = context.resources.getDimension(this)

@ColorInt
fun @receiver:ColorRes Int.toColorInt(context: Context): Int = ContextCompat.getColor(context, this)
