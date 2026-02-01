package com.makd.afinity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.updater.UpdateManager
import com.makd.afinity.data.updater.UpdateScheduler
import com.makd.afinity.data.updater.models.UpdateCheckFrequency
import com.makd.afinity.navigation.MainNavigation
import com.makd.afinity.ui.components.AfinitySplashScreen
import com.makd.afinity.ui.login.LoginScreen
import com.makd.afinity.ui.login.LoginViewModel
import com.makd.afinity.ui.login.WebViewLoginScreen
import com.makd.afinity.ui.theme.AFinityTheme
import com.makd.afinity.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var updateManager: UpdateManager

    @Inject
    lateinit var offlineModeManager: OfflineModeManager

    @Inject
    lateinit var updateScheduler: UpdateScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
            val windowSize = calculateWindowSizeClass(this)
            val themeMode by preferencesRepository.getThemeModeFlow()
                .collectAsState(initial = "SYSTEM")
            val dynamicColors by preferencesRepository.getDynamicColorsFlow()
                .collectAsState(initial = true)

            AFinityTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColors
            ) {
                val windowInsetsController =
                    WindowCompat.getInsetsController(window, window.decorView)
                val mode = ThemeMode.fromString(themeMode)
                val systemInDarkTheme = isSystemInDarkTheme()

                val isLightTheme = when (mode) {
                    ThemeMode.SYSTEM -> !systemInDarkTheme
                    ThemeMode.LIGHT -> true
                    ThemeMode.DARK, ThemeMode.AMOLED -> false
                }

                windowInsetsController.isAppearanceLightStatusBars = isLightTheme
                windowInsetsController.isAppearanceLightNavigationBars = isLightTheme

                MainContent(
                    modifier = Modifier.fillMaxSize(),
                    updateManager = updateManager,
                    offlineModeManager = offlineModeManager,
                    widthSizeClass = windowSize.widthSizeClass
                )
            }
        }
        lifecycleScope.launch {
            val frequency = preferencesRepository.getUpdateCheckFrequency()
            if (frequency == UpdateCheckFrequency.ON_APP_OPEN.hours) {
                updateManager.checkForUpdates()
            }
        }
    }
}

@Composable
private fun MainContent(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
    updateManager: UpdateManager,
    offlineModeManager: OfflineModeManager,
    widthSizeClass: WindowWidthSizeClass
) {
    val authState by viewModel.authenticationState.collectAsStateWithLifecycle()

    when (authState) {
        AuthenticationState.Loading -> {
            AfinitySplashScreen(
                statusText = stringResource(R.string.splash_status_authenticating),
                modifier = modifier
            )
        }

        AuthenticationState.Authenticated -> {
            MainNavigation(
                modifier = modifier,
                updateManager = updateManager,
                offlineModeManager = offlineModeManager,
                widthSizeClass = widthSizeClass
            )
        }

        AuthenticationState.NotAuthenticated -> {
            var webViewUrl by remember { mutableStateOf<String?>(null) }

            if (webViewUrl != null) {
                val loginViewModel = hiltViewModel<LoginViewModel>()

                WebViewLoginScreen(
                    url = webViewUrl!!,
                    onLoginSuccess = { cookies ->
                        loginViewModel.saveAuthCookies(cookies)
                        webViewUrl = null
                    },
                    onBackClick = {
                        webViewUrl = null
                    }
                )
            } else {
                Scaffold(
                    modifier = modifier
                ) { innerPadding ->
                    LoginScreen(
                        onLoginSuccess = {},
                        onWebViewLoginRequired = { url ->
                            webViewUrl = url
                        },
                        modifier = Modifier.padding(innerPadding),
                        widthSizeClass = widthSizeClass
                    )
                }
            }
        }
    }
}

sealed class AuthenticationState {
    object Loading : AuthenticationState()
    object Authenticated : AuthenticationState()
    object NotAuthenticated : AuthenticationState()
}