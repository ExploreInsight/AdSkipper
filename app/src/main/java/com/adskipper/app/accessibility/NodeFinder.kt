package com.adskipper.app.accessibility

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class NodeFinder {

    private val TAG = "AdSkip:Finder"

    private val skipTextPatterns = listOf(
        "skip ad",
        "skip ads",
        "skip",
        "skip this ad",
        "skip ad in",
        "विज्ञापन छोड़ें",
    )

    private val knownViewIds = listOf(
        "com.google.android.youtube:id/skip_ad_button",
        "com.google.android.youtube:id/ad_progress_text",
        "com.google.android.youtube:id/skip_ad_button_text",
        "com.google.android.apps.youtube.music:id/skip_ad_button",
    )

    fun findSkipButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {

        for (viewId in knownViewIds) {
            try {
                val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
                if (!nodes.isNullOrEmpty()) {
                    val clickable = nodes.firstOrNull {
                        it.isVisibleToUser && isNodeClickable(it)
                    }
                    if (clickable != null) {
                        Log.d(TAG, "Found by viewId: $viewId")
                        nodes.filter { it != clickable }.forEach { safeRecycle(it) }
                        return clickable
                    }
                    nodes.forEach { safeRecycle(it) }
                }
            } catch (_: Exception) {}
        }

        for (pattern in skipTextPatterns) {
            try {
                val nodes = root.findAccessibilityNodeInfosByText(pattern)
                if (!nodes.isNullOrEmpty()) {
                    val target = findClickableInList(nodes)
                    if (target != null) {
                        Log.d(TAG, "Found by text: '$pattern'")
                        nodes.filter { it != target }.forEach { safeRecycle(it) }
                        return target
                    }
                    nodes.forEach { safeRecycle(it) }
                }
            } catch (_: Exception) {}
        }

        val result = deepSearch(root, 0)
        if (result != null) {
            Log.d(TAG, "Found by deep search")
        }
        return result
    }

    private fun isNodeClickable(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable || node.isCheckable
    }

    private fun findClickableInList(
        nodes: List<AccessibilityNodeInfo>
    ): AccessibilityNodeInfo? {
        for (node in nodes) {
            if (node.isVisibleToUser && isNodeClickable(node)) {
                return node
            }

            var parent: AccessibilityNodeInfo? = null
            try {
                parent = node.parent
            } catch (_: Exception) {}

            var depth = 0
            while (parent != null && depth < 5) {
                if (parent.isVisibleToUser && isNodeClickable(parent)) {
                    return parent
                }
                val grandparent = try { parent.parent } catch (_: Exception) { null }
                depth++
                parent = grandparent
            }
        }
        return null
    }

    private fun deepSearch(
        node: AccessibilityNodeInfo,
        depth: Int
    ): AccessibilityNodeInfo? {
        if (depth > 20) return null

        try {
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val combined = "$text $desc"

            val isSkipRelated = skipTextPatterns.any { pattern ->
                combined.contains(pattern)
            }

            if (isSkipRelated && node.isVisibleToUser && isNodeClickable(node)) {
                return AccessibilityNodeInfo.obtain(node)
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = deepSearch(child, depth + 1)
                if (result != null) {
                    safeRecycle(child)
                    return result
                }
                safeRecycle(child)
            }
        } catch (_: Exception) {}

        return null
    }

    private fun safeRecycle(node: AccessibilityNodeInfo) {
        try {
            node.recycle()
        } catch (_: Exception) {}
    }
}
