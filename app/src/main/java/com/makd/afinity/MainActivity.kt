package com.makd.afinity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.PendingNavigationManager
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.updater.UpdateManager
import com.makd.afinity.data.updater.UpdateScheduler
import com.makd.afinity.data.updater.models.UpdateCheckFrequency
import com.makd.afinity.data.updater.notification.UpdateNotificationManager
import com.makd.afinity.data.websocket.AudiobookshelfSocketManager
import com.makd.afinity.navigation.Destination
import com.makd.afinity.navigation.MainNavigation
import com.makd.afinity.ui.components.AfinitySplashScreen
import com.makd.afinity.ui.login.LoginScreen
import com.makd.afinity.ui.theme.AFinityTheme
import com.makd.afinity.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var preferencesRepository: PreferencesRepository

    @Inject lateinit var updateManager: UpdateManager

    @Inject lateinit var offlineModeManager: OfflineModeManager

    @Inject lateinit var updateScheduler: UpdateScheduler

    @Inject lateinit var audiobookshelfSocketManager: AudiobookshelfSocketManager

    @Inject lateinit var pendingNavigationManager: PendingNavigationManager

    private val mainViewModel: MainViewModel by viewModels()

    private val showNotificationRationale = MutableStateFlow(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                lifecycleScope.launch {
                    preferencesRepository.setNotificationPermissionDeclined(true)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        splashScreen.setKeepOnScreenCondition {
            mainViewModel.authenticationState.value == AuthenticationState.Loading
        }

        setContent {
            @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
            val windowSize = calculateWindowSizeClass(this)
            val themeMode by
                preferencesRepository
                    .getThemeModeFlow()
                    .collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val dynamicColors by
                preferencesRepository
                    .getDynamicColorsFlow()
                    .collectAsStateWithLifecycle(initialValue = true)

            val appFont by
                preferencesRepository
                    .getAppFontFlow()
                    .collectAsStateWithLifecycle(initialValue = "DEFAULT")
            AFinityTheme(themeMode = themeMode, dynamicColor = dynamicColors, appFont = appFont) {
                val windowInsetsController =
                    WindowCompat.getInsetsController(window, window.decorView)
                val mode = ThemeMode.fromString(themeMode)
                val systemInDarkTheme = isSystemInDarkTheme()

                val isLightTheme =
                    when (mode) {
                        ThemeMode.SYSTEM -> !systemInDarkTheme
                        ThemeMode.LIGHT -> true
                        ThemeMode.DARK,
                        ThemeMode.AMOLED -> false
                    }

                windowInsetsController.isAppearanceLightStatusBars = isLightTheme
                windowInsetsController.isAppearanceLightNavigationBars = isLightTheme

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainContent(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = mainViewModel,
                        updateManager = updateManager,
                        offlineModeManager = offlineModeManager,
                        widthSizeClass = windowSize.widthSizeClass,
                    )
                }

                val showRationale by showNotificationRationale.collectAsStateWithLifecycle()
                if (showRationale) {
                    AlertDialog(
                        onDismissRequest = {
                            showNotificationRationale.value = false
                            lifecycleScope.launch {
                                preferencesRepository.setNotificationPermissionDeclined(true)
                            }
                        },
                        title = { Text(stringResource(R.string.notification_permission_title)) },
                        text = { Text(stringResource(R.string.notification_permission_message)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showNotificationRationale.value = false
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                }
                            ) {
                                Text(stringResource(R.string.notification_permission_yes))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showNotificationRationale.value = false
                                    lifecycleScope.launch {
                                        preferencesRepository.setNotificationPermissionDeclined(
                                            true
                                        )
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.notification_permission_no))
                            }
                        },
                    )
                }
            }
        }
        lifecycleScope.launch {
            val frequency = preferencesRepository.getUpdateCheckFrequency()
            if (frequency == UpdateCheckFrequency.ON_APP_OPEN.hours) {
                updateManager.checkForUpdates()
            }
        }

        lifecycleScope.launch {
            val alreadyGranted =
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            val declined = preferencesRepository.getNotificationPermissionDeclined()
            if (!alreadyGranted && !declined) {
                showNotificationRationale.value = true
            }
        }

        if (intent.getBooleanExtra(UpdateNotificationManager.EXTRA_AUTO_DOWNLOAD_UPDATE, false)) {
            lifecycleScope.launch { updateManager.triggerAutoDownload() }
        }
        handleNavigationExtras(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(UpdateNotificationManager.EXTRA_AUTO_DOWNLOAD_UPDATE, false)) {
            lifecycleScope.launch { updateManager.triggerAutoDownload() }
        }
        handleNavigationExtras(intent)
    }

    private fun handleNavigationExtras(intent: Intent) {
        if (intent.getBooleanExtra(PendingNavigationManager.EXTRA_OPEN_DOWNLOADS, false)) {
            pendingNavigationManager.navigateTo(Destination.createDownloadSettingsRoute())
        }
    }
}

@Composable
private fun MainContent(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
    updateManager: UpdateManager,
    offlineModeManager: OfflineModeManager,
    widthSizeClass: WindowWidthSizeClass,
) {
    val authState by viewModel.authenticationState.collectAsStateWithLifecycle()
    val appLoadingState by viewModel.appLoadingState.collectAsStateWithLifecycle()

    var sawLogin by rememberSaveable { mutableStateOf(false) }
    // 0 = unresolved, 1 = start at home, 2 = start at onboarding hub
    var startDecision by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthenticationState.NotAuthenticated -> {
                sawLogin = true
                startDecision = 0
            }
            is AuthenticationState.Authenticated -> {
                if (startDecision == 0) {
                    startDecision =
                        if (sawLogin && !viewModel.isOnboardingFirstRunDone()) 2 else 1
                }
            }
            else -> {}
        }
    }

    AnimatedContent(
        targetState = authState is AuthenticationState.NotAuthenticated,
        transitionSpec = {
            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
        },
        label = "AuthTransition",
    ) { showLogin ->
        if (showLogin) {
            LoginScreen(onLoginSuccess = {}, modifier = modifier, widthSizeClass = widthSizeClass)
        } else {
            Box(modifier = modifier) {
                if (authState is AuthenticationState.Authenticated && startDecision != 0) {
                    MainNavigation(
                        modifier = Modifier.fillMaxSize(),
                        updateManager = updateManager,
                        offlineModeManager = offlineModeManager,
                        widthSizeClass = widthSizeClass,
                        startAtOnboarding = startDecision == 2,
                    )
                }

                val isAuthenticating = authState is AuthenticationState.Loading
                val resolvingOnboarding =
                    authState is AuthenticationState.Authenticated && startDecision == 0
                AnimatedVisibility(
                    visible = isAuthenticating || resolvingOnboarding || appLoadingState.isLoading,
                    enter = fadeIn(animationSpec = tween(400)),
                    exit = fadeOut(animationSpec = tween(400)),
                ) {
                    AfinitySplashScreen(
                        statusText =
                            if (isAuthenticating) {
                                stringResource(R.string.splash_status_authenticating)
                            } else {
                                appLoadingState.loadingPhase.ifEmpty { "Loading..." }
                            },
                        progress = if (isAuthenticating) null else appLoadingState.loadingProgress,
                        modifier = Modifier.fillMaxSize(),
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
