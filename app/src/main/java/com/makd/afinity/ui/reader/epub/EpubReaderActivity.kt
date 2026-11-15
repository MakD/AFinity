package com.makd.afinity.ui.reader.epub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.ui.theme.AFinityTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import org.readium.r2.navigator.epub.EpubNavigatorFragment

@AndroidEntryPoint
class EpubReaderActivity : AppCompatActivity() {

    @Inject lateinit var preferencesRepository: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        super.onCreate(savedInstanceState)
        if (
            savedInstanceState != null ||
                intent.getStringExtra(EpubReaderViewModel.EXTRA_ITEM_ID) == null
        ) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            val themeModeFlow = remember { preferencesRepository.getThemeModeFlow() }
            val dynamicColorsFlow = remember { preferencesRepository.getDynamicColorsFlow() }
            val appFontFlow = remember { preferencesRepository.getAppFontFlow() }
            val themeMode by themeModeFlow.collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val dynamicColors by dynamicColorsFlow.collectAsStateWithLifecycle(initialValue = true)
            val appFont by appFontFlow.collectAsStateWithLifecycle(initialValue = "DEFAULT")
            AFinityTheme(themeMode = themeMode, dynamicColor = dynamicColors, appFont = appFont) {
                EpubReaderScreen(
                    onBack = ::finish,
                    onOpenBook = { next ->
                        startActivity(EpubReaderActivity.intent(this, next.id))
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        fun intent(context: Context, itemId: UUID): Intent =
            Intent(context, EpubReaderActivity::class.java)
                .putExtra(EpubReaderViewModel.EXTRA_ITEM_ID, itemId.toString())
    }
}
