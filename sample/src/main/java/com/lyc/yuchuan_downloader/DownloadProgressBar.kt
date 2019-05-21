package com.lyc.yuchuan_downloader

import android.content.Context
import android.graphics.*
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.util.AttributeSet
import android.view.View
import android.view.View.MeasureSpec.AT_MOST
import android.view.View.MeasureSpec.UNSPECIFIED
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Created by Liu Yuchuan on 2019/5/19.
 */
class DownloadProgressBar(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private var outRadius = 0
        set(value) {
            if (field != value) {
                field = value
                circlePaint.strokeWidth = strokeWidth
                innerPaint.strokeWidth = innerStrokeWidth
                rectF.left = strokeWidth / 2f
                rectF.top = rectF.left
                rectF.bottom = rectF.top + outRadius * 2 - strokeWidth
                rectF.right = rectF.bottom
                arrowPath.reset()
            }
        }
    private var circleColor = Color.rgb(205, 205, 205)
    private var activeColor = Color.rgb(69, 148, 255)
    private var innerColor = Color.rgb(119, 119, 119)
    private val DEFAULT_MAX_R = (21 * context.resources.displayMetrics.density).roundToInt()
    private val pauseLineHafHeight: Float
        get() = outRadius * 17 / 42f
    private val pauseLinesHalfDistance: Float
        get() = outRadius * 4 / 21f
    private val downloadArrowHalfHeight: Float
        get() = outRadius * 9 / 21f
    private val downloadArrowHalfWidth: Float
        get() = outRadius * 8 / 21f
    private val innerStrokeWidth
        get() = outRadius / 7f
    private val strokeWidth
        get() = outRadius * 4 / 63f
    private val circlePaint = Paint(ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val rectF = RectF()
    private val innerPaint = Paint(ANTI_ALIAS_FLAG)
    private val arrowPath = Path()
    var progress = 0
        set(value) {
            val newValue = max(0, min(value, 100))
            if (field != newValue) {
                field = newValue
                postInvalidate()
            }
        }
    var active = false
        set(value) {
            if (field != value) {
                field = value
                postInvalidate()
            }
        }

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wm = MeasureSpec.getMode(widthMeasureSpec)
        val hm = MeasureSpec.getMode(heightMeasureSpec)
        outRadius = if ((wm == AT_MOST && hm == AT_MOST) || (wm == UNSPECIFIED && hm == UNSPECIFIED)) {
            min(DEFAULT_MAX_R, min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec)))
        } else {
            min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        } / 2
        setMeasuredDimension(outRadius * 2, outRadius * 2)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        circlePaint.color = if (active) {
            activeColor
        } else {
            innerColor
        }
        val progressDegree = 360 * progress / 100f
        canvas.drawArc(rectF, -90f, progressDegree, false, circlePaint)
        circlePaint.color = circleColor
        canvas.drawArc(rectF, progressDegree - 90f, 360 - progressDegree, false, circlePaint)

        canvas.save()
        canvas.translate(outRadius.toFloat(), outRadius.toFloat())
        if (isPressed && isEnabled) {
            innerPaint.color = activeColor
        } else {
            innerPaint.color = innerColor
        }
        if (active) {
            val x = pauseLinesHalfDistance
            val y = pauseLineHafHeight
            canvas.drawLine(-x, -y, -x, y, innerPaint)
            canvas.drawLine(x, -y, x, y, innerPaint)
        } else {
            val x = downloadArrowHalfWidth
            val y = downloadArrowHalfHeight
            val delta = strokeWidth / sqrt(2f)
            canvas.drawLine(0f, -y, 0f, y - delta, innerPaint)
            canvas.drawLine(-x, y - x, delta, y + delta, innerPaint)
            canvas.drawLine(x, y - x, -delta, y + delta, innerPaint)
        }
        canvas.restore()
    }

    override fun refreshDrawableState() {
        super.refreshDrawableState()
        invalidate()
    }
}
