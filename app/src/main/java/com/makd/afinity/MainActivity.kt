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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.PendingNavigationManager
import com.makd.afinity.data.manager.RemoteMessageManager
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.updater.UpdateManager
import com.makd.afinity.data.updater.UpdateScheduler
import com.makd.afinity.data.updater.models.UpdateCheckFrequency
import com.makd.afinity.data.updater.notification.UpdateNotificationManager
import com.makd.afinity.data.websocket.AudiobookshelfSocketManager
import com.makd.afinity.navigation.Destination
import com.makd.afinity.navigation.MainNavigation
import com.makd.afinity.ui.components.RemoteMessagePill
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

    @Inject lateinit var remoteMessageManager: RemoteMessageManager

    private val authViewModel: AuthViewModel by viewModels()

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
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

                SideEffect {
                    windowInsetsController.isAppearanceLightStatusBars = isLightTheme
                    windowInsetsController.isAppearanceLightNavigationBars = isLightTheme
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainContent(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = authViewModel,
                            updateManager = updateManager,
                            offlineModeManager = offlineModeManager,
                            widthSizeClass = windowSize.widthSizeClass,
                        )

                        val remoteMessage by
                            remoteMessageManager.message.collectAsStateWithLifecycle()
                        RemoteMessagePill(
                            message = remoteMessage,
                            onDismiss = remoteMessageManager::dismiss,
                            modifier =
                                Modifier.align(Alignment.TopCenter)
                                    .statusBarsPadding()
                                    .padding(top = 8.dp),
                        )
                    }
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
    viewModel: AuthViewModel = hiltViewModel(),
    updateManager: UpdateManager,
    offlineModeManager: OfflineModeManager,
    widthSizeClass: WindowWidthSizeClass,
) {
    val authState by viewModel.authenticationState.collectAsStateWithLifecycle()

    MainNavigation(
        modifier = modifier.fillMaxSize(),
        updateManager = updateManager,
        offlineModeManager = offlineModeManager,
        widthSizeClass = widthSizeClass,
        authState = authState,
    )
}
