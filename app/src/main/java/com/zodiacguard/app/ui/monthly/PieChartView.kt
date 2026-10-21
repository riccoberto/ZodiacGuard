package com.zodiacguard.app.ui.monthly

import android.content.Context
import android.app.Dialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.zodiacguard.app.R
import kotlin.math.roundToInt

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(25, 36, 55)
        style = Paint.Style.FILL
    }
    private val centerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(170, 183, 202)
        textSize = 10f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.08f
    }
    private val rectF = RectF()
    
    private var data: List<Pair<Float, Int>> = emptyList()
    private var total: Float = 0f
    private var expectedTotal: Float = 0f
    private val expectedColor = 0xFFFFA500.toInt()

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Grafico distribuzione ore. Tocca per ingrandire."
        setOnClickListener { showExpandedChart() }
    }

    fun setData(newData: List<Pair<Float, Int>>, expected: Float = 0f) {
        data = newData
        total = newData.sumOf { it.first.toDouble() }.toFloat()
        expectedTotal = expected
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val size = width.coerceAtMost(height)
        rectF.set(0f, 0f, size.toFloat(), size.toFloat())

        if (expectedTotal > 0 && total <= expectedTotal) {
            paint.color = expectedColor
            paint.alpha = 100
            canvas.drawArc(rectF, -90f, 360f, true, paint)
            
            if (total > 0) {
                var startAngle = -90f
                val coverageAngle = (total / expectedTotal) * 360f
                
                for ((value, color) in data) {
                    if (value <= 0) continue
                    val sweepAngle = (value / expectedTotal) * 360f
                    paint.color = color
                    paint.alpha = 255
                    canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
                    
                    // NUOVO: Disegna etichetta percentuale al centro della fetta
                    if (sweepAngle > 15) { // Solo se la fetta è abbastanza grande
                        val midAngle = startAngle + sweepAngle / 2
                        val midRadius = size * 0.39f
                        val x = (size / 2 + midRadius * Math.cos(Math.toRadians(midAngle.toDouble()))).toFloat()
                        val y = (size / 2 + midRadius * Math.sin(Math.toRadians(midAngle.toDouble()))).toFloat()
                        val percentage = ((value / expectedTotal) * 100).toInt()
                        canvas.drawText("$percentage%", x, y, textPaint)
                    }
                    
                    startAngle += sweepAngle
                    if (startAngle >= -90f + coverageAngle) break
                }
            }
        } else {
            if (data.isEmpty() || total == 0f) return
            
            var startAngle = -90f
            for ((value, color) in data) {
                if (value <= 0) continue
                val sweepAngle = (value / total) * 360f
                paint.color = color
                paint.alpha = 255
                canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
                
                // NUOVO: Disegna etichetta percentuale
                if (sweepAngle > 15) {
                    val midAngle = startAngle + sweepAngle / 2
                    val midRadius = size * 0.39f
                    val x = (size / 2 + midRadius * Math.cos(Math.toRadians(midAngle.toDouble()))).toFloat()
                    val y = (size / 2 + midRadius * Math.sin(Math.toRadians(midAngle.toDouble()))).toFloat()
                    val percentage = ((value / total) * 100).toInt()
                    canvas.drawText("$percentage%", x, y, textPaint)
                }
                
                startAngle += sweepAngle
            }
        }

        val center = size / 2f
        canvas.drawCircle(center, center, size * 0.27f, centerPaint)
        val workedHours = total.toInt() / 60
        val workedMinutes = total.toInt() % 60
        canvas.drawText(
            String.format(java.util.Locale.ROOT, "%dh %02dm", workedHours, workedMinutes),
            center,
            center - 1f,
            textPaint
        )
        canvas.drawText(
            "LAVORATE",
            center,
            center + 17f * resources.displayMetrics.density,
            centerLabelPaint
        )
    }

    override fun performClick(): Boolean = super.performClick()

    fun showExpandedChart() {
        val density = resources.displayMetrics.density
        val screenWidthDp = (resources.displayMetrics.widthPixels / density).roundToInt()
        val screenHeightDp = (resources.displayMetrics.heightPixels / density).roundToInt()
        val dialogWidthDp = minOf(420, screenWidthDp - 40).coerceAtLeast(240)
        val chartSizeDp = minOf(dialogWidthDp - 32, screenHeightDp - 260).coerceAtLeast(220)
        val dialogContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundResource(R.drawable.bg_dialog_nfc_unlock)
            setPadding(
                (16 * density).roundToInt(),
                (16 * density).roundToInt(),
                (16 * density).roundToInt(),
                (16 * density).roundToInt()
            )
        }
        val enlargedChart = PieChartView(context).apply {
            setData(data, expectedTotal)
            contentDescription = "Grafico ingrandito della distribuzione delle ore"
            isClickable = false
            isFocusable = false
            setOnClickListener(null)
        }
        dialogContent.addView(
            enlargedChart,
            LinearLayout.LayoutParams(
                (chartSizeDp * density).roundToInt(),
                (chartSizeDp * density).roundToInt()
            )
        )
        dialogContent.addView(
            TextView(context).apply {
                text = "DISTRIBUZIONE DELLE ORE"
                textSize = 12f
                setTextColor(Color.rgb(242, 201, 76))
                gravity = Gravity.CENTER
                letterSpacing = 0.08f
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * density).roundToInt()
            }
        )

        val dialog = Dialog(context)
        val closeButton = com.google.android.material.button.MaterialButton(context).apply {
            text = "CHIUDI"
            setTextColor(Color.rgb(16, 24, 39))
            backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.rgb(242, 201, 76))
            setOnClickListener { dialog.dismiss() }
        }
        dialogContent.addView(
            closeButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (48 * density).roundToInt()
            ).apply {
                topMargin = (12 * density).roundToInt()
            }
        )
        dialog.setContentView(dialogContent)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (dialogWidthDp * density).roundToInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.CENTER)
        }
    }
}
