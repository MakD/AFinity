package com.makd.afinity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
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
import com.makd.afinity.ui.theme.AFinityTheme
import com.makd.afinity.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Timber.d("Notification permission granted")
        } else {
            Timber.w("Notification permission denied - Media controls will not show")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkNotificationPermission()

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

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
            Scaffold(
                modifier = modifier
            ) { innerPadding ->
                LoginScreen(
                    onLoginSuccess = {
                    },
                    modifier = Modifier.padding(innerPadding),
                    widthSizeClass = widthSizeClass
                )
            }
        }
    }
}

sealed class AuthenticationState {
    object Loading : AuthenticationState()
    object Authenticated : AuthenticationState()
    object NotAuthenticated : AuthenticationState()
}