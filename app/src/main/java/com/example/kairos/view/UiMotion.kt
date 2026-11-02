package com.example.kairos.view

import android.animation.ValueAnimator
import android.app.Activity
import android.view.View
import android.view.animation.PathInterpolator
import com.example.kairos.R

internal object UiMotion {
    fun reveal(view: View) {
        view.animate().cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) { view.alpha = 1f; view.translationY = 0f; return }
        view.alpha = 0f
        view.translationY = 12 * view.resources.displayMetrics.density
        view.animate().alpha(1f).translationY(0f).setDuration(220)
            .setInterpolator(PathInterpolator(.2f, 0f, 0f, 1f)).start()
    }
    @Suppress("DEPRECATION")
    fun open(activity: Activity) {
        if (ValueAnimator.areAnimatorsEnabled()) activity.overridePendingTransition(R.anim.kairos_screen_enter, R.anim.kairos_screen_exit)
    }
    @Suppress("DEPRECATION")
    fun close(activity: Activity) {
        if (ValueAnimator.areAnimatorsEnabled()) activity.overridePendingTransition(R.anim.kairos_screen_enter, R.anim.kairos_screen_exit)
    }
}
