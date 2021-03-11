package com.kryptkode.facedetection.detection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.kryptkode.facedetection.R

class FacePositionView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val path = Path()
    private val ovalRect = RectF()
    private val ovalCenter = PointF()
    private val outlinePaint = Paint().apply {
        color = Color.parseColor("#FF1B5ECE")
        style = Paint.Style.STROKE
        strokeWidth = R.dimen.stroke_width.resToPx(context)
        isAntiAlias = true
    }

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#BA000000")
    }

    private var showOutline = false
    private var callback: ((Boolean) -> Unit)? = null

    private val facesBounds = mutableListOf<FaceBounds>()
    private val anchorPaint = Paint()
    private val idPaint = Paint()
    private val boundsPaint = Paint()

    init {
        anchorPaint.color = ContextCompat.getColor(context, android.R.color.holo_blue_dark)

        idPaint.color = ContextCompat.getColor(context, android.R.color.holo_blue_dark)
        idPaint.textSize = 40f

        boundsPaint.style = Paint.Style.STROKE
        boundsPaint.color = ContextCompat.getColor(context, android.R.color.holo_blue_dark)
        boundsPaint.strokeWidth = 4f
    }

    internal fun updateFaces(bounds: List<FaceBounds>) {
        facesBounds.clear()
        facesBounds.addAll(bounds)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        path.reset()

        ovalCenter.set(
                (width / 2).toFloat(),
                (height / 2).toFloat()
        )

        ovalRect.set(
                fromCenter(
                        ovalCenter,
                        (width * 0.45).toFloat(),
                        (height * 0.45).toFloat()
                )
        )

        path.addOval(
                ovalRect,
                Path.Direction.CW
        )

        path.fillType = Path.FillType.INVERSE_EVEN_ODD

        canvas.drawPath(path, backgroundPaint)

        facesBounds.forEach { faceBounds ->
            if (ovalRect.contains(faceBounds.box)) {
                canvas.drawOval(
                        ovalRect,
                        outlinePaint
                )
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
        return ovalRect.contains(rectF)
    }

    fun setOnOutLineShownListener(listener: (Boolean) -> Unit) {
        callback = listener
    }


}

fun Int.resToPx(context: Context): Float = context.resources.getDimension(this)

fun fromCenter(center: PointF, width: Float, height: Float) =
        RectF(
                center.x - width / 2,
                center.y - height / 2,
                center.x + width / 2,
                center.y + height / 2
        )

@ColorInt
fun @receiver:ColorRes Int.toColorInt(context: Context): Int = ContextCompat.getColor(context, this)