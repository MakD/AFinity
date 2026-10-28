package com.makd.afinity.ui.components.filter

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.makd.afinity.R
import com.makd.afinity.ui.components.windowHeightDp

@Composable
fun FilterDialog(
    onDismiss: () -> Unit,
    onClearAll: () -> Unit,
    onApply: () -> Unit,
    clearAllEnabled: Boolean = true,
    bodyBottomPadding: Dp = 16.dp,
    title: String = stringResource(R.string.discover_filter_title),
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxDialogHeight = windowHeightDp() * 0.92f

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = true,
            ),
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize().imePadding().pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier.fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .heightIn(max = maxDialogHeight)
                        .padding(16.dp)
                        .pointerInput(Unit) { detectTapGestures {} },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier =
                            Modifier.weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(
                                    start = 24.dp,
                                    end = 24.dp,
                                    top = 24.dp,
                                    bottom = bodyBottomPadding,
                                )
                    ) {
                        Text(
                            text = title,
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                        )
                        content()
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onClearAll, enabled = clearAllEnabled) {
                            Text(stringResource(R.string.discover_filter_clear_all))
                        }
                        Button(onClick = onApply) { Text(stringResource(R.string.action_apply)) }
                    }
                }
            }
        }
    }
}
