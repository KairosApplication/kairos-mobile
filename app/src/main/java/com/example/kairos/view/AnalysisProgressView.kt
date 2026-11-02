package com.example.kairos.view

import android.animation.ValueAnimator
import android.animation.PropertyValuesHolder
import android.animation.Keyframe
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.kairos.R

/** Figma 713:1502: original spinner, 2s loop, rotation ends at 60%, then holds. */
class AnalysisProgressView(context: Context, private val confirming: Boolean) : View(context) {
    private val spinner = ContextCompat.getDrawable(context, R.drawable.scanner_spinner)!!
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = ResourcesCompat.getFont(context, R.font.montserrat_regular) }
    private val backgroundPaint = Paint()
    private var angle = 0f
    private val animator = ValueAnimator.ofPropertyValuesHolder(PropertyValuesHolder.ofKeyframe("angle",
        Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(.6f, 359.989f).apply {
            interpolator = android.view.animation.PathInterpolator(.42f, 0f, .58f, 1f)
        }, Keyframe.ofFloat(1f, 359.989f))).apply {
        duration = 2000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { angle = it.getAnimatedValue("angle") as Float; invalidate() }
    }
    init { contentDescription = if (confirming) "Registrando reposição no servidor" else "Analisando gôndola" }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        backgroundPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFF0D6249.toInt(), 0xFF047C58.toInt(), 0xFF0D6249.toInt()), floatArrayOf(0f, .51923f, 1f), Shader.TileMode.CLAMP)
    }
    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        val scale = minOf(width / 412f, height / 917f)
        val centerX = width / 2f; val centerY = height * .453f
        canvas.save(); canvas.rotate(angle, centerX, centerY)
        spinner.setBounds((centerX - 37 * scale).toInt(), (centerY - 57 * scale).toInt(),
            (centerX + 37 * scale).toInt(), (centerY + 57 * scale).toInt())
        spinner.draw(canvas); canvas.restore()
        paint.textAlign = Paint.Align.CENTER; paint.color = Color.WHITE; paint.textSize = 24 * scale
        canvas.drawText(if (confirming) "Registrando reposição…" else "Analisando gôndola…", centerX, height * .57f, paint)
        paint.textSize = 16 * scale; paint.color = 0xBFFFFFFF.toInt()
        canvas.drawText(if (confirming) "Aguarde a confirmação do servidor." else "Identificando os produtos visíveis.", centerX, height * .62f, paint)
        if (!confirming) canvas.drawText("A foto não determina o saldo de estoque.", centerX, height * .65f, paint)
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (ValueAnimator.areAnimatorsEnabled()) animator.start() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
}
