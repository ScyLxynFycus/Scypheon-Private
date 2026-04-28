package com.scypheon.sdk.core.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

/**
 * Enterprise Sub-System: Gesture Injector.
 * Calculates physical screen bounds and injects low-level X/Y coordinate touches
 * mimicking a human finger. Used when standard AccessibilityNode actions (ACTION_CLICK)
 * are blocked by the app.
 */
class GestureInjector(private val service: AccessibilityService) {

    /**
     * Executes a raw AccessibilityNode ACTION_CLICK.
     */
    fun performStandardClick(node: AccessibilityNodeInfo): Boolean {
        Timber.i("🖱️ GestureInjector: Attempting standard Accessibility ACTION_CLICK")
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Calculates the exact center of a UI node and dispatches a physical gesture tap.
     */
    fun performPhysicalTapOnNode(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (bounds.isEmpty) {
            Timber.e("🖱️ GestureInjector: Node bounds are empty. Cannot tap.")
            return false
        }

        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()

        Timber.i("🖱️ GestureInjector: Dispatching physical tap at X:$centerX, Y:$centerY")
        return dispatchPhysicalTap(centerX, centerY)
    }

    /**
     * Dispatches a raw X/Y physical tap.
     */
    fun dispatchPhysicalTap(x: Float, y: Float): Boolean {
        val clickPath = Path().apply {
            moveTo(x, y)
        }

        val clickStroke = GestureDescription.StrokeDescription(clickPath, 0, 50)
        val clickBuilder = GestureDescription.Builder()
        clickBuilder.addStroke(clickStroke)

        return service.dispatchGesture(clickBuilder.build(), null, null)
    }

    /**
     * Dispatches a physical swipe gesture.
     */
    fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300L): Boolean {
        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val swipeStroke = GestureDescription.StrokeDescription(swipePath, 0, durationMs)
        val swipeBuilder = GestureDescription.Builder()
        swipeBuilder.addStroke(swipeStroke)

        return service.dispatchGesture(swipeBuilder.build(), null, null)
    }
}
