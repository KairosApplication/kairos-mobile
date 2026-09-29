package com.example.kairos.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.view.View
import com.example.kairos.R

/** Figma 430:1114, with the original transparent GIF supplied for the spinner. */
class LoadingView(context: Context) : View(context) {
    private val spinner = ImageDecoder.decodeDrawable(
        ImageDecoder.createSource(resources, R.drawable.kairos_loading)
    ) as AnimatedImageDrawable
    private val backgroundPaint = Paint()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private var dots = 1
    private val tick = object : Runnable {
        override fun run() {
            dots = dots % 3 + 1
            invalidate()
            postDelayed(this, 400L)
        }
    }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.loading)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        spinner.callback = this
        spinner.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        backgroundPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFF0D6249.toInt(), 0xFF047C58.toInt(), 0xFF1B4D3E.toInt()),
            floatArrayOf(0f, 0.35096f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        // Keep the artwork square and the reference's vertical placement responsive.
        val scale = minOf(width / 414f, height / 917f)
        val left = (width - 300f * scale) / 2f
        val top = height * (335f / 917f) - 150f * scale
        // AnimatedImageDrawable draws at its decoded size. Transform the canvas
        // explicitly so its native renderer also respects the design's placement.
        val checkpoint = canvas.save()
        canvas.translate(left, top)
        canvas.scale(300f * scale / spinner.intrinsicWidth, 300f * scale / spinner.intrinsicHeight)
        spinner.setBounds(0, 0, spinner.intrinsicWidth, spinner.intrinsicHeight)
        spinner.draw(canvas)
        canvas.restoreToCount(checkpoint)
        labelPaint.textSize = 34.506f * scale
        val label = context.getString(R.string.loading)
        // Reserve all three dots so the word never jumps between frames.
        val textLeft = (width - labelPaint.measureText("$label...")) / 2f
        canvas.drawText(label + ".".repeat(dots), textLeft,
            height * (537f / 917f) - labelPaint.fontMetrics.top, labelPaint)
    }

    override fun verifyDrawable(who: Drawable) = who === spinner || super.verifyDrawable(who)

    private fun updateAnimation() {
        removeCallbacks(tick)
        spinner.stop()
        // This is ongoing progress feedback, independent of the system's
        // transition/animator duration scale (which may be zero).
        if (isAttachedToWindow && isShown && windowVisibility == VISIBLE) {
            spinner.start()
            postDelayed(tick, 400L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        spinner.stop()
        super.onDetachedFromWindow()
    }
}
