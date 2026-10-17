package com.zodiacguard.app.ui.monthly

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.zodiacguard.app.R

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()
    
    // Dati del grafico: lista di (valore, colore)
    private var data: List<Pair<Float, Int>> = emptyList()
    private var total: Float = 0f
    
    // NUOVO: Valore di riferimento (ore attese) per lo sfondo
    private var expectedTotal: Float = 0f
    private val expectedColor = 0xFFFFA500.toInt() // Arancione

    fun setData(newData: List<Pair<Float, Int>>, expected: Float = 0f) {
        data = newData
        total = newData.sumOf { it.first.toDouble() }.toFloat()
        expectedTotal = expected
        invalidate() // Ridisegna
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val size = width.coerceAtMost(height)
        rectF.set(0f, 0f, size.toFloat(), size.toFloat())

        // NUOVO: Disegna sfondo arancione per ore attese (se specificato)
        if (expectedTotal > 0) {
            paint.color = expectedColor
            paint.alpha = 80 // Semi-trasparente
            canvas.drawArc(rectF, -90f, 360f, true, paint)
        }

        if (data.isEmpty() || total == 0f) return

        var startAngle = -90f // Inizia dall'alto

        for ((value, color) in data) {
            if (value <= 0) continue
            
            val sweepAngle = (value / total) * 360f
            paint.color = color
            paint.alpha = 255 // Opaco
            canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
            startAngle += sweepAngle
        }
    }
}
