package com.makd.afinity.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.ui.theme.AFinityTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AudioWidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var preferencesRepository: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val widgetId =
            intent
                ?.extras
                ?.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_CANCELED, result)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            val themeModeFlow = remember { preferencesRepository.getThemeModeFlow() }
            val dynamicColorsFlow = remember { preferencesRepository.getDynamicColorsFlow() }
            val appFontFlow = remember { preferencesRepository.getAppFontFlow() }
            val themeMode by themeModeFlow.collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val dynamicColors by dynamicColorsFlow.collectAsStateWithLifecycle(initialValue = true)
            val appFont by appFontFlow.collectAsStateWithLifecycle(initialValue = "DEFAULT")

            AFinityTheme(themeMode = themeMode, dynamicColor = dynamicColors, appFont = appFont) {
                AudioWidgetConfigScreen(
                    viewModel = hiltViewModel(),
                    onClose = { finish() },
                    onSaved = {
                        setResult(RESULT_OK, result)
                        finish()
                    },
                )
            }
        }
    }
}
