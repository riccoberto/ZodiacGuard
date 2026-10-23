package com.zodiacguard.app.ui.monthly

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.Locale

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val travelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TRAVEL_OVERLAY_COLOR
        alpha = 225
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(25, 36, 55)
        style = Paint.Style.FILL
    }
    private val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(190, 200, 215)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        letterSpacing = 0.05f
    }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 183, 77)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val outerBounds = RectF()
    private val ringBounds = RectF()

    private var progressAnimator: ValueAnimator? = null
    private var data: List<Pair<Float, Int>> = emptyList()
    private var travelData: List<Float> = emptyList()
    private var expectedTotal = 0f
    private var centerTotalMinutes = 0
    private var animationProgress = 1f

    init {
        totalPaint.textSize = 18f * density
        labelPaint.textSize = 9f * density
        detailPaint.textSize = 9f * density
        contentDescription = "Grafico circolare delle ore lavorate, viaggio incluso."
    }

    fun setData(
        newData: List<Pair<Float, Int>>,
        expected: Float = 0f,
        centerTotalMinutes: Int? = null,
        travelByCategory: List<Float> = emptyList()
    ) {
        data = newData
        travelData = travelByCategory
        expectedTotal = expected.coerceAtLeast(0f)
        this.centerTotalMinutes = centerTotalMinutes
            ?: newData.sumOf { it.first.toDouble() }.toInt()
        updateContentDescription()
        animateChart()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = width.coerceAtMost(height).toFloat()
        if (size <= 0f) return

        val centerX = width / 2f
        val centerY = height / 2f
        val outerStrokeWidth = 4f * density
        val ringStrokeWidth = minOf(size * 0.085f, 26f * density)
        val ringRadius = size * 0.345f

        outerPaint.strokeWidth = outerStrokeWidth
        outerBounds.set(
            centerX - size * 0.47f,
            centerY - size * 0.47f,
            centerX + size * 0.47f,
            centerY + size * 0.47f
        )
        outerPaint.color = TRACK_COLOR
        outerPaint.alpha = 155
        canvas.drawArc(outerBounds, 0f, 360f, false, outerPaint)

        ringPaint.strokeWidth = ringStrokeWidth
        ringBounds.set(
            centerX - ringRadius,
            centerY - ringRadius,
            centerX + ringRadius,
            centerY + ringRadius
        )
        ringPaint.color = EXPECTED_COLOR
        ringPaint.alpha = 185
        canvas.drawArc(ringBounds, 0f, 360f, false, ringPaint)

        val categoryTotal = data.sumOf { it.first.coerceAtLeast(0f).toDouble() }.toFloat()
        val target = if (expectedTotal > 0f) expectedTotal else categoryTotal
        if (categoryTotal > 0f && target > 0f) {
            drawCategories(
                canvas,
                target,
                minOf(categoryTotal, target) * animationProgress,
                ringStrokeWidth
            )
        }
        drawContractOverrun(
            canvas,
            centerX,
            centerY,
            size,
            outerStrokeWidth,
            categoryTotal,
            animationProgress
        )
        drawCenter(canvas, centerX, centerY, size)
    }

    private fun drawCategories(
        canvas: Canvas,
        targetMinutes: Float,
        visibleTotal: Float,
        ringStrokeWidth: Float
    ) {
        var consumedMinutes = 0f
        var startAngle = -90f
        val gapAngle = 2f
        travelPaint.strokeWidth = ringStrokeWidth * 0.42f

        data.forEachIndexed { index, (rawValue, color) ->
            val value = rawValue.coerceAtLeast(0f)
            val visibleValue = minOf(value, (visibleTotal - consumedMinutes).coerceAtLeast(0f))
            if (visibleValue <= 0f) return@forEachIndexed

            val sweepAngle = visibleValue / targetMinutes * 360f
            val arcStart = startAngle + if (sweepAngle > gapAngle) gapAngle / 2f else 0f
            val arcSweep = (sweepAngle - gapAngle).coerceAtLeast(0f)

            ringPaint.color = color
            ringPaint.alpha = 255
            canvas.drawArc(ringBounds, arcStart, arcSweep, false, ringPaint)

            val travelMinutes = travelData.getOrElse(index) { 0f }.coerceAtLeast(0f)
            val travelSweep = minOf(travelMinutes, visibleValue) / targetMinutes * 360f
            val visibleTravelSweep = minOf(travelSweep, arcSweep)
            if (visibleTravelSweep > 0f) {
                canvas.drawArc(
                    ringBounds,
                    arcStart,
                    visibleTravelSweep,
                    false,
                    travelPaint
                )
            }

            consumedMinutes += visibleValue
            startAngle += sweepAngle
        }
    }

    private fun drawContractOverrun(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float,
        strokeWidth: Float,
        categoryTotal: Float,
        progress: Float
    ) {
        if (expectedTotal <= 0f || categoryTotal <= expectedTotal) return

        val radius = size * 0.47f
        outerBounds.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        val excess = categoryTotal - expectedTotal
        val sweepAngle = (excess / expectedTotal * 360f).coerceAtMost(360f) * progress
        outerPaint.color = OVERRUN_COLOR
        outerPaint.alpha = 255
        outerPaint.strokeWidth = strokeWidth
        canvas.drawArc(outerBounds, -90f, sweepAngle, false, outerPaint)
    }

    private fun drawCenter(canvas: Canvas, centerX: Float, centerY: Float, size: Float) {
        canvas.drawCircle(centerX, centerY, size * 0.25f, centerPaint)

        val totalHours = centerTotalMinutes / 60
        val totalMinutes = centerTotalMinutes % 60
        canvas.drawText(
            String.format(Locale.ROOT, "%dh %02dm", totalHours, totalMinutes),
            centerX,
            centerY - 5f * density,
            totalPaint
        )
        canvas.drawText(
            "LAVORATE + VIAGGIO",
            centerX,
            centerY + 12f * density,
            labelPaint
        )

        val excess = (centerTotalMinutes - expectedTotal.toInt()).coerceAtLeast(0)
        val detail = when {
            expectedTotal <= 0f -> "CONTRATTO NON IMPOSTATO"
            excess > 0 -> "OLTRE +${formatCompactDuration(excess)}"
            else -> "OBIETTIVO ${formatCompactDuration(expectedTotal.toInt())}"
        }
        canvas.drawText(
            detail.uppercase(Locale.ROOT),
            centerX,
            centerY + 29f * density,
            detailPaint
        )
    }

    private fun updateContentDescription() {
        val travel = travelData.sumOf { it.coerceAtLeast(0f).toDouble() }.toInt()
        val excess = (centerTotalMinutes - expectedTotal.toInt()).coerceAtLeast(0)
        contentDescription = buildString {
            append("Totale lavorate e viaggio incluso: ${formatDuration(centerTotalMinutes)}.")
            append(" Viaggio evidenziato: ${formatDuration(travel)}.")
            if (expectedTotal > 0f) {
                append(" Ore attese: ${formatDuration(expectedTotal.toInt())}.")
                if (excess > 0) append(" Oltre il contratto: ${formatDuration(excess)}.")
            }
        }
    }

    private fun animateChart() {
        progressAnimator?.cancel()
        animationProgress = 0f
        progressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CHART_ANIMATION_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animationProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        progressAnimator?.cancel()
        progressAnimator = null
        super.onDetachedFromWindow()
    }

    private fun formatDuration(minutes: Int): String =
        "${minutes / MINUTES_PER_HOUR} ore e ${minutes % MINUTES_PER_HOUR} minuti"

    private fun formatCompactDuration(minutes: Int): String =
        "${minutes / MINUTES_PER_HOUR}h ${minutes % MINUTES_PER_HOUR}m"

    override fun performClick(): Boolean = super.performClick()

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val EXPECTED_COLOR = 0xFF795548.toInt()
        const val TRACK_COLOR = 0xFF526174.toInt()
        const val OVERRUN_COLOR = 0xFFFFB74D.toInt()
        const val TRAVEL_OVERLAY_COLOR = 0xFFD9C2FF.toInt()
        const val CHART_ANIMATION_DURATION_MS = 650L
    }
}
