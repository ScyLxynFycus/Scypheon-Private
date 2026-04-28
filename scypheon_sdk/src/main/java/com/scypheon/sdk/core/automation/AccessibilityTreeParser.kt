package com.scypheon.sdk.core.automation

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Enterprise Sub-System: Accessibility Tree Parser.
 * Recursively traverses the Android OS Accessibility Node Tree to find specific UI elements
 * (buttons, inputs, text) based on semantic descriptions or text matching, exactly like an
 * automated testing framework (Appium/UIAutomator).
 */
class AccessibilityTreeParser {

    /**
     * Finds the first node that matches the target text (case-insensitive)
     * either in its text property or content description.
     */
    fun findNodeByText(root: AccessibilityNodeInfo?, targetText: String): AccessibilityNodeInfo? {
        if (root == null) return null

        val text = root.text?.toString()?.lowercase() ?: ""
        val contentDesc = root.contentDescription?.toString()?.lowercase() ?: ""
        val query = targetText.lowercase()

        if (text.contains(query) || contentDesc.contains(query)) {
            return root
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            val result = findNodeByText(child, targetText)
            if (result != null) {
                return result
            }
        }

        return null
    }

    /**
     * Generates a structural dump of the UI for the LLM (VLM) to analyze.
     */
    fun dumpScreenStructure(root: AccessibilityNodeInfo?): String {
        if (root == null) return "[]"
        val sb = java.lang.StringBuilder()
        traverseAndDump(root, 0, sb)
        return sb.toString()
    }

    private fun traverseAndDump(node: AccessibilityNodeInfo, depth: Int, sb: java.lang.StringBuilder) {
        val indent = "  ".repeat(depth)
        val text = node.text?.toString() ?: "null"
        val desc = node.contentDescription?.toString() ?: "null"
        val isClickable = node.isClickable

        if (text != "null" || desc != "null" || isClickable) {
            sb.append("$indent- Node [text: $text, desc: $desc, clickable: $isClickable]\n")
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseAndDump(child, depth + 1, sb)
            }
        }
    }
}
