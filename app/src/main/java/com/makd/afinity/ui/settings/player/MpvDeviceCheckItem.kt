package com.makd.afinity.ui.settings.player

import android.view.Display
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.AsyncUpdates
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.makd.afinity.R
import com.makd.afinity.player.mpv.MpvDeviceCapabilities
import com.makd.afinity.player.mpv.MpvDeviceSupport
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsItem

enum class MpvDeviceCheckStatus {
    IDLE,
    CHECKING,
    SUPPORTED,
    LIMITED,
    UNSUPPORTED,
}

data class MpvDeviceCheck(
    val status: MpvDeviceCheckStatus = MpvDeviceCheckStatus.IDLE,
    val capabilities: MpvDeviceCapabilities? = null,
)

fun MpvDeviceCapabilities?.toCheckStatus(): MpvDeviceCheckStatus {
    val capabilities = this ?: return MpvDeviceCheckStatus.UNSUPPORTED
    return when {
        !capabilities.vulkanSupported -> MpvDeviceCheckStatus.UNSUPPORTED
        capabilities.hdrTypes.isEmpty() -> MpvDeviceCheckStatus.LIMITED
        else -> MpvDeviceCheckStatus.SUPPORTED
    }
}

@Composable
fun MpvDeviceCheckEntry(
    visible: Boolean,
    check: MpvDeviceCheck,
    onStart: () -> Unit,
    onFinish: () -> Unit,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = EnterTransition.None,
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column {
            SettingsDivider()
            SettingsItem(
                icon = painterResource(id = R.drawable.ic_hdr),
                title = stringResource(R.string.pref_mpv_device_check_title),
                subtitle = stringResource(R.string.pref_mpv_device_check_summary),
                onClick = { showDialog = true },
            )
        }
    }

    if (showDialog) {
        MpvDeviceCheckDialog(
            check = check,
            onStart = onStart,
            onClose = {
                showDialog = false
                onFinish()
            },
        )
    }
}

@Composable
private fun MpvDeviceCheckDialog(check: MpvDeviceCheck, onStart: () -> Unit, onClose: () -> Unit) {
    LaunchedEffect(Unit) { if (check.status == MpvDeviceCheckStatus.IDLE) onStart() }

    AlertDialog(
        onDismissRequest = onClose,
        icon = { DeviceCheckAnimation(status = check.status) },
        title = { Text(text = stringResource(R.string.pref_mpv_device_check_title)) },
        text = { DeviceCheckConsole(check = check) },
        confirmButton = {
            TextButton(onClick = onClose) { Text(text = stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun DeviceCheckAnimation(status: MpvDeviceCheckStatus) {
    val color by
        animateColorAsState(
            targetValue =
                when (status) {
                    MpvDeviceCheckStatus.SUPPORTED -> ConsoleGreen
                    MpvDeviceCheckStatus.UNSUPPORTED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            label = "DeviceCheckIconColor",
        )

    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("anim_verify.lottie"))

    val clipSpec =
        when (status) {
            MpvDeviceCheckStatus.SUPPORTED,
            MpvDeviceCheckStatus.LIMITED -> LottieClipSpec.Frame(min = 60, max = 89)
            MpvDeviceCheckStatus.UNSUPPORTED -> LottieClipSpec.Frame(min = 90, max = 138)
            else -> LottieClipSpec.Frame(min = 0, max = 60)
        }

    val progress by
        animateLottieCompositionAsState(
            composition = composition,
            clipSpec = clipSpec,
            iterations =
                if (
                    status == MpvDeviceCheckStatus.CHECKING || status == MpvDeviceCheckStatus.IDLE
                ) {
                    LottieConstants.IterateForever
                } else 1,
        )

    val dynamicProperties =
        rememberLottieDynamicProperties(
            rememberLottieDynamicProperty(
                property = LottieProperty.STROKE_COLOR,
                value = color.toArgb(),
                keyPath = arrayOf("**"),
            )
        )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        dynamicProperties = dynamicProperties,
        asyncUpdates = AsyncUpdates.ENABLED,
        modifier = Modifier.size(48.dp),
    )
}

private val ConsoleBackground = Color(0xFF11151C)
private val ConsoleText = Color(0xFFE6EDF3)
private val ConsoleDim = Color(0xFF7D8590)
private val ConsoleGreen = Color(0xFF3FB950)
private val ConsoleRed = Color(0xFFF85149)

@Composable
private fun DeviceCheckConsole(check: MpvDeviceCheck, modifier: Modifier = Modifier) {
    val mono =
        MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )
    val capabilities = check.capabilities

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = ConsoleBackground,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "$ device-check --vulkan --hdr", style = mono, color = ConsoleDim)

            if (
                check.status == MpvDeviceCheckStatus.CHECKING ||
                    check.status == MpvDeviceCheckStatus.IDLE
            ) {
                val cursorAlpha by
                    rememberInfiniteTransition(label = "DeviceCheckCursor")
                        .animateFloat(
                            initialValue = 1f,
                            targetValue = 0f,
                            animationSpec =
                                infiniteRepeatable(
                                    animation = tween(durationMillis = 500),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                            label = "DeviceCheckCursorAlpha",
                        )
                Row {
                    Text(text = "vulkan:   ", style = mono, color = ConsoleText)
                    Text(
                        text = "█",
                        style = mono,
                        color = ConsoleText.copy(alpha = cursorAlpha),
                    )
                }
            } else {
                val vulkanVersion =
                    capabilities?.vulkanVersion
                        ?: stringResource(R.string.mpv_device_check_vulkan_missing)
                val needs =
                    stringResource(
                        R.string.mpv_device_check_vulkan_needs,
                        MpvDeviceSupport.MIN_VULKAN_VERSION_LABEL,
                    )
                Text(
                    text = "vulkan:   $vulkanVersion $needs",
                    style = mono,
                    color = if (capabilities?.vulkanSupported == true) ConsoleGreen else ConsoleRed,
                )

                val hdrLabels =
                    capabilities?.hdrTypes.orEmpty().mapNotNull(::hdrTypeLabel).distinct()
                Text(
                    text =
                        "hdr:      " +
                            hdrLabels.joinToString().ifEmpty {
                                stringResource(R.string.mpv_device_check_hdr_none)
                            },
                    style = mono,
                    color = if (hdrLabels.isNotEmpty()) ConsoleGreen else ConsoleDim,
                )
            }

            val statusText: String
            val statusColor: Color
            when (check.status) {
                MpvDeviceCheckStatus.SUPPORTED -> {
                    statusText = stringResource(R.string.mpv_device_check_status_supported)
                    statusColor = ConsoleGreen
                }

                MpvDeviceCheckStatus.LIMITED -> {
                    statusText = stringResource(R.string.mpv_device_check_status_limited)
                    statusColor = ConsoleText
                }

                MpvDeviceCheckStatus.UNSUPPORTED -> {
                    statusText =
                        stringResource(
                            R.string.mpv_device_check_status_unsupported,
                            MpvDeviceSupport.MIN_VULKAN_VERSION_LABEL,
                        )
                    statusColor = ConsoleRed
                }

                else -> {
                    statusText = stringResource(R.string.mpv_device_check_status_checking)
                    statusColor = ConsoleDim
                }
            }

            Text(
                text = "status:   $statusText",
                style = mono.copy(fontWeight = FontWeight.SemiBold),
                color = statusColor,
            )
        }
    }
}

private fun hdrTypeLabel(type: Int): String? =
    when (type) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
        else -> null
    }
