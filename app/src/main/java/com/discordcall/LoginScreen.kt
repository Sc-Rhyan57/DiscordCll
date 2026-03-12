package com.discordcall

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val JS_TOKEN = "javascript:(function()%7Bvar%20i%3Ddocument.createElement('iframe')%3Bdocument.body.appendChild(i)%3Balert(i.contentWindow.localStorage.token.slice(1,-1))%7D)()"

@Composable
fun LoginScreen(
    vm: AppViewModel,
    footerClicks: Int,
    onFooterClick: () -> Unit
) {
    val scale     = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }

    var showWebView by remember { mutableStateOf(false) }
    var loading     by remember { mutableStateOf(false) }
    var keyClicks   by remember { mutableStateOf(0) }
    var speed       by remember { mutableStateOf(1f) }

    val shimmerT = rememberInfiniteTransition(label = "sh")
    val shimmer  by shimmerT.animateFloat(-1.5f, 1.5f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "sv")
    val glowT    = rememberInfiniteTransition(label = "gl")
    val glow     by glowT.animateFloat(0.25f, 0.75f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "gv")

    if (showWebView) {
        WebViewLoginScreen(
            onTokenReceived = { t ->
                showWebView = false
                loading     = true
                CoroutineScope(Dispatchers.Main).launch {
                    vm.loginWithToken(t)
                    loading = false
                }
            },
            onBack = { showWebView = false }
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(AppColors.LoginBg1, AppColors.LoginBg2, AppColors.LoginBg3))
        ))
        ParticleBackground(speedMultiplier = speed, modifier = Modifier.fillMaxSize())

        Column(
            Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(1f))

            Box(
                Modifier
                    .size(96.dp)
                    .background(AppColors.Primary.copy(0.18f), CircleShape)
                    .border(1.5.dp, AppColors.Primary.copy(0.4f), CircleShape)
                    .clickable {
                        keyClicks++
                        speed = 1f + keyClicks * 1.2f
                        if (keyClicks >= 10) {
                            keyClicks = 0
                            speed = 1f
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Call,
                    null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            Text("Discord Call", fontSize = 28.sp, fontWeight = FontWeight.Black, color = AppColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("v${BuildConfig.VERSION_NAME}", fontSize = 10.sp, color = AppColors.TextMuted.copy(0.5f), fontFamily = FontFamily.Monospace)

            Spacer(Modifier.height(40.dp))

            Card(
                Modifier.fillMaxWidth().scale(scale.value),
                colors    = CardDefaults.cardColors(containerColor = AppColors.Surface.copy(0.9f)),
                shape     = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(10.dp),
                border    = BorderStroke(1.dp, AppColors.Primary.copy(0.2f))
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "O melhor cliente de chamadas Discord",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = AppColors.TextPrimary,
                        textAlign  = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Entre com sua conta Discord para acessar servidores e canais de voz.",
                        fontSize  = 13.sp,
                        color     = AppColors.TextMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))

                    if (vm.loginError != null) {
                        Box(
                            Modifier.fillMaxWidth()
                                .background(AppColors.ErrorContainer, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(vm.loginError!!, fontSize = 13.sp, color = AppColors.OnError)
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    Box(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier.fillMaxWidth().height(58.dp)
                                .graphicsLayer { shadowElevation = 28f; shape = RoundedCornerShape(14.dp); clip = false }
                                .background(AppColors.Primary.copy(glow * 0.4f), RoundedCornerShape(16.dp))
                        )
                        Box(
                            Modifier.fillMaxWidth().height(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AppColors.Primary)
                                .clickable(enabled = !loading) { showWebView = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.linearGradient(
                                        listOf(Color.Transparent, Color.White.copy(0.28f), Color.Transparent),
                                        start = Offset(shimmer * 400f + 200f, 0f),
                                        end   = Offset(shimmer * 400f + 350f, 80f)
                                    )
                                )
                            )
                            if (loading) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.5.dp)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Outlined.Login, null, modifier = Modifier.size(19.dp), tint = Color.White)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Entrar com Discord", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        SecurityPill(Icons.Outlined.Security, "Seguro")
                        SecurityPill(Icons.Outlined.VisibilityOff, "Privado")
                        SecurityPill(Icons.Outlined.PhoneAndroid, "Local")
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Footer(clicks = footerClicks, onClick = onFooterClick)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SecurityPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = AppColors.Success, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = AppColors.Success, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun WebViewLoginScreen(
    onTokenReceived: (String) -> Unit,
    onBack: () -> Unit
) {
    val webRef = remember { mutableStateOf<WebView?>(null) }

    BackHandler { onBack() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).also { wv ->
                    webRef.value = wv
                    wv.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    wv.setBackgroundColor(android.graphics.Color.parseColor("#1E1F22"))
                    wv.settings.apply {
                        javaScriptEnabled  = true
                        domStorageEnabled  = true
                        userAgentString    = "Mozilla/5.0 (Linux; Android 14; SM-S921U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
                    }
                    wv.webViewClient = object : WebViewClient() {
                        @Deprecated("Deprecated")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                            if (url.contains("/app") || url.endsWith("/channels/@me")) {
                                view.stopLoading()
                                Handler(Looper.getMainLooper()).postDelayed({ view.loadUrl(JS_TOKEN) }, 500)
                                return true
                            }
                            return false
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            if (url.contains("/app") || url.endsWith("/channels/@me")) {
                                Handler(Looper.getMainLooper()).postDelayed({ view.loadUrl(JS_TOKEN) }, 800)
                            }
                        }
                    }
                    wv.webChromeClient = object : WebChromeClient() {
                        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                            result.confirm()
                            view.visibility = View.GONE
                            if (message.isNotBlank() && message != "undefined") {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    onTokenReceived(message.trim())
                                }, 200)
                            }
                            return true
                        }
                    }
                    wv.loadUrl("https://discord.com/login")
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = 40.dp, start = 12.dp)
        ) {
            TextButton(
                onClick = { onBack() },
                colors  = ButtonDefaults.textButtonColors(
                    containerColor = AppColors.Background.copy(0.85f),
                    contentColor   = AppColors.TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Voltar", fontWeight = FontWeight.Bold)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webRef.value?.apply {
                stopLoading()
                loadUrl("about:blank")
                Handler(Looper.getMainLooper()).postDelayed({ clearHistory(); destroy() }, 500)
            }
            webRef.value = null
        }
    }
}
