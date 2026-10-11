package com.makd.afinity.ui.admin.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.makd.afinity.R
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScaffold(
    title: String,
    onBackClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val playerOffset = LocalPlayerOffset.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_chevron_left),
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
            )
        },
        snackbarHost = {
            if (snackbarHostState != null) {
                SnackbarHost(hostState = snackbarHostState, snackbar = { AFinitySnackbar(it) })
            }
        },
        floatingActionButton = {
            Box(modifier = Modifier.padding(bottom = playerOffset)) { floatingActionButton() }
        },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current
        content(
            PaddingValues(
                top = paddingValues.calculateTopPadding(),
                start = paddingValues.calculateStartPadding(layoutDirection),
                end = paddingValues.calculateEndPadding(layoutDirection),
                bottom = max(paddingValues.calculateBottomPadding(), playerOffset),
            )
        )
    }
}

@Composable
fun <T : Any> AdminLoadContent(
    data: T?,
    failed: Boolean,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
    content: @Composable (T) -> Unit,
) {
    when {
        data != null -> content(data)
        failed ->
            FullScreenError(
                message = stringResource(R.string.error_load_content_generic),
                modifier = Modifier.padding(contentPadding),
                actionText = stringResource(R.string.action_retry),
                onActionClick = onRetry,
            )
        else -> FullScreenLoading(modifier = Modifier.padding(contentPadding))
    }
}

@Composable
fun AdminPill(text: String, modifier: Modifier = Modifier, emphasised: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color =
            if (emphasised) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (emphasised) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
