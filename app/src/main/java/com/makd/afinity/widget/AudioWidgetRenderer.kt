package com.makd.afinity.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.makd.afinity.MainActivity
import com.makd.afinity.R
import com.makd.afinity.cast.CastRemoteRouterActivity

internal class AudioWidgetRenderer(private val context: Context) {

    private enum class Layout(
        val shadowResId: Int,
        val plainResId: Int,
        val isRow: Boolean,
        val hasCover: Boolean,
        val continueRows: Int,
        val titleLines: Int,
        val showSubtitle: Boolean = true,
    ) {
        SMALL(
            R.layout.widget_audio_small,
            R.layout.widget_audio_small_plain,
            isRow = true,
            hasCover = true,
            continueRows = 0,
            titleLines = 1,
            showSubtitle = false,
        ),
        SMALL_TALL(
            R.layout.widget_audio_small,
            R.layout.widget_audio_small_plain,
            isRow = true,
            hasCover = true,
            continueRows = 0,
            titleLines = 1,
        ),
        SQUARE(
            R.layout.widget_audio_square,
            R.layout.widget_audio_square_plain,
            isRow = false,
            hasCover = false,
            continueRows = 0,
            titleLines = 1,
        ),
        SQUARE_TALL(
            R.layout.widget_audio_square,
            R.layout.widget_audio_square_plain,
            isRow = false,
            hasCover = false,
            continueRows = 0,
            titleLines = 2,
        ),
        MEDIUM(
            R.layout.widget_audio_medium,
            R.layout.widget_audio_medium_plain,
            isRow = false,
            hasCover = true,
            continueRows = 0,
            titleLines = 1,
        ),
        MEDIUM_TALL(
            R.layout.widget_audio_medium,
            R.layout.widget_audio_medium_plain,
            isRow = false,
            hasCover = true,
            continueRows = 0,
            titleLines = 2,
        ),
        LARGE(
            R.layout.widget_audio_large,
            R.layout.widget_audio_large_plain,
            isRow = false,
            hasCover = true,
            continueRows = 2,
            titleLines = 1,
        ),
        LARGE_TALL(
            R.layout.widget_audio_large,
            R.layout.widget_audio_large_plain,
            isRow = false,
            hasCover = true,
            continueRows = 3,
            titleLines = 2,
        ),
    }

    private class Style(
        val settings: AudioWidgetSettings,
        val lightText: Boolean,
        val cover: Bitmap?,
    ) {
        val textColor = AudioWidgetLook.textColor(lightText)
        val secondaryTextColor = AudioWidgetLook.secondaryTextColor(lightText)
    }

    private class ContinueSlot(val row: Int, val title: Int, val subtitle: Int)

    fun render(
        state: AudioWidgetState,
        settings: AudioWidgetSettings,
        lightText: Boolean,
        cover: Bitmap?,
    ): RemoteViews {
        val style = Style(settings, lightText, cover)
        return RemoteViews(
            mapOf(
                SizeF(250f, 48f) to build(state, Layout.SMALL, style),
                SizeF(250f, 64f) to build(state, Layout.SMALL_TALL, style),
                SizeF(110f, 110f) to build(state, Layout.SQUARE, style),
                SizeF(110f, 150f) to build(state, Layout.SQUARE_TALL, style),
                SizeF(250f, 110f) to build(state, Layout.MEDIUM, style),
                SizeF(250f, 140f) to build(state, Layout.MEDIUM_TALL, style),
                SizeF(250f, 240f) to build(state, Layout.LARGE, style),
                SizeF(250f, 330f) to build(state, Layout.LARGE_TALL, style),
            )
        )
    }

    private fun build(state: AudioWidgetState, layout: Layout, style: Style): RemoteViews {
        val settings = style.settings
        val views =
            RemoteViews(
                context.packageName,
                if (settings.textShadow) layout.shadowResId else layout.plainResId,
            )

        views.setColorStateList(
            R.id.widget_card,
            "setBackgroundTintList",
            ColorStateList.valueOf(
                AudioWidgetLook.backgroundColor(style.lightText, settings.backgroundOpacity)
            ),
        )
        views.setInt(
            R.id.widget_root,
            "setLayoutDirection",
            if (settings.alignment == WidgetAlignment.RIGHT) View.LAYOUT_DIRECTION_LTR
            else View.LAYOUT_DIRECTION_RTL,
        )

        bindStatus(views, state, style)
        setOptionalText(views, R.id.widget_title, state.title, style)
        views.setInt(R.id.widget_title, "setMaxLines", layout.titleLines)
        setOptionalText(
            views,
            R.id.widget_subtitle,
            state.subtitle.takeIf { layout.showSubtitle },
            style,
        )
        if (layout.hasCover) bindCover(views, style)

        bindTransport(views, state, layout, style)
        bindPlay(views, state, style)

        val open =
            if (state.isCasting) castRemoteIntent()
            else openIntent(state.takeIf { it.isLoaded }, OPEN_PLAYER_REQUEST_CODE)
        views.setOnClickPendingIntent(R.id.widget_info, open)
        views.setOnClickPendingIntent(R.id.widget_root, open)

        if (layout.continueRows > 0) {
            bindContinue(
                views,
                state,
                if (settings.showContinue) layout.continueRows else 0,
                style,
            )
        }
        return views
    }

    private fun bindStatus(views: RemoteViews, state: AudioWidgetState, style: Style) {
        views.setViewVisibility(
            R.id.widget_status_row,
            if (style.settings.showStatus) View.VISIBLE else View.GONE,
        )
        val materialYou = style.settings.badgeColours == WidgetBadgeColours.MATERIAL_YOU
        val (statusText, badge) =
            when (state.status) {
                AudioWidgetStatus.PLAYING ->
                    R.string.widget_status_playing to
                        if (materialYou) R.drawable.widget_badge_you_playing
                        else R.drawable.widget_badge_playing
                AudioWidgetStatus.PAUSED ->
                    R.string.widget_status_paused to
                        if (materialYou) R.drawable.widget_badge_you_paused
                        else R.drawable.widget_badge_paused
                AudioWidgetStatus.STOPPED ->
                    R.string.widget_status_stopped to
                        if (materialYou) R.drawable.widget_badge_you_stopped
                        else R.drawable.widget_badge_stopped
            }
        views.setTextViewText(R.id.widget_status, context.getString(statusText))
        views.setTextColor(R.id.widget_status, style.textColor)
        views.setImageViewResource(R.id.widget_badge, badge)
    }

    private fun bindCover(views: RemoteViews, style: Style) {
        val cover = style.cover
        if (style.settings.showCover && cover != null) {
            views.setImageViewBitmap(R.id.widget_cover, cover)
            views.setViewVisibility(R.id.widget_cover, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_cover, View.GONE)
        }
    }

    private fun bindTransport(
        views: RemoteViews,
        state: AudioWidgetState,
        layout: Layout,
        style: Style,
    ) {
        val isAbs = state.engine == AudioWidgetEngine.ABS
        if (layout.isRow) {
            bindControl(
                views,
                R.id.widget_next,
                if (isAbs) R.drawable.ic_rewind_backward_30 else R.drawable.ic_player_skip_forward,
                if (isAbs) R.string.cd_rewind_30 else R.string.cd_next,
                actionIntent(
                    if (isAbs) AudioWidgetIntents.ACTION_REWIND else AudioWidgetIntents.ACTION_NEXT
                ),
                style,
            )
            return
        }
        bindControl(
            views,
            R.id.widget_previous,
            if (isAbs) R.drawable.ic_rewind_backward_30 else R.drawable.ic_player_skip_back,
            if (isAbs) R.string.cd_rewind_30 else R.string.cd_previous,
            actionIntent(
                if (isAbs) AudioWidgetIntents.ACTION_REWIND else AudioWidgetIntents.ACTION_PREVIOUS
            ),
            style,
        )
        bindControl(
            views,
            R.id.widget_next,
            if (isAbs) R.drawable.ic_rewind_forward_30 else R.drawable.ic_player_skip_forward,
            if (isAbs) R.string.cd_forward_30 else R.string.cd_next,
            actionIntent(
                if (isAbs) AudioWidgetIntents.ACTION_FORWARD else AudioWidgetIntents.ACTION_NEXT
            ),
            style,
        )
    }

    private fun bindPlay(views: RemoteViews, state: AudioWidgetState, style: Style) {
        val isPlaying = state.status == AudioWidgetStatus.PLAYING
        views.setImageViewResource(
            R.id.widget_play,
            when {
                isPlaying && style.lightText -> R.drawable.widget_pause_circle
                isPlaying -> R.drawable.widget_pause_circle_dark
                style.lightText -> R.drawable.widget_play_circle
                else -> R.drawable.widget_play_circle_dark
            },
        )
        when {
            state.status != AudioWidgetStatus.STOPPED -> {
                views.setContentDescription(
                    R.id.widget_play,
                    context.getString(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                )
                views.setOnClickPendingIntent(
                    R.id.widget_play,
                    actionIntent(AudioWidgetIntents.ACTION_PLAY_PAUSE),
                )
            }
            state.isSignedIn -> {
                views.setContentDescription(
                    R.id.widget_play,
                    context.getString(R.string.cd_shuffle_play),
                )
                views.setOnClickPendingIntent(
                    R.id.widget_play,
                    if (state.isCasting) castRemoteIntent()
                    else actionIntent(AudioWidgetIntents.ACTION_SHUFFLE_ALL),
                )
            }
            else -> {
                views.setContentDescription(R.id.widget_play, context.getString(R.string.cd_play))
                views.setOnClickPendingIntent(
                    R.id.widget_play,
                    openIntent(null, OPEN_APP_REQUEST_CODE),
                )
            }
        }
    }

    private fun bindContinue(
        views: RemoteViews,
        state: AudioWidgetState,
        rows: Int,
        style: Style,
    ) {
        val items = state.continueItems.take(rows)
        views.setViewVisibility(
            R.id.widget_continue,
            if (items.isEmpty()) View.GONE else View.VISIBLE,
        )
        views.setTextColor(R.id.widget_continue_label, style.secondaryTextColor)
        CONTINUE_SLOTS.forEachIndexed { index, slot ->
            val item = items.getOrNull(index)
            if (item == null) {
                views.setViewVisibility(slot.row, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(slot.row, View.VISIBLE)
            views.setTextViewText(slot.title, item.title)
            views.setTextColor(slot.title, style.textColor)
            if (item.subtitle.isNullOrBlank()) {
                views.setViewVisibility(slot.subtitle, View.GONE)
            } else {
                views.setTextViewText(slot.subtitle, item.subtitle)
                views.setTextColor(slot.subtitle, style.secondaryTextColor)
                views.setViewVisibility(slot.subtitle, View.VISIBLE)
            }
            views.setContentDescription(
                slot.row,
                context.getString(R.string.widget_resume_fmt, item.title),
            )
            views.setOnClickPendingIntent(
                slot.row,
                if (state.isCasting) castRemoteIntent()
                else continueIntent(item, CONTINUE_REQUEST_CODE + index),
            )
        }
    }

    private fun setOptionalText(views: RemoteViews, viewId: Int, text: String?, style: Style) {
        if (text.isNullOrBlank()) {
            views.setViewVisibility(viewId, View.GONE)
        } else {
            views.setTextViewText(viewId, text)
            views.setTextColor(viewId, style.textColor)
            views.setViewVisibility(viewId, View.VISIBLE)
        }
    }

    private fun bindControl(
        views: RemoteViews,
        viewId: Int,
        icon: Int,
        description: Int,
        intent: PendingIntent,
        style: Style,
    ) {
        views.setImageViewResource(viewId, icon)
        views.setColorStateList(
            viewId,
            "setImageTintList",
            ColorStateList.valueOf(style.textColor),
        )
        views.setContentDescription(viewId, context.getString(description))
        views.setOnClickPendingIntent(viewId, intent)
    }

    private fun actionIntent(action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, AudioWidgetActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun continueIntent(item: ContinueItem, requestCode: Int): PendingIntent {
        val intent =
            Intent(context, AudioWidgetActionReceiver::class.java).apply {
                when (item.engine) {
                    AudioWidgetEngine.MUSIC -> action = AudioWidgetIntents.ACTION_RESUME_MUSIC
                    AudioWidgetEngine.ABS -> {
                        action = AudioWidgetIntents.ACTION_PLAY_ABS_ITEM
                        putExtra(AudioWidgetIntents.EXTRA_ABS_ITEM_ID, item.absItemId)
                        putExtra(AudioWidgetIntents.EXTRA_ABS_EPISODE_ID, item.absEpisodeId)
                    }
                }
            }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun castRemoteIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            CAST_REMOTE_REQUEST_CODE,
            Intent(context, CastRemoteRouterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openIntent(loaded: AudioWidgetState?, requestCode: Int): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                when (loaded?.engine) {
                    AudioWidgetEngine.MUSIC ->
                        putExtra(
                            AudioWidgetIntents.EXTRA_OPEN_PLAYER,
                            AudioWidgetIntents.PLAYER_MUSIC,
                        )
                    AudioWidgetEngine.ABS ->
                        loaded.absItemId?.let { itemId ->
                            putExtra(
                                AudioWidgetIntents.EXTRA_OPEN_PLAYER,
                                AudioWidgetIntents.PLAYER_ABS,
                            )
                            putExtra(AudioWidgetIntents.EXTRA_ABS_ITEM_ID, itemId)
                            putExtra(AudioWidgetIntents.EXTRA_ABS_EPISODE_ID, loaded.absEpisodeId)
                        }
                    null -> Unit
                }
            }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val OPEN_PLAYER_REQUEST_CODE = 4100
        const val OPEN_APP_REQUEST_CODE = 4101
        const val CAST_REMOTE_REQUEST_CODE = 4102
        const val CONTINUE_REQUEST_CODE = 4200

        val CONTINUE_SLOTS =
            listOf(
                ContinueSlot(
                    R.id.widget_continue_row_1,
                    R.id.widget_continue_title_1,
                    R.id.widget_continue_subtitle_1,
                ),
                ContinueSlot(
                    R.id.widget_continue_row_2,
                    R.id.widget_continue_title_2,
                    R.id.widget_continue_subtitle_2,
                ),
                ContinueSlot(
                    R.id.widget_continue_row_3,
                    R.id.widget_continue_title_3,
                    R.id.widget_continue_subtitle_3,
                ),
            )
    }
}
