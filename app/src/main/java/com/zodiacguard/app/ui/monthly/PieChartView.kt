package com.zodiacguard.app.ui.monthly

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()
    
    private var data: List<Pair<Float, Int>> = emptyList()
    private var total: Float = 0f
    private var expectedTotal: Float = 0f
    private val expectedColor = 0xFFFFA500.toInt() // Arancione

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

        // Se ci sono ore attese e le ore lavorate sono minori o uguali
        if (expectedTotal > 0 && total <= expectedTotal) {
            // Disegna cerchio arancione di sfondo (ore attese = 100%)
            paint.color = expectedColor
            paint.alpha = 100 // Semi-trasparente
            canvas.drawArc(rectF, -90f, 360f, true, paint)
            
            // Disegna le fette colorate che coprono in proporzione
            if (total > 0) {
                var startAngle = -90f
                val coverageAngle = (total / expectedTotal) * 360f
                
                for ((value, color) in data) {
                    if (value <= 0) continue
                    val sweepAngle = (value / expectedTotal) * 360f
                    paint.color = color
                    paint.alpha = 255
                    canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
                    startAngle += sweepAngle
                    
                    // Se abbiamo coperto tutto l'angolo disponibile, fermiamoci
                    if (startAngle >= -90f + coverageAngle) break
                }
            }
        } else {
            // Se le ore lavorate superano le attese, mostra solo le fette in proporzione
            if (data.isEmpty() || total == 0f) return
            
            var startAngle = -90f
            for ((value, color) in data) {
                if (value <= 0) continue
                val sweepAngle = (value / total) * 360f
                paint.color = color
                paint.alpha = 255
                canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
                startAngle += sweepAngle
            }
        }
    }
}
