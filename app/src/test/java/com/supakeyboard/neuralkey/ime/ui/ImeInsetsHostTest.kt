package com.supakeyboard.neuralkey.ime.ui

import android.app.Activity
import android.content.Context
import android.graphics.Insets
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ActivityController

/** Real View measurement/dispatch, with controllable platform root-inset delivery. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35], qualifiers = "w360dp-h800dp-port")
class ImeInsetsHostTest {
    private class Host(context: Context) : ImeInsetsHost(context) {
        var windowInsets: WindowInsets? = null
        override fun getRootWindowInsets(): WindowInsets? = windowInsets
    }

    private lateinit var controller: ActivityController<Activity>
    private lateinit var host: Host
    private lateinit var content: View
    private lateinit var parent: FrameLayout
    private lateinit var decor: View

    @Before fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.window.setDecorFitsSystemWindows(false)
        parent = FrameLayout(activity)
        host = Host(activity)
        content = View(activity)
        host.addView(content, FrameLayout.LayoutParams(-1, 300))
        parent.addView(host, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        activity.setContentView(parent)
        controller.visible()
        decor = activity.window.decorView
        settleLayout()
    }

    @After fun tearDown() { controller.pause().stop().destroy() }

    private fun insets(nav: Insets = Insets.NONE, caption: Insets = Insets.NONE,
                       cutout: Insets = Insets.NONE): WindowInsets = WindowInsets.Builder()
        .setInsets(WindowInsets.Type.navigationBars(), nav)
        .setInsets(WindowInsets.Type.captionBar(), caption)
        .setInsets(WindowInsets.Type.displayCutout(), cutout)
        // Neither the keyboard's own inset nor a top status bar is keyboard padding.
        .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 500))
        .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 24, 0, 0))
        .build()

    private fun settleLayout() {
        repeat(4) {
            decor.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, 720, 1600)
            decor.viewTreeObserver.dispatchOnPreDraw()
        }
        assertFalse("Insets must converge without a repeated layout loop", host.isLayoutRequested)
    }

    private fun bottomGap(): Int {
        val rootPos = IntArray(2)
        val keyPos = IntArray(2)
        decor.getLocationInWindow(rootPos)
        content.getLocationInWindow(keyPos)
        return rootPos[1] + decor.height - keyPos[1] - content.height
    }

    @Test fun lateCaptionInsetMovesKeysWithoutAnyComposeStateChange() {
        host.windowInsets = insets()
        settleLayout()
        host.windowInsets = insets(nav = Insets.of(0, 0, 0, 24), caption = Insets.of(0, 0, 0, 48))
        // Deliberately no onApplyWindowInsets dispatch: reproduce stale child insets.
        settleLayout()
        assertEquals(48, bottomGap())
        assertEquals(300, content.height)
        assertEquals(0, host.paddingTop)
    }

    @Test fun consumedParentInsetsAreReservedExactlyOnce() {
        parent.setPadding(0, 0, 0, 48)
        host.windowInsets = insets(caption = Insets.of(0, 0, 0, 48))
        host.dispatchApplyWindowInsets(insets())
        settleLayout()
        assertEquals(0, host.paddingBottom)
        assertEquals(48, bottomGap())
    }

    @Test fun partiallyReservedInsetsOnlyAddTheMissingSpace() {
        parent.setPadding(0, 0, 0, 24)
        host.windowInsets = insets(caption = Insets.of(0, 0, 0, 48))
        settleLayout()
        assertEquals(24, host.paddingBottom)
        assertEquals(48, bottomGap())
    }

    @Test fun appSwitchAndNavigationModeChangesUpdateWithoutRecreatingKeys() {
        for (bottom in listOf(48, 24, 60, 0, 48)) {
            host.windowInsets = insets(nav = Insets.of(0, 0, 0, bottom))
            host.refreshInsets()
            settleLayout()
            assertEquals(bottom, bottomGap())
            assertSame(content, host.getChildAt(0))
        }
    }

    @Test fun rotationClearsBottomPaddingAndReservesSideBarsAndCutouts() {
        host.windowInsets = insets(caption = Insets.of(0, 0, 0, 48))
        settleLayout()
        host.windowInsets = insets(nav = Insets.of(48, 0, 0, 0), cutout = Insets.of(0, 0, 24, 0))
        settleLayout()
        assertEquals(0, host.paddingBottom)
        assertEquals(48, host.paddingLeft)
        assertEquals(24, host.paddingRight)
        assertEquals(300, content.height)
    }

    @Test fun systemControlAreaDoesNotDispatchTouchesToKeyboardContent() {
        var receivedTouch = false
        content.setOnTouchListener { _, _ -> receivedTouch = true; true }
        host.windowInsets = insets(caption = Insets.of(0, 0, 0, 48))
        settleLayout()
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 20f, host.height - 10f, 0)
        try { assertFalse(host.dispatchTouchEvent(event)) } finally { event.recycle() }
        assertFalse(receivedTouch)
        val dispatched = insets(caption = Insets.of(0, 0, 0, 48))
        assertSame(dispatched, host.dispatchApplyWindowInsets(dispatched))
    }
}
