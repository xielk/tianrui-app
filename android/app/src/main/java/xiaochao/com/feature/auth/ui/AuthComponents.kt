package xiaochao.com.feature.auth.ui

import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import xiaochao.com.feature.auth.presentation.AgreementType

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    maxLength: Int = 11,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength) onValueChange(it) },
        placeholder = { Text(placeholder, color = Color(0xFFAAAAAA), fontSize = 14.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color(0xFFE0E0E0),
            focusedBorderColor = Color(0xFF007AFF),
            unfocusedContainerColor = Color(0xFFFAF9F9),
            focusedContainerColor = Color.White
        )
    )
}

@Composable
fun AgreementRow(
    isAgree: Boolean,
    onToggle: () -> Unit,
    onShowAgreement: (AgreementType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isAgree,
            onCheckedChange = { onToggle() },
        )
        Text(text = "我已阅读并同意", fontSize = 13.sp, color = Color(0xFF666666))
        Text(
            text = "《用户协议》",
            fontSize = 13.sp,
            color = Color(0xFF007AFF),
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { onShowAgreement(AgreementType.SERVICE) },
        )
        Text(text = "和", fontSize = 13.sp, color = Color(0xFF666666), modifier = Modifier.padding(horizontal = 2.dp))
        Text(
            text = "《隐私政策》",
            fontSize = 13.sp,
            color = Color(0xFF007AFF),
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { onShowAgreement(AgreementType.PRIVACY) },
        )
    }
}

@Composable
fun AgreementWebDialog(
    title: String,
    url: String,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                    Text(
                        text = "关闭",
                        color = Color(0xFF007AFF),
                        fontSize = 15.sp,
                        modifier = Modifier.clickable { onClose() }
                    )
                }
                HorizontalDivider(color = Color(0xFFEAEAEA))
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            loadUrl(url)
                        }
                    },
                    update = { webView ->
                        if (webView.url != url) {
                            webView.loadUrl(url)
                        }
                    }
                )
            }
        }
    }
}
