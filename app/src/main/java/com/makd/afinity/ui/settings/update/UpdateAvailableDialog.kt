package com.makd.afinity.ui.settings.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import com.makd.afinity.R
import com.makd.afinity.data.updater.models.ApkVerification
import com.makd.afinity.data.updater.models.GitHubRelease
import java.io.File

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun UpdateAvailableDialog(
    currentVersionName: String,
    release: GitHubRelease,
    downloadedFile: File? = null,
    isDownloading: Boolean,
    verification: ApkVerification = ApkVerification.UNVERIFIED,
    expectedSha256: String? = null,
    computedSha256: String? = null,
    onDownload: () -> Unit,
    onVerify: () -> Unit = {},
    onInstall: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val effectiveFile = if (verification == ApkVerification.FAILED) null else downloadedFile
    val installReady =
        effectiveFile != null &&
            (verification == ApkVerification.VERIFIED ||
                verification == ApkVerification.UNAVAILABLE)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_system_update),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.update_available_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier =
                        Modifier.background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.large,
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.update_version_fmt, currentVersionName),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_right),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = release.tagName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.update_whats_new),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                val consoleVisible =
                    verification == ApkVerification.VERIFYING ||
                        verification == ApkVerification.VERIFIED ||
                        verification == ApkVerification.FAILED

                Column(
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                        ) {
                            ProvideTextStyle(value = MaterialTheme.typography.bodyMedium) {
                                RichText(modifier = Modifier.fillMaxWidth()) {
                                    Markdown(content = formatChangelog(release.body ?: ""))
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = consoleVisible,
                        enter =
                            expandVertically(
                                animationSpec = tween(400, easing = FastOutSlowInEasing)
                            ) + fadeIn(tween(400)),
                        exit =
                            shrinkVertically(
                                animationSpec = tween(400, easing = FastOutSlowInEasing)
                            ) + fadeOut(tween(400)),
                    ) {
                        VerificationConsole(
                            fileName = downloadedFile?.name ?: release.tagName,
                            expectedSha256 = expectedSha256,
                            computedSha256 = computedSha256,
                            verification = verification,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (downloadedFile != null && verification != ApkVerification.UNAVAILABLE) {
                    ValidateButton(
                        verification = verification,
                        onVerify = onVerify,
                        modifier = Modifier.weight(1f),
                    )
                }

                Button(
                    onClick = {
                        if (effectiveFile != null) {
                            if (installReady) onInstall(effectiveFile)
                        } else if (!isDownloading) {
                            onDownload()
                        }
                    },
                    enabled = if (effectiveFile != null) installReady else !isDownloading,
                    modifier = Modifier.weight(1f),
                ) {
                    AnimatedContent(
                        targetState =
                            when {
                                effectiveFile != null -> UpdateButtonState.INSTALL
                                isDownloading -> UpdateButtonState.DOWNLOADING
                                else -> UpdateButtonState.DOWNLOAD
                            },
                        label = "ButtonAnimation",
                        transitionSpec = {
                            slideInVertically { height -> height } + fadeIn() togetherWith
                                slideOutVertically { height -> -height } + fadeOut()
                        },
                    ) { state ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            when (state) {
                                UpdateButtonState.INSTALL -> {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_system_update),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.btn_install_restart))
                                }

                                UpdateButtonState.DOWNLOADING -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.btn_downloading))
                                }

                                UpdateButtonState.DOWNLOAD -> {
                                    Icon(
                                        painter =
                                            painterResource(id = R.drawable.ic_download_arrow),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.btn_download_update))
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) }
        },
    )
}

private enum class UpdateButtonState {
    DOWNLOAD,
    DOWNLOADING,
    INSTALL,
}

@Composable
private fun ValidateButton(
    verification: ApkVerification,
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val errorContainer = MaterialTheme.colorScheme.errorContainer
    val onErrorContainer = MaterialTheme.colorScheme.onErrorContainer
    val successContainer = Color(0xFFD1E7DD)
    val onSuccessContainer = Color(0xFF0F5132)
    val defaultContainer = MaterialTheme.colorScheme.secondaryContainer
    val defaultContent = MaterialTheme.colorScheme.onSecondaryContainer

    val containerColor by
        animateColorAsState(
            targetValue =
                when (verification) {
                    ApkVerification.VERIFIED -> successContainer
                    ApkVerification.FAILED -> errorContainer
                    else -> defaultContainer
                },
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            label = "ValidateContainerColor",
        )

    val contentColor by
        animateColorAsState(
            targetValue =
                when (verification) {
                    ApkVerification.VERIFIED -> onSuccessContainer
                    ApkVerification.FAILED -> onErrorContainer
                    else -> defaultContent
                },
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            label = "ValidateContentColor",
        )

    Button(
        onClick = onVerify,
        enabled = verification == ApkVerification.UNVERIFIED,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor,
                disabledContentColor = contentColor,
            ),
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = verification == ApkVerification.UNVERIFIED,
            label = "ValidateButtonContent",
            transitionSpec = {
                (scaleIn(initialScale = 0.7f, animationSpec = tween(400)) +
                    fadeIn(tween(400))) togetherWith
                    (scaleOut(targetScale = 0.7f, animationSpec = tween(400)) + fadeOut(tween(400)))
            },
        ) { idle ->
            if (idle) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_security),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_validate))
                }
            } else {
                val composition by
                    rememberLottieComposition(LottieCompositionSpec.Asset("anim_verify.lottie"))

                val clipSpec =
                    when (verification) {
                        ApkVerification.VERIFIED -> LottieClipSpec.Frame(min = 60, max = 89)
                        ApkVerification.FAILED -> LottieClipSpec.Frame(min = 90, max = 138)
                        else -> LottieClipSpec.Frame(min = 0, max = 60)
                    }

                val progress by
                    animateLottieCompositionAsState(
                        composition = composition,
                        clipSpec = clipSpec,
                        iterations =
                            if (verification == ApkVerification.VERIFYING) {
                                LottieConstants.IterateForever
                            } else 1,
                    )

                val dynamicProperties =
                    rememberLottieDynamicProperties(
                        rememberLottieDynamicProperty(
                            property = LottieProperty.STROKE_COLOR,
                            value = contentColor.toArgb(),
                            keyPath = arrayOf("**"),
                        )
                    )

                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    dynamicProperties = dynamicProperties,
                    asyncUpdates = AsyncUpdates.ENABLED,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

private val ConsoleBackground = Color(0xFF11151C)
private val ConsoleText = Color(0xFFE6EDF3)
private val ConsoleDim = Color(0xFF7D8590)
private val ConsoleGreen = Color(0xFF3FB950)
private val ConsoleRed = Color(0xFFF85149)

@Composable
private fun VerificationConsole(
    fileName: String,
    expectedSha256: String?,
    computedSha256: String?,
    verification: ApkVerification,
) {
    val mono =
        MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = ConsoleBackground,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "$ sha256sum $fileName", style = mono, color = ConsoleDim)

            Text(text = "expected: ${expectedSha256 ?: "—"}", style = mono, color = ConsoleText)

            if (verification == ApkVerification.VERIFYING) {
                val cursorAlpha by
                    rememberInfiniteTransition(label = "ConsoleCursor")
                        .animateFloat(
                            initialValue = 1f,
                            targetValue = 0f,
                            animationSpec =
                                infiniteRepeatable(
                                    animation = tween(durationMillis = 500),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                            label = "ConsoleCursorAlpha",
                        )
                Row {
                    Text(text = "computed: ", style = mono, color = ConsoleText)
                    Text(
                        text = "█",
                        style = mono,
                        color = ConsoleText.copy(alpha = cursorAlpha),
                    )
                }
            } else {
                Text(
                    text = "computed: ${computedSha256 ?: "—"}",
                    style = mono,
                    color = ConsoleText,
                )
            }

            val statusText: String
            val statusColor: Color
            when (verification) {
                ApkVerification.VERIFYING -> {
                    statusText = stringResource(R.string.update_console_status_hashing)
                    statusColor = ConsoleDim
                }

                ApkVerification.VERIFIED -> {
                    statusText = stringResource(R.string.update_console_status_match)
                    statusColor = ConsoleGreen
                }

                ApkVerification.FAILED -> {
                    statusText =
                        if (computedSha256 == null) {
                            stringResource(R.string.update_console_status_error)
                        } else {
                            stringResource(R.string.update_console_status_mismatch)
                        }
                    statusColor = ConsoleRed
                }

                else -> {
                    statusText = ""
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

private fun formatChangelog(text: String): String {
    var formatted = text

    val issueRegex = "https://github\\.com/[^/]+/[^/]+/(?:pull|issues)/(\\d+)".toRegex()
    formatted =
        issueRegex.replace(formatted) { matchResult ->
            val url = matchResult.value
            val number = matchResult.groupValues[1]
            "[$number]($url)"
        }

    val commitRegex = "https://github\\.com/[^/]+/[^/]+/commit/([0-9a-f]{7})[0-9a-f]*".toRegex()
    formatted =
        commitRegex.replace(formatted) { matchResult ->
            val url = matchResult.value
            val shortHash = matchResult.groupValues[1]
            "[`$shortHash`]($url)"
        }

    val compareRegex =
        "https://github\\.com/[^/]+/[^/]+/compare/([\\w\\d\\.-]+)\\.\\.\\.([\\w\\d\\.-]+)".toRegex()
    formatted =
        compareRegex.replace(formatted) { matchResult ->
            val url = matchResult.value
            val oldVersion = matchResult.groupValues[1]
            val newVersion = matchResult.groupValues[2]
            "[$oldVersion...$newVersion]($url)"
        }

    val userRegex = "https://github\\.com/([a-zA-Z0-9-]+)(?=[\\s)]|$)".toRegex()
    formatted =
        userRegex.replace(formatted) { matchResult ->
            val url = matchResult.value
            val user = matchResult.groupValues[1]
            "[@$user]($url)"
        }

    return formatted
}
