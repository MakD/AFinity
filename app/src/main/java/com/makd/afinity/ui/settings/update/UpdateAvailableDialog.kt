package com.makd.afinity.ui.settings.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material3.RichText
import com.makd.afinity.R
import com.makd.afinity.data.updater.models.GitHubRelease
import java.io.File

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun UpdateAvailableDialog(
    currentVersionName: String,
    release: GitHubRelease,
    downloadedFile: File? = null,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_system_update),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },

        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.update_available_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.update_version_fmt, currentVersionName),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_right),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = release.tagName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.update_whats_new),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height(400.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ProvideTextStyle(value = MaterialTheme.typography.bodyMedium) {
                            RichText(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Markdown(content = formatChangelog(release.body ?: ""))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (downloadedFile != null) {
                        onInstall(downloadedFile)
                    } else if (!isDownloading) {
                        onDownload()
                    }
                },
                enabled = downloadedFile != null || !isDownloading,
                modifier = Modifier.fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = when {
                        downloadedFile != null -> UpdateButtonState.INSTALL
                        isDownloading -> UpdateButtonState.DOWNLOADING
                        else -> UpdateButtonState.DOWNLOAD
                    },
                    label = "ButtonAnimation",
                    transitionSpec = {
                        slideInVertically { height -> height } + fadeIn() togetherWith
                                slideOutVertically { height -> -height } + fadeOut()
                    }
                ) { state ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        when (state) {
                            UpdateButtonState.INSTALL -> {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_system_update),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.btn_install_restart))
                            }

                            UpdateButtonState.DOWNLOADING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.btn_downloading))
                            }

                            UpdateButtonState.DOWNLOAD -> {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_download_arrow),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.btn_download_update))
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_not_now))
            }
        }
    )
}

private enum class UpdateButtonState {
    DOWNLOAD, DOWNLOADING, INSTALL
}

private fun formatChangelog(text: String): String {
    var formatted = text

    val issueRegex = "https://github\\.com/[^/]+/[^/]+/(?:pull|issues)/(\\d+)".toRegex()
    formatted = issueRegex.replace(formatted) { matchResult ->
        val url = matchResult.value
        val number = matchResult.groupValues[1]
        "[$number]($url)"
    }

    val commitRegex = "https://github\\.com/[^/]+/[^/]+/commit/([0-9a-f]{7})[0-9a-f]*".toRegex()
    formatted = commitRegex.replace(formatted) { matchResult ->
        val url = matchResult.value
        val shortHash = matchResult.groupValues[1]
        "[`$shortHash`]($url)"
    }

    val compareRegex =
        "https://github\\.com/[^/]+/[^/]+/compare/([\\w\\d\\.-]+)\\.\\.\\.([\\w\\d\\.-]+)".toRegex()
    formatted = compareRegex.replace(formatted) { matchResult ->
        val url = matchResult.value
        val oldVersion = matchResult.groupValues[1]
        val newVersion = matchResult.groupValues[2]
        "[$oldVersion...$newVersion]($url)"
    }

    val userRegex = "https://github\\.com/([a-zA-Z0-9-]+)(?=[\\s)]|$)".toRegex()
    formatted = userRegex.replace(formatted) { matchResult ->
        val url = matchResult.value
        val user = matchResult.groupValues[1]
        "[@$user]($url)"
    }

    return formatted
}