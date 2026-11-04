package com.makd.afinity.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AudioWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var updater: AudioWidgetUpdater

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        updater.start()
        updater.refreshAsync { pending.finish() }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pending = goAsync()
        updater.deleteSettingsAsync(appWidgetIds) { pending.finish() }
    }
}
