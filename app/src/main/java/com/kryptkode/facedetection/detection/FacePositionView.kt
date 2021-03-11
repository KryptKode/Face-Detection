package com.kryptkode.facedetection.detection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRect
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
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

    private var callback: ((Boolean) -> Unit)? = null

    private val facesBounds = mutableListOf<FaceBounds>()
    private val outlineDrawable = VectorDrawableCompat.create(resources, R.drawable.ic_border, null)


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
//                outlineDrawable?.bounds = ovalRect.toRect()
//                outlineDrawable?.draw(canvas)
                canvas.drawOval(
                        ovalRect,
                        outlinePaint
                )
                callback?.invoke(true)
            } else {
                callback?.invoke(false)
            }
        }

        if(facesBounds.isEmpty()){
            callback?.invoke(false)
        }
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