package com.makd.afinity.ui.requests

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.ui.components.LocalImageUrlRewriter
import com.makd.afinity.util.rememberSeerrImageProxy

@Composable
fun SeerrImages(content: @Composable () -> Unit) {
    val proxy = rememberSeerrImageProxy()
    val enabled by proxy.enabled.collectAsStateWithLifecycle()
    val rewriter: ((String) -> String)? =
        remember(proxy, enabled) { if (enabled) proxy::rewrite else null }
    CompositionLocalProvider(LocalImageUrlRewriter provides rewriter, content = content)
}
