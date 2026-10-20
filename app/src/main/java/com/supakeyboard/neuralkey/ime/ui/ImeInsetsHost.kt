package com.supakeyboard.neuralkey.ime.ui

import android.content.Context
import android.graphics.Insets
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.FrameLayout

/**
 * Owns system-bar spacing outside Compose. IME caption/nav insets can arrive after the
 * input view is created, or be consumed by an OEM decor before reaching the input view.
 * Reconcile against the actual window at pre-draw, without waiting for recomposition.
 */
internal open class ImeInsetsHost(context: Context) : FrameLayout(context) {
    private val hostLocation = IntArray(2)
    private val rootLocation = IntArray(2)
    private val beforeDraw = ViewTreeObserver.OnPreDrawListener {
        // Padding changes request another measure. Do not draw one frame of overlapping keys.
        !reconcileWindowInsets()
    }

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        // Padding belongs to the system controls, never to a key's hit target.
        clipToPadding = true
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        // Bootstrap measurement before root coordinates exist. Once laid out, pre-draw
        // uses raw window insets so consumed child insets cannot undo the safe padding.
        if (!isLaidOut || rootWindowInsets == null) applySafePadding(insets.getInsets(SAFE_TYPES))
        invalidate()
        // Preserve dispatch to system-owned navigation controls and sibling views.
        return insets
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(beforeDraw)
        refreshInsets()
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(beforeDraw)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) refreshInsets()
    }

    fun refreshInsets() {
        requestApplyInsets()
        requestLayout()
    }

    private fun reconcileWindowInsets(): Boolean {
        val root = rootView
        if (!isLaidOut || root.height == 0) return false
        val insets = rootWindowInsets?.getInsets(SAFE_TYPES) ?: return false
        getLocationInWindow(hostLocation)
        root.getLocationInWindow(rootLocation)
        // Subtract space the platform already reserved. This also handles side navigation
        // and cutouts in landscape, without adding the same inset twice.
        val leftGap = (hostLocation[0] - rootLocation[0]).coerceAtLeast(0)
        val rightGap = (rootLocation[0] + root.width - hostLocation[0] - width).coerceAtLeast(0)
        val bottomGap = (rootLocation[1] + root.height - hostLocation[1] - height).coerceAtLeast(0)
        return applySafePadding(Insets.of(
            (insets.left - leftGap).coerceAtLeast(0),
            0,
            (insets.right - rightGap).coerceAtLeast(0),
            (insets.bottom - bottomGap).coerceAtLeast(0)
        ))
    }

    private fun applySafePadding(insets: Insets): Boolean {
        if (paddingLeft == insets.left && paddingRight == insets.right &&
            paddingBottom == insets.bottom) return false
        setPadding(insets.left, 0, insets.right, insets.bottom)
        return true
    }

    companion object {
        // The keyboard's own ime() inset must never be included here.
        private val SAFE_TYPES = WindowInsets.Type.navigationBars() or
            WindowInsets.Type.captionBar() or WindowInsets.Type.displayCutout()
    }
}
