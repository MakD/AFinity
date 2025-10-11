package com.makd.afinity

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.navigation.MainNavigation
import com.makd.afinity.ui.home.HomeScreen
import com.makd.afinity.ui.login.LoginScreen
import com.makd.afinity.ui.theme.AFinityTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        /*setContent {
            AFinityTheme {
                MainContent(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }*/
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

@Preview(showBackground = true)
@Composable
fun MainActivityPreview() {
    AFinityTheme {
        LoginScreen(
            onLoginSuccess = {}
        )
    }
}