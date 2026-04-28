package com.scypheon.sdk.core.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

/**
 * Enterprise PuppetMaster Android Implementation.
 * Acts as an Accessibility Service to automate UI tasks for users with dexterity
 * or cognitive impairments (e.g., elderly, visually impaired).
 *
 * Capability tiers:
 * 1. Intent Dispatch (Deep Links)
 * 2. View Node Traversal & Execution
 * 3. X/Y Coordinates Gesture Injection
 */
class PuppetMasterAndroid : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitor UI changes to track success of automation
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            Timber.d("PuppetMaster: UI state changed, parsing new Accessibility Tree...")
        }
    }

    override fun onInterrupt() {
        Timber.w("PuppetMaster Accessibility Service Interrupted.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.i("PuppetMaster Accessibility Service Connected and Active.")
    }

    /**
     * Tier 2 Automation: Traverses the Accessibility Tree to find and click a specific node by its text or content description.
     */
    fun clickNodeByText(targetText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        // Find nodes matching the text exactly
        val nodes = rootNode.findAccessibilityNodeInfosByText(targetText)
        if (nodes.isNullOrEmpty()) {
            Timber.w("PuppetMaster: Node with text '$targetText' not found.")
            return false
        }

        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Timber.i("PuppetMaster: Successfully clicked node '$targetText'")
                node.recycle()
                return true
            }
            // If the node itself isn't clickable, check its parent
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Timber.i("PuppetMaster: Successfully clicked parent of node '$targetText'")
                    node.recycle()
                    parent.recycle()
                    return true
                }
                parent = parent.parent
            }
            node.recycle()
        }

        Timber.w("PuppetMaster: Found node '$targetText' but it (and its parents) were not clickable.")
        return false
    }

    /**
     * Tier 3 Automation: Injects a physical tap at specific X, Y coordinates.
     * Used when the UI framework (e.g., heavily customized game engines or Flutter)
     * does not properly expose Accessibility Nodes.
     */
    fun performTapAt(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)

        // Build a gesture that simulates a quick tap
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 100L)) // 100ms duration
            .build()

        val success = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Timber.i("PuppetMaster: Hardware Gesture tap completed at ($x, $y).")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Timber.w("PuppetMaster: Hardware Gesture tap CANCELLED at ($x, $y).")
            }
        }, null)

        if (!success) {
            Timber.e("PuppetMaster: Failed to dispatch Hardware Gesture.")
        }
    }
}
