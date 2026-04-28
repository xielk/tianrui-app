package com.example.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutomationService : AccessibilityService() {
    private val tag = "AutomationService"

    private enum class FlowState {
        HOME,
        OPEN_APP,
        LOGIN,
        HOME_PAGE,
        MINE,
        DEVICE_DETAIL,
        BACK_TO_MINE,
        LOGOUT,
        DONE
    }

    private var state: FlowState = FlowState.LOGIN
    private var lastActionAt = 0L
    private var lastDumpAt = 0L
    private var lastDumpPage: PageType = PageType.UNKNOWN
    private var phoneFilled = false
    private var codeFilled = false
    private var loginClicked = false
    private var loopCount = 0

    private val actionGapMs = 180L
    private val retryTimeoutMs = 1_500L
    private val dumpGapMs = 1_500L
    private val maxDumpNodes = 160

    private val appName = "天瑞智行"
    private val phone = "13817085533"
    private val code = "123123"

    private val idPhone = "com.tianruitest.app:id/et_phone"
    private val idCode = "com.tianruitest.app:id/et_code"
    private val idLogin = "com.tianruitest.app:id/btn_login"
    private val idMineTab = "com.tianruitest.app:id/tab_mine"
    private val idDeviceDetail = "com.tianruitest.app:id/tv_device_detail"
    private val targetLoops = 1

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        logStep("service connected, state=$state")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        if (!canRunNextAction()) return

        val currentPage = PageDetector.detect(this)
        Log.d(tag, "event type=$type state=$state page=$currentPage pkg=${event.packageName}")
        val pkg = currentPackageName()
        if (pkg != PageDetector.TARGET_PACKAGE) {
            Log.v(tag, "[SAFE_GUARD] skip non-target package: $pkg")
            return
        }
        maybeDumpUiTree(currentPage, event)

        when (state) {
            FlowState.HOME -> {
                state = FlowState.LOGIN
                touchActionTime()
            }
            FlowState.OPEN_APP -> {
                state = FlowState.LOGIN
                touchActionTime()
            }
            FlowState.LOGIN -> stepLogin(currentPage)
            FlowState.HOME_PAGE -> stepGoMine(currentPage)
            FlowState.MINE -> stepOpenDeviceDetail(currentPage)
            FlowState.DEVICE_DETAIL -> stepAtDeviceDetail(currentPage)
            FlowState.BACK_TO_MINE -> stepBackToMine()
            FlowState.LOGOUT -> stepLogout(currentPage)
            FlowState.DONE -> Unit
        }
    }

    override fun onInterrupt() {
        Log.w(tag, "service interrupted")
    }

    private fun stepGoHome() {
        logStep("[HOME] 回到桌面")
        val ok = performGlobalAction(GLOBAL_ACTION_HOME)
        Log.d(tag, "GLOBAL_ACTION_HOME result=$ok")
        if (ok) {
            resetFlowFlags()
            state = FlowState.OPEN_APP
            touchActionTime()
        }
    }

    private fun stepOpenApp() {
        logStep("[OPEN_APP] 在应用列表查找并打开: $appName")
        val node =
            waitNodePreferred(id = null, text = appName, className = "android.widget.TextView")
                ?: NodeUtils.waitContentDesc(this, appName, 1200)
        if (node != null && ActionUtils.click(node)) {
            state = FlowState.LOGIN
            touchActionTime()
            return
        }

        Log.w(tag, "app icon not found on current launcher page, waiting next event retry")
    }

    private fun stepLogin(page: PageType) {
        if (page == PageType.UNKNOWN) {
            logStep("[LOGIN] 非目标登录页，等待")
            return
        }

        if (page == PageType.HOME_PAGE || page == PageType.MINE_PAGE) {
            logStep("[LOGIN] 登录后已进入首页/我的页")
            state = FlowState.HOME_PAGE
            touchActionTime()
            return
        }

        if (!phoneFilled) {
            logStep("[LOGIN] 输入手机号")
            val phoneNode =
                waitNodePreferred(id = idPhone, text = "手机号", className = null)
                    ?: waitNodePreferred(id = null, text = "手机号码", className = null)
                    ?: waitNodePreferred(id = null, text = null, className = "android.widget.EditText")
                    ?: waitNthEditable(0)
                    ?: waitNthEditText(0)
            if (!ActionUtils.setText(phoneNode, phone)) {
                Log.w(tag, "set phone failed")
                return
            }
            phoneFilled = true
            touchActionTime()
            return
        }

        if (!codeFilled) {
            logStep("[LOGIN] 输入验证码")
            val codeNode =
                waitNodePreferred(id = idCode, text = null, className = null)
                    ?: waitNthEditable(1)
                    ?: waitNthEditText(1)
            if (!ActionUtils.setText(codeNode, code)) {
                Log.w(tag, "set code failed")
                return
            }
            codeFilled = true
            touchActionTime()
            return
        }

        val root = rootInActiveWindow
        val agreeText = NodeUtils.findText(root, "我已阅读并同意")
        if (agreeText != null) {
            val checkbox = NodeUtils.findByClassName(root, "android.widget.CheckBox")
            if (checkbox != null && checkbox.isCheckable && !checkbox.isChecked) {
                logStep("[LOGIN] 勾选协议")
                if (ActionUtils.click(checkbox)) {
                    touchActionTime()
                    return
                }
            }
        }

        logStep("[LOGIN] 点击登录")
        val loginNode =
            waitNodePreferred(id = idLogin, text = "登录", className = null)
                ?: waitNodePreferred(id = null, text = "立即登录", className = null)
                ?: waitNodePreferred(id = null, text = "登录", className = "android.widget.TextView")
        if (ActionUtils.click(loginNode)) {
            loginClicked = true
            touchActionTime()
            return
        }
        Log.w(tag, "click login failed, will retry; page=$page")
    }

    private fun stepGoMine(page: PageType) {
        if (page == PageType.MINE_PAGE) {
            state = FlowState.MINE
            touchActionTime()
            return
        }

        logStep("[HOME_PAGE] 点击底部 我")
        val mineNode = waitNodePreferred(id = idMineTab, text = "我", className = "android.widget.TextView")
        if (ActionUtils.click(mineNode)) {
            state = FlowState.MINE
            touchActionTime()
            return
        }
        logStep("[HOME_PAGE] 未找到我按钮，继续等待")
    }

    private fun stepOpenDeviceDetail(page: PageType) {
        if (page == PageType.DEVICE_DETAIL_PAGE) {
            state = FlowState.DEVICE_DETAIL
            touchActionTime()
            return
        }
        if (page != PageType.MINE_PAGE) {
            logStep("[MINE] 等待我的页面")
            return
        }

        logStep("[MINE] 点击设备详情")
        val deviceNode = waitNodePreferred(
            id = idDeviceDetail,
            text = "设备详情",
            className = "android.widget.TextView"
        ) ?: waitNodePreferred(
            id = null,
            text = "产品详情",
            className = "android.widget.TextView"
        )
        if (ActionUtils.click(deviceNode)) {
            state = FlowState.DEVICE_DETAIL
            touchActionTime()
        }
    }

    private fun stepAtDeviceDetail(page: PageType) {
        val reached = page == PageType.DEVICE_DETAIL_PAGE ||
            NodeUtils.waitText(this, "设备详情", 1000) != null ||
            NodeUtils.waitText(this, "产品详情", 1000) != null ||
            NodeUtils.waitText(this, "车辆详情", 1000) != null
        if (reached) {
            logStep("[DEVICE_DETAIL] 已到达详情页，准备返回我的")
            state = FlowState.BACK_TO_MINE
            touchActionTime()
            return
        }
        logStep("[DEVICE_DETAIL] 等待设备详情页")
    }

    private fun stepBackToMine() {
        logStep("[BACK_TO_MINE] 返回我的页面")
        val ok = performGlobalAction(GLOBAL_ACTION_BACK)
        Log.d(tag, "GLOBAL_ACTION_BACK result=$ok")
        if (ok) {
            state = FlowState.LOGOUT
            touchActionTime()
        }
    }

    private fun stepLogout(page: PageType) {
        if (page != PageType.MINE_PAGE &&
            NodeUtils.findText(rootInActiveWindow, "退出") == null &&
            NodeUtils.findByContentDesc(rootInActiveWindow, "logout") == null
        ) {
            logStep("[LOGOUT] 等待我的页面")
            return
        }

        logStep("[LOGOUT] 点击右上角退出")
        val logoutNode =
            waitNodePreferred(id = null, text = "退出", className = "android.widget.TextView")
                ?: waitNodePreferred(id = null, text = "退出", className = null)
                ?: waitNodePreferred(id = null, text = "注销", className = "android.widget.TextView")
                ?: NodeUtils.waitContentDesc(this, "logout", 1200)
        if (ActionUtils.click(logoutNode)) {
            loopCount += 1
            if (loopCount < targetLoops) {
                logStep("[LOOP] 第${loopCount}轮完成，开始下一轮")
                resetFlowFlags()
                state = FlowState.LOGIN
            } else {
                logStep("[DONE] 流程完成，共${loopCount}轮")
                state = FlowState.DONE
            }
            touchActionTime()
        }
    }

    private fun waitNodePreferred(id: String?, text: String?, className: String?): AccessibilityNodeInfo? {
        return NodeUtils.waitPreferredNode(
            service = this,
            id = id,
            text = text,
            className = className,
            timeoutMs = retryTimeoutMs
        )
    }

    private fun waitNthEditText(index: Int): AccessibilityNodeInfo? {
        return NodeUtils.waitNode(retryTimeoutMs) {
            NodeUtils.findNthByClassName(rootInActiveWindow, "android.widget.EditText", index)
        }
    }

    private fun waitNthEditable(index: Int): AccessibilityNodeInfo? {
        return NodeUtils.waitNode(retryTimeoutMs) {
            NodeUtils.findNthEditable(rootInActiveWindow, index)
        }
    }

    private fun canRunNextAction(): Boolean {
        val now = System.currentTimeMillis()
        return now - lastActionAt >= actionGapMs
    }

    private fun touchActionTime() {
        lastActionAt = System.currentTimeMillis()
    }

    private fun logStep(message: String) {
        Log.i(tag, message)
    }

    private fun maybeDumpUiTree(page: PageType, event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        val shouldDumpByPage = page != lastDumpPage
        val shouldDumpByGap = now - lastDumpAt >= dumpGapMs
        if (!shouldDumpByPage && !shouldDumpByGap) return

        val root = rootInActiveWindow ?: return
        lastDumpAt = now
        lastDumpPage = page

        Log.i(tag, "[UI_DUMP] begin page=$page event=${event.eventType} pkg=${event.packageName} class=${event.className}")
        val counter = IntArray(1)
        dumpNodeRecursive(root, depth = 0, counter = counter)
        Log.i(tag, "[UI_DUMP] end totalNodes=${counter[0]}")
    }

    private fun dumpNodeRecursive(node: AccessibilityNodeInfo?, depth: Int, counter: IntArray) {
        if (node == null) return
        if (counter[0] >= maxDumpNodes) return
        counter[0]++

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val indent = "  ".repeat(depth.coerceAtMost(6))
        val id = node.viewIdResourceName ?: "-"
        val text = node.text?.toString()?.replace("\n", "\\n") ?: "-"
        val desc = node.contentDescription?.toString()?.replace("\n", "\\n") ?: "-"
        val cls = node.className?.toString() ?: "-"

        Log.i(
            tag,
            "[UI] ${indent}d=$depth cls=$cls id=$id text=$text desc=$desc clickable=${node.isClickable} editable=${node.isEditable} enabled=${node.isEnabled} bounds=$bounds"
        )

        for (i in 0 until node.childCount) {
            dumpNodeRecursive(node.getChild(i), depth + 1, counter)
            if (counter[0] >= maxDumpNodes) return
        }
    }

    private fun resetFlowFlags() {
        phoneFilled = false
        codeFilled = false
        loginClicked = false
    }

    private fun currentPackageName(): String {
        return rootInActiveWindow?.packageName?.toString().orEmpty()
    }

    private fun isLauncherLike(pkg: String): Boolean {
        return pkg.contains("launcher")
    }
}
