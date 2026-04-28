package com.example.automation

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object ActionUtils {
    private const val TAG = "ActionUtils"

    fun click(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        var current: AccessibilityNodeInfo? = node
        var climb = 0
        while (current != null && climb < 10) {
            if (current.isClickable) {
                val ok = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "click result=$ok, climb=$climb")
                return ok
            }
            current = current.parent
            climb++
        }
        Log.w(TAG, "click failed, no clickable parent found")
        return false
    }

    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        val bundle = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        Log.d(TAG, "setText result=$ok, textLength=${text.length}")
        return ok
    }
}
