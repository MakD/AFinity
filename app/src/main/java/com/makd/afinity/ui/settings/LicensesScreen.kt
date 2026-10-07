package com.makd.afinity.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.makd.afinity.R
import com.makd.afinity.navigation.LocalPlayerOffset
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.m3.style.accentDerivedLicenseHueResolver
import com.mikepenz.aboutlibraries.ui.compose.m3.style.m3VariantColors
import com.mikepenz.aboutlibraries.ui.compose.variant.LibrariesDensity
import com.mikepenz.aboutlibraries.ui.compose.variant.LibrariesVariant
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryActionMode
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryBadges
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryDetailMode

private data class AboutLink(val iconRes: Int, val labelRes: Int, val url: String)

private val aboutLinks =
    listOf(
        AboutLink(
            R.drawable.ic_brand_github,
            R.string.about_link_github,
            "https://github.com/MakD/AFinity",
        ),
        AboutLink(
            R.drawable.ic_brand_discord,
            R.string.about_link_discord,
            "https://discord.gg/v7P9CAvCKZ",
        ),
        AboutLink(
            R.drawable.ic_brand_kofi,
            R.string.about_link_kofi,
            "https://ko-fi.com/m0rph3us",
        ),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    val libraries by produceLibraries(R.raw.aboutlibraries)
    val playerOffset = LocalPlayerOffset.current
    val uriHandler = LocalUriHandler.current
    val bannerRes =
        if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
            R.drawable.afinity_banner_light
        } else {
            R.drawable.afinity_banner
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.pref_group_about),
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_left),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val customPadding =
            PaddingValues(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = max(innerPadding.calculateBottomPadding(), playerOffset),
            )
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.fillMaxSize(),
            detailMode = LibraryDetailMode.Sheet,
            variant = LibrariesVariant.Refined,
            density = LibrariesDensity.Compact,
            actionMode = LibraryActionMode.Icons,
            badges =
                LibraryBadges(
                    version = true,
                    license = true,
                    author = false,
                    description = false,
                    funding = false,
                ),
            contentPadding =
                PaddingValues(
                    top = customPadding.calculateTopPadding() + 16.dp,
                    start = customPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = customPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    bottom = customPadding.calculateBottomPadding() + 16.dp,
                ),
            colors =
                LibraryDefaults.libraryColors(
                    libraryBackgroundColor = MaterialTheme.colorScheme.surface,
                    libraryContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            variantColors =
                LibraryDefaults.m3VariantColors(
                    licenseHueResolver = accentDerivedLicenseHueResolver()
                ),
            header = {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(
                                painter = painterResource(bannerRes),
                                contentDescription = stringResource(R.string.app_name),
                                contentScale = ContentScale.Fit,
                                modifier =
                                    Modifier.widthIn(max = 340.dp)
                                        .fillMaxWidth()
                                        .aspectRatio(1200f / 423f),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                aboutLinks.forEach { link ->
                                    FilledTonalIconButton(
                                        onClick = { uriHandler.openUri(link.url) }
                                    ) {
                                        Icon(
                                            painter = painterResource(link.iconRes),
                                            contentDescription = stringResource(link.labelRes),
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(R.string.licenses_header_title),
                                style =
                                    MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.licenses_header_subtitle),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            },
            divider = { Spacer(modifier = Modifier.height(12.dp)) },
            footer = {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.licenses_footer_built_with),
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.licenses_footer_terms),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            },
        )
    }
}
