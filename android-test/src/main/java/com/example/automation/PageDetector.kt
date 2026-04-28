package com.example.automation

import android.accessibilityservice.AccessibilityService
import android.util.Log

enum class PageType {
    UNKNOWN,
    LOGIN_PAGE,
    HOME_PAGE,
    MINE_PAGE,
    DEVICE_DETAIL_PAGE
}

object PageDetector {
    private const val TAG = "PageDetector"
    const val TARGET_PACKAGE = "com.tianruitest.app"

    private const val ID_PHONE = "com.tianruitest.app:id/et_phone"
    private const val ID_CODE = "com.tianruitest.app:id/et_code"
    private const val ID_LOGIN = "com.tianruitest.app:id/btn_login"

    private const val ID_TAB_MINE = "com.tianruitest.app:id/tab_mine"
    private const val ID_DEVICE_DETAIL = "com.tianruitest.app:id/tv_device_detail"

    fun detect(service: AccessibilityService): PageType {
        val root = service.rootInActiveWindow ?: return PageType.UNKNOWN
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg != TARGET_PACKAGE) {
            Log.d(TAG, "package not match target, pkg=$pkg target=$TARGET_PACKAGE")
        }

        val loginFeatures =
            NodeUtils.findId(root, ID_PHONE) != null ||
                NodeUtils.findId(root, ID_CODE) != null ||
                NodeUtils.findText(root, "登录") != null ||
                NodeUtils.findId(root, ID_LOGIN) != null
        if (loginFeatures) return PageType.LOGIN_PAGE

        val deviceDetailFeatures =
            (NodeUtils.findText(root, "设备详情") != null ||
                NodeUtils.findText(root, "产品详情") != null ||
                NodeUtils.findText(root, "车辆详情") != null) &&
                NodeUtils.findText(root, "我") == null &&
                NodeUtils.findId(root, ID_TAB_MINE) == null
        if (deviceDetailFeatures) return PageType.DEVICE_DETAIL_PAGE

        val mineFeatures =
            NodeUtils.findText(root, "设备详情") != null ||
                NodeUtils.findText(root, "产品详情") != null ||
                NodeUtils.findId(root, ID_DEVICE_DETAIL) != null ||
                NodeUtils.findText(root, "我的设备") != null ||
                NodeUtils.findText(root, "退出") != null ||
                NodeUtils.findText(root, "绑定车辆") != null ||
                NodeUtils.findByContentDesc(root, "logout") != null
        if (mineFeatures) return PageType.MINE_PAGE

        val homeFeatures =
            NodeUtils.findId(root, ID_TAB_MINE) != null ||
                NodeUtils.findText(root, "我") != null ||
                NodeUtils.findText(root, "首页") != null
        if (homeFeatures) return PageType.HOME_PAGE

        Log.d(TAG, "detect unknown page, pkg=$pkg")
        return PageType.UNKNOWN
    }
}
