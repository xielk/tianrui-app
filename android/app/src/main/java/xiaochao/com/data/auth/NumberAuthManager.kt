package xiaochao.com.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.mobile.auth.gatewayauth.PhoneNumberAuthHelper
import com.mobile.auth.gatewayauth.ResultCode
import com.mobile.auth.gatewayauth.TokenResultListener
import com.mobile.auth.gatewayauth.model.TokenRet
import xiaochao.com.BuildConfig

object NumberAuthManager {
    private const val TAG = "NUMBER_AUTH"

    fun prepare(context: Context) {
        if (BuildConfig.NUMBER_AUTH_SECRET.isBlank()) {
            Log.w(TAG, "NUMBER_AUTH_SECRET empty, one-key login disabled")
            return
        }
        val helper = PhoneNumberAuthHelper.getInstance(context.applicationContext, object : TokenResultListener {
            override fun onTokenSuccess(ret: String) = Unit
            override fun onTokenFailed(ret: String) = Unit
        })
        helper.getReporter().setLoggerEnable(true)
        helper.setAuthSDKInfo(BuildConfig.NUMBER_AUTH_SECRET)
    }

    fun startOneKeyLogin(
        activity: Activity,
        timeoutMs: Int = 5000,
        onToken: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (BuildConfig.NUMBER_AUTH_SECRET.isBlank()) {
            onError("号码认证未配置")
            return
        }

        lateinit var helper: PhoneNumberAuthHelper
        val listener = object : TokenResultListener {
            override fun onTokenSuccess(ret: String) {
                runCatching {
                    val tokenRet = TokenRet.fromJson(ret)
                    when (tokenRet.code) {
                        ResultCode.CODE_START_AUTHPAGE_SUCCESS -> {
                            Log.d(TAG, "auth page started")
                        }
                        ResultCode.CODE_SUCCESS -> {
                            val token = tokenRet.token.orEmpty()
                            helper.hideLoginLoading()
                            helper.quitLoginPage()
                            if (token.isNotBlank()) {
                                onToken(token)
                            } else {
                                onError("号码认证失败")
                            }
                            helper.setAuthListener(null)
                        }
                    }
                }.onFailure {
                    onError("号码认证解析失败")
                    helper.setAuthListener(null)
                }
            }

            override fun onTokenFailed(ret: String) {
                val message = runCatching { TokenRet.fromJson(ret).msg }.getOrNull().orEmpty()
                helper.hideLoginLoading()
                helper.quitLoginPage()
                helper.setAuthListener(null)
                onError(message.ifBlank { "一键登录失败，请使用短信登录" })
            }
        }

        helper = PhoneNumberAuthHelper.getInstance(activity.applicationContext, listener)
        helper.getReporter().setLoggerEnable(true)
        helper.setAuthSDKInfo(BuildConfig.NUMBER_AUTH_SECRET)
        helper.checkEnvAvailable()
        helper.getLoginToken(activity, timeoutMs)
    }
}
