package com.example.finora.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Utility for handling display cutouts (notches) and system bar insets
 * to prevent UI clipping across all Android versions and device form factors.
 */
object EdgeToEdgeUtil {

    /**
     * Applies status bar and display cutout (notch) insets to [view]'s top padding.
     * Preserves any existing top padding set in layout.
     */
    fun applyTopCutoutInsets(view: View) {
        val basePaddingTop = view.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(top = basePaddingTop + insets.top)
            windowInsets
        }
        requestInsetsWhenAttached(view)
    }

    /**
     * Applies top insets (status bar + notch) to [topView] and bottom insets
     * (navigation bar) to [bottomView].
     */
    fun applySystemBarInsets(
        rootView: View,
        topView: View? = null,
        bottomView: View? = null
    ) {
        val baseTopPadding = topView?.paddingTop ?: 0
        val baseBottomPadding = bottomView?.paddingBottom ?: 0

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
            val topInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val bottomInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )

            topView?.updatePadding(top = baseTopPadding + topInsets.top)
            bottomView?.updatePadding(bottom = baseBottomPadding + bottomInsets.bottom)

            windowInsets
        }
        requestInsetsWhenAttached(rootView)
    }

    private fun requestInsetsWhenAttached(view: View) {
        if (view.isAttachedToWindow) {
            view.requestApplyInsets()
        } else {
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    v.requestApplyInsets()
                }
                override fun onViewDetachedFromWindow(v: View) {}
            })
        }
    }
}
