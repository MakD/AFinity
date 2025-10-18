package com.makd.afinity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.updater.UpdateManager
import com.makd.afinity.data.updater.UpdateScheduler
import com.makd.afinity.data.updater.models.UpdateCheckFrequency
import com.makd.afinity.navigation.MainNavigation
import com.makd.afinity.ui.login.LoginScreen
import com.makd.afinity.ui.theme.AFinityTheme
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
    lateinit var updateScheduler: UpdateScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val themeMode by preferencesRepository.getThemeModeFlow()
                .collectAsState(initial = "SYSTEM")
            val dynamicColors by preferencesRepository.getDynamicColorsFlow()
                .collectAsState(initial = true)

            AFinityTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColors
            ) {
                MainContent(
                    modifier = Modifier.fillMaxSize()
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
    viewModel: MainViewModel = hiltViewModel()
) {
    val authState by viewModel.authenticationState.collectAsStateWithLifecycle()

    when (authState) {
        AuthenticationState.Loading -> {
            CustomAuthSplashScreen(
                modifier = modifier
            )
        }

        AuthenticationState.Authenticated -> {
            MainNavigation(
                modifier = modifier
            )
        }

        AuthenticationState.NotAuthenticated -> {
            Scaffold(
                modifier = modifier
            ) { innerPadding ->
                LoginScreen(
                    onLoginSuccess = {
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun CustomAuthSplashScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_monochrome),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.Center)
                .offset(y = (-32).dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "AFinity",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Powered By Jellyfin",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Authenticating...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

sealed class AuthenticationState {
    object Loading : AuthenticationState()
    object Authenticated : AuthenticationState()
    object NotAuthenticated : AuthenticationState()
}

/*@Preview(showBackground = true)
@Composable
fun MainActivityPreview() {
    AFinityTheme {
        LoginScreen(
            onLoginSuccess = {}
        )
    }
}*/