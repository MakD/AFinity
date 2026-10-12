package com.makd.afinity.ui.settings.servers.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.player.components.transcodeReasonRes
import java.util.Locale
import org.jellyfin.sdk.model.api.HardwareAccelerationType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.SessionInfoDto

private const val BITS_PER_MEGABIT = 1_000_000.0

@StringRes
internal fun SessionInfoDto.playMethodLabelRes(): Int? {
    if (nowPlayingItem == null) return null
    val info = transcodingInfo
    return when {
        info != null && info.isVideoDirect && info.isAudioDirect -> R.string.session_method_remux
        info != null && info.isVideoDirect -> R.string.session_method_direct_stream
        info != null || playState?.playMethod == PlayMethod.TRANSCODE ->
            R.string.session_method_transcode
        playState?.playMethod == PlayMethod.DIRECT_STREAM -> R.string.session_method_direct_stream
        playState?.playMethod == PlayMethod.DIRECT_PLAY -> R.string.session_method_direct_play
        else -> null
    }
}

@Composable
internal fun SessionPlaybackDetails(session: SessionInfoDto, modifier: Modifier = Modifier) {
    val methodRes = session.playMethodLabelRes() ?: return
    val info = session.transcodingInfo
    val reasons =
        info
            ?.transcodeReasons
            .orEmpty()
            .map { stringResource(transcodeReasonRes(it)) }
            .distinct()
            .joinToString(", ")

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(methodRes),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            if (reasons.isNotEmpty()) {
                DetailLine(text = reasons)
            }
            if (info != null) {
                info.videoCodec?.let { codec ->
                    DetailLine(
                        text =
                            stringResource(
                                if (info.isVideoDirect) R.string.session_video_direct_fmt
                                else R.string.session_video_transcoded_fmt,
                                codec.uppercase(Locale.ROOT),
                            )
                    )
                }
                info.audioCodec?.let { codec ->
                    DetailLine(
                        text =
                            stringResource(
                                if (info.isAudioDirect) R.string.session_audio_direct_fmt
                                else R.string.session_audio_transcoded_fmt,
                                codec.uppercase(Locale.ROOT),
                            )
                    )
                }
                info.bitrate
                    ?.takeIf { it > 0 }
                    ?.let { bitrate ->
                        val megabits =
                            String.format(Locale.getDefault(), "%.1f", bitrate / BITS_PER_MEGABIT)
                        DetailLine(text = stringResource(R.string.session_bitrate_fmt, megabits))
                    }
                info.hardwareAccelerationType
                    ?.takeIf { it != HardwareAccelerationType.NONE }
                    ?.let { type ->
                        DetailLine(
                            text =
                                stringResource(
                                    R.string.session_hardware_fmt,
                                    type.serialName.uppercase(Locale.ROOT),
                                )
                        )
                    }
            }
        }
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
