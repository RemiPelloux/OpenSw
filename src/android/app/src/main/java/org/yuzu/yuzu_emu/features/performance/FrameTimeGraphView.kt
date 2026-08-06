// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.performance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max
import org.yuzu.yuzu_emu.R

class FrameTimeGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val samples = ArrayDeque<Float>(MAX_SAMPLES)
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.opensw_outline)
        strokeWidth = resources.displayMetrics.density
        alpha = 150
    }
    private val stablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.opensw_cyan)
        strokeWidth = resources.displayMetrics.density * 2f
        strokeCap = Paint.Cap.ROUND
    }
    private val spikePaint = Paint(stablePaint).apply {
        color = ContextCompat.getColor(context, R.color.opensw_yellow)
    }

    fun addSample(frameTimeMs: Double) {
        if (!frameTimeMs.isFinite() || frameTimeMs <= 0.0) return
        if (samples.size == MAX_SAMPLES) samples.removeFirst()
        samples.addLast(frameTimeMs.toFloat())
        postInvalidateOnAnimation()
    }

    fun clear() {
        samples.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        if (contentWidth <= 0 || contentHeight <= 0) return

        val maxFrameTime = max(50f, (samples.maxOrNull() ?: 0f) * 1.1f)
        drawGuide(canvas, 16.67f, maxFrameTime, contentWidth, contentHeight)
        drawGuide(canvas, 33.33f, maxFrameTime, contentWidth, contentHeight)
        if (samples.size < 2) return

        val values = samples.toList()
        val step = contentWidth.toFloat() / (MAX_SAMPLES - 1)
        val startX = paddingLeft + (MAX_SAMPLES - values.size) * step
        for (index in 1 until values.size) {
            val previous = values[index - 1]
            val current = values[index]
            canvas.drawLine(
                startX + (index - 1) * step,
                valueToY(previous, maxFrameTime, contentHeight),
                startX + index * step,
                valueToY(current, maxFrameTime, contentHeight),
                if (current > 33.33f) spikePaint else stablePaint
            )
        }
    }

    private fun drawGuide(
        canvas: Canvas,
        value: Float,
        maxValue: Float,
        contentWidth: Int,
        contentHeight: Int
    ) {
        val y = valueToY(value, maxValue, contentHeight)
        canvas.drawLine(paddingLeft.toFloat(), y, (paddingLeft + contentWidth).toFloat(), y, guidePaint)
    }

    private fun valueToY(value: Float, maxValue: Float, contentHeight: Int): Float {
        val normalized = (value / maxValue).coerceIn(0f, 1f)
        return paddingTop + contentHeight * (1f - normalized)
    }

    companion object {
        private const val MAX_SAMPLES = 120
    }
}
