package com.example.automation

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object NodeUtils {
    private const val TAG = "NodeUtils"
    private const val DEFAULT_INTERVAL_MS = 250L

    fun findText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null || text.isBlank()) return null
        val result = root.findAccessibilityNodeInfosByText(text)
        return result.firstOrNull()
    }

    fun findId(root: AccessibilityNodeInfo?, id: String): AccessibilityNodeInfo? {
        if (root == null || id.isBlank()) return null
        return try {
            root.findAccessibilityNodeInfosByViewId(id).firstOrNull()
        } catch (t: Throwable) {
            Log.w(TAG, "findId failed for id=$id, error=${t.message}")
            null
        }
    }

    fun findByClassName(root: AccessibilityNodeInfo?, className: String): AccessibilityNodeInfo? {
        if (root == null || className.isBlank()) return null
        return searchByClassName(root, className)
    }

    fun findByContentDesc(root: AccessibilityNodeInfo?, desc: String): AccessibilityNodeInfo? {
        if (root == null || desc.isBlank()) return null
        return searchByContentDesc(root, desc)
    }

    fun findAllByClassName(root: AccessibilityNodeInfo?, className: String): List<AccessibilityNodeInfo> {
        if (root == null || className.isBlank()) return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()
        collectByClassName(root, className, result)
        return result
    }

    fun findNthByClassName(root: AccessibilityNodeInfo?, className: String, index: Int): AccessibilityNodeInfo? {
        if (index < 0) return null
        val all = findAllByClassName(root, className)
        return if (index < all.size) all[index] else null
    }

    fun findAllEditable(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()
        collectEditable(root, result)
        return result
    }

    fun findNthEditable(root: AccessibilityNodeInfo?, index: Int): AccessibilityNodeInfo? {
        if (index < 0) return null
        val all = findAllEditable(root)
        return if (index < all.size) all[index] else null
    }

    fun waitText(service: AccessibilityService, text: String, timeoutMs: Long): AccessibilityNodeInfo? {
        return waitNode(timeoutMs) {
            findText(service.rootInActiveWindow, text)
        }
    }

    fun waitContentDesc(service: AccessibilityService, desc: String, timeoutMs: Long): AccessibilityNodeInfo? {
        return waitNode(timeoutMs) {
            findByContentDesc(service.rootInActiveWindow, desc)
        }
    }

    fun waitPreferredNode(
        service: AccessibilityService,
        id: String?,
        text: String?,
        className: String?,
        timeoutMs: Long,
        intervalMs: Long = DEFAULT_INTERVAL_MS
    ): AccessibilityNodeInfo? {
        val start = SystemClock.uptimeMillis()
        var attempts = 0
        while (SystemClock.uptimeMillis() - start <= timeoutMs) {
            attempts++
            val root = service.rootInActiveWindow
            if (root != null) {
                if (!id.isNullOrBlank()) {
                    val byId = findId(root, id)
                    if (byId != null) {
                        Log.d(TAG, "waitPreferredNode hit by id=$id attempts=$attempts")
                        return byId
                    }
                }
                if (!text.isNullOrBlank()) {
                    val byText = findText(root, text)
                    if (byText != null) {
                        Log.d(TAG, "waitPreferredNode hit by text=$text attempts=$attempts")
                        return byText
                    }
                }
                if (!className.isNullOrBlank()) {
                    val byClass = findByClassName(root, className)
                    if (byClass != null) {
                        Log.d(TAG, "waitPreferredNode hit by class=$className attempts=$attempts")
                        return byClass
                    }
                }
            }
            Log.v(TAG, "waitPreferredNode retry=$attempts id=$id text=$text class=$className")
            SystemClock.sleep(intervalMs)
        }
        Log.w(TAG, "waitPreferredNode timeout id=$id text=$text class=$className timeoutMs=$timeoutMs")
        return null
    }

    fun waitId(service: AccessibilityService, id: String, timeoutMs: Long): AccessibilityNodeInfo? {
        return waitNode(timeoutMs) {
            findId(service.rootInActiveWindow, id)
        }
    }

    fun waitClassName(
        service: AccessibilityService,
        className: String,
        timeoutMs: Long
    ): AccessibilityNodeInfo? {
        return waitNode(timeoutMs) {
            findByClassName(service.rootInActiveWindow, className)
        }
    }

    fun waitNode(
        timeoutMs: Long,
        intervalMs: Long = DEFAULT_INTERVAL_MS,
        finder: () -> AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        val start = SystemClock.uptimeMillis()
        var attempts = 0
        while (SystemClock.uptimeMillis() - start <= timeoutMs) {
            attempts++
            val node = finder()
            if (node != null) {
                Log.d(TAG, "waitNode success, attempts=$attempts")
                return node
            }
            SystemClock.sleep(intervalMs)
        }
        Log.w(TAG, "waitNode timeout, attempts=$attempts, timeoutMs=$timeoutMs")
        return null
    }

    private fun searchByClassName(node: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
        if (node.className?.toString() == className) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val matched = searchByClassName(child, className)
            if (matched != null) return matched
        }
        return null
    }

    private fun searchByContentDesc(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString() == desc) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val matched = searchByContentDesc(child, desc)
            if (matched != null) return matched
        }
        return null
    }

    private fun collectByClassName(
        node: AccessibilityNodeInfo,
        className: String,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.className?.toString() == className) {
            out.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectByClassName(child, className, out)
        }
    }

    private fun collectEditable(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (node.isEditable) {
            out.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectEditable(child, out)
        }
    }
}
