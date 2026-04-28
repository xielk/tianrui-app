package xiaochao.com.feature.f2.ui.components

import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import xiaochao.com.core.map.TianDiTuHtmlBuilder

@Composable
fun TianDiTuCurrentLocationWebMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
) {
    val html = remember(latitude, longitude) {
        TianDiTuHtmlBuilder.currentLocationHtml(latitude, longitude)
    }
    TianDiTuWebView(html = html, modifier = modifier)
}

@Composable
fun TianDiTuTrackWebMap(
    points: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
) {
    val html = remember(points) {
        TianDiTuHtmlBuilder.trackHtml(points)
    }
    TianDiTuWebView(html = html, modifier = modifier)
}

@Composable
private fun TianDiTuWebView(
    html: String,
    modifier: Modifier = Modifier,
) {
    val logTag = "TianDiTuWebMap"
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript("document.title") { title ->
                            Log.d(logTag, "page title=$title url=$url")
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        Log.e(logTag, "onReceivedError url=${request?.url} code=${error?.errorCode} desc=${error?.description}")
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        Log.d(logTag, "console ${consoleMessage.messageLevel()}: ${consoleMessage.message()} @${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}")
                        return true
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(android.graphics.Color.WHITE)
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { webView ->
            val oldHtml = webView.tag as? String
            if (oldHtml != html) {
                webView.tag = html
                webView.loadDataWithBaseURL(
                    "https://api.tianditu.gov.cn/",
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
    )
}
