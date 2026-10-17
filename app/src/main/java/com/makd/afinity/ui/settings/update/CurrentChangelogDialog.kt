package com.makd.afinity.ui.settings.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R

@Composable
fun CurrentChangelogDialog(
    onDismiss: () -> Unit,
    viewModel: CurrentChangelogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.load() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        dismissButton =
            if (state == CurrentChangelogState.Failed) {
                {
                    TextButton(onClick = viewModel::load) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            } else null,
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_git_compare),
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
                    text = stringResource(R.string.changelog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = viewModel.tagName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier.background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.large,
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        },
        text = {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 480.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                when (val current = state) {
                    is CurrentChangelogState.Loaded ->
                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp)
                        ) {
                            ProvideTextStyle(value = MaterialTheme.typography.bodyMedium) {
                                ChangelogContent(
                                    body = current.release.body.orEmpty(),
                                    tagName = current.release.tagName,
                                )
                            }
                        }
                    CurrentChangelogState.Loading ->
                        Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    CurrentChangelogState.NotPublished ->
                        ChangelogMessage(text = stringResource(R.string.changelog_not_published))
                    CurrentChangelogState.Failed ->
                        ChangelogMessage(text = stringResource(R.string.changelog_load_failed))
                }
            }
        },
    )
}

@Composable
private fun ChangelogMessage(text: String) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(24.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
