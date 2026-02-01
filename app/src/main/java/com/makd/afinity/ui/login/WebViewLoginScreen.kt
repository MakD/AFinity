package com.makd.afinity.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.makd.afinity.R
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewLoginScreen(
    url: String,
    onLoginSuccess: (String) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Login") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_left),
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        WebViewContent(
            url = url,
            onLoginSuccess = onLoginSuccess,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContent(
    url: String,
    onLoginSuccess: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val originalHost = url.toUri().host
    val isDarkTheme = isSystemInDarkTheme()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    if (isDarkTheme) {
                        isAlgorithmicDarkeningAllowed = true
                    }
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    private var cookiesHarvested = false

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        checkForLoginComplete(url)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        checkForLoginComplete(url)
                    }

                    private fun checkForLoginComplete(currentUrl: String?) {
                        if (currentUrl == null || cookiesHarvested) return

                        val currentHost = currentUrl.toUri().host
                        val cookies = CookieManager.getInstance().getCookie(currentUrl)

                        Timber.d(
                            "WebViewLogin: url=$currentUrl | host=$currentHost | originalHost=$originalHost | cookies=${
                                cookies?.take(
                                    150
                                ) ?: "NULL"
                            }"
                        )

                        if (cookies.isNullOrBlank()) return

                        val isOnOriginalDomain = currentHost == originalHost
                        val isJellyfinHome = currentUrl.contains("/web/index.html") ||
                                currentUrl.endsWith("/web/")

                        if (isOnOriginalDomain || isJellyfinHome) {
                            cookiesHarvested = true
                            Timber.i("WebViewLogin: Harvesting cookies! isOnOriginalDomain=$isOnOriginalDomain isJellyfinHome=$isJellyfinHome")
                            onLoginSuccess(cookies)
                        }
                    }
                }
                loadUrl(url)
            }
        }
    )
}