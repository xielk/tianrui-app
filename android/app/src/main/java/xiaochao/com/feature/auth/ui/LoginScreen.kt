package xiaochao.com.feature.auth.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xiaochao.com.R
import xiaochao.com.feature.auth.presentation.AuthIntent
import xiaochao.com.feature.auth.presentation.AuthViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.errorString) {
        uiState.errorString?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.processIntent(AuthIntent.ErrorConsumed)
        }
    }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
            onLoginSuccess()
        }
    }

    Scaffold(containerColor = Color.White) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // Logo simulation
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .border(2.dp, Color(0xFF00509E), CircleShape)
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(60.dp)
                )
            }
            
            Text(
                text = "天瑞智行",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(top = 16.dp, bottom = 40.dp)
            )

            AuthTextField(
                value = uiState.phone,
                onValueChange = { viewModel.processIntent(AuthIntent.PhoneChanged(it)) },
                placeholder = "请输入手机号",
                maxLength = 11
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AuthTextField(
                    value = uiState.code,
                    onValueChange = { viewModel.processIntent(AuthIntent.CodeChanged(it)) },
                    placeholder = "请输入验证码",
                    maxLength = 6,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(
                    onClick = {
                        if (!uiState.isAgree) {
                            Toast.makeText(context, "请先阅读并同意协议", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        viewModel.processIntent(AuthIntent.SendCodeClicked)
                    },
                    enabled = uiState.countdown == 0,
                    modifier = Modifier.height(56.dp).width(120.dp),
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
                ) {
                    Text(if (uiState.countdown > 0) "${uiState.countdown}秒" else "发送验证码", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { viewModel.processIntent(AuthIntent.SubmitClicked) },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Text(if (uiState.isLoading) "登录中..." else "登录", fontSize = 16.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(14.dp))

            AgreementRow(
                isAgree = uiState.isAgree,
                onToggle = { viewModel.processIntent(AuthIntent.ToggleAgreement) },
                onShowAgreement = { viewModel.processIntent(AuthIntent.ShowAgreement(it)) },
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        if (uiState.showAgreementLayer) {
            AgreementWebDialog(
                title = uiState.agreementTitle,
                url = uiState.agreementUrl,
                onClose = { viewModel.processIntent(AuthIntent.CloseAgreement) }
            )
        }
    }
}
