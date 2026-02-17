package com.makd.afinity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.QualityProfile
import com.makd.afinity.data.models.jellyseerr.RatingsCombined
import com.makd.afinity.data.models.jellyseerr.RequestStatus
import com.makd.afinity.data.models.jellyseerr.ServiceSettings
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestConfirmationDialog(
    modifier: Modifier = Modifier,
    mediaTitle: String,
    mediaPosterUrl: String?,
    mediaType: MediaType,
    availableSeasons: Int = 0,
    selectedSeasons: List<Int>,
    onSeasonsChange: (List<Int>) -> Unit,
    disabledSeasons: List<Int> = emptyList(),
    existingStatus: MediaStatus? = null,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isManagementMode: Boolean = false,
    requestStatus: RequestStatus? = null,
    onApprove: () -> Unit = {},
    onDecline: () -> Unit = {},
    onDelete: () -> Unit = {},
    onUpdate: () -> Unit = {},
    manageServerName: String? = null,
    manageProfileName: String? = null,
    manageRootFolder: String? = null,
    mediaBackdropUrl: String? = null,
    mediaTagline: String? = null,
    mediaOverview: String? = null,
    releaseDate: String? = null,
    runtime: Int? = null,
    voteAverage: Double? = null,
    certification: String? = null,
    originalLanguage: String? = null,
    director: String? = null,
    genres: List<String> = emptyList(),
    ratingsCombined: RatingsCombined? = null,
    can4k: Boolean = false,
    is4k: Boolean = false,
    onIs4kChange: (Boolean) -> Unit = {},
    canAdvanced: Boolean = false,
    availableServers: List<ServiceSettings> = emptyList(),
    selectedServer: ServiceSettings? = null,
    onServerSelected: (ServiceSettings) -> Unit = {},
    availableProfiles: List<QualityProfile> = emptyList(),
    selectedProfile: QualityProfile? = null,
    onProfileSelected: (QualityProfile) -> Unit = {},
    selectedRootFolder: String? = null,
    isLoadingServers: Boolean = false,
    isLoadingProfiles: Boolean = false,
) {
    val alreadyRequested =
        !isManagementMode &&
            existingStatus != null &&
            (mediaType == MediaType.MOVIE ||
                existingStatus == MediaStatus.AVAILABLE ||
                existingStatus == MediaStatus.PROCESSING)

    val headerImageUrl = mediaBackdropUrl?.takeIf { it.isNotBlank() } ?: mediaPosterUrl
    val scrollState = rememberScrollState()
    LaunchedEffect(mediaTitle) { scrollState.scrollTo(0) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                text = if (isManagementMode) "Manage Request" else "Request on Seerr",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
            ) {
                if (!headerImageUrl.isNullOrBlank()) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            imageUrl = headerImageUrl,
                            contentDescription = mediaTitle,
                            blurHash = null,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .height(200.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors =
                                                listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.8f),
                                                ),
                                            startY = 100f,
                                        )
                                    )
                        )
                        Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                            Text(
                                mediaTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!mediaTagline.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    mediaTagline,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                } else {
                    Column {
                        Text(
                            mediaTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!mediaTagline.isNullOrBlank())
                            Text(
                                mediaTagline,
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    data class MetaItem(
                        val icon: Int = 0,
                        val text: String,
                        val contentDesc: String? = null,
                        val tint: Color = Color.Unspecified,
                        val flagUrl: String? = null,
                    )
                    val metadataItems =
                        remember(ratingsCombined, voteAverage, originalLanguage, certification) {
                            buildList {
                                ratingsCombined?.imdb?.criticsScore?.let {
                                    add(
                                        MetaItem(
                                            R.drawable.ic_imdb_logo,
                                            String.format(Locale.US, "%.1f", it),
                                            "IMDb",
                                        )
                                    )
                                }
                                ratingsCombined?.rt?.criticsScore?.let {
                                    add(
                                        MetaItem(
                                            if (it >= 60) R.drawable.ic_rotten_tomato_fresh
                                            else R.drawable.ic_rotten_tomato_rotten,
                                            "$it%",
                                            "RT Critic",
                                        )
                                    )
                                }
                                ratingsCombined?.rt?.audienceScore?.let {
                                    add(
                                        MetaItem(
                                            if (it >= 60) R.drawable.ic_rt_fresh_popcorn
                                            else R.drawable.ic_rt_stale_popcorn,
                                            "$it%",
                                            "RT Audience",
                                        )
                                    )
                                }
                                voteAverage?.let {
                                    if (it > 0)
                                        add(
                                            MetaItem(
                                                R.drawable.ic_tmdb,
                                                "${(it * 10).toInt()}%",
                                                "TMDB",
                                            )
                                        )
                                }
                                originalLanguage
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let {
                                        add(
                                            MetaItem(
                                                R.drawable.ic_language,
                                                it.uppercase(),
                                                "Language",
                                                flagUrl = getAutoFlagUrl(it),
                                            )
                                        )
                                    }
                                certification
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { add(MetaItem(text = it)) }
                            }
                        }

                    if (metadataItems.isNotEmpty()) {
                        SeparatedFlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            separator = { MetadataDot() },
                        ) {
                            metadataItems.forEach { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (item.icon != 0)
                                        Icon(
                                            painter = painterResource(id = item.icon),
                                            contentDescription = null,
                                            tint =
                                                if (item.contentDesc == "Language")
                                                    MaterialTheme.colorScheme.onSurface
                                                else item.tint,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    if (item.flagUrl != null) CircleFlagIcon(url = item.flagUrl)
                                    else if (item.icon == 0) {
                                        androidx.compose.material3.Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            border =
                                                androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                                ),
                                            color = Color.Transparent,
                                        ) {
                                            Text(
                                                item.text,
                                                style =
                                                    MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                color =
                                                    MaterialTheme.colorScheme.onSurface.copy(0.9f),
                                                modifier =
                                                    Modifier.padding(
                                                        horizontal = 6.dp,
                                                        vertical = 2.dp,
                                                    ),
                                            )
                                        }
                                    } else
                                        Text(
                                            item.text,
                                            style =
                                                MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.9f),
                                        )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    if (!mediaOverview.isNullOrBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Overview",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                mediaOverview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    director
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("Director: ")
                                    }
                                    append(it)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.9f),
                            )
                        }
                    releaseDate?.let {
                        formatReleaseDate(it)
                            .takeIf { d -> d.isNotBlank() }
                            ?.let { d ->
                                Text(
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append("Release Date: ")
                                        }
                                        append(d)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.9f),
                                )
                            }
                    }
                    runtime
                        ?.takeIf { it > 0 }
                        ?.let {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("Runtime: ")
                                    }
                                    append(formatRuntime(it))
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.9f),
                            )
                        }
                    if (genres.isNotEmpty())
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("Genre: ")
                                }
                                append(genres.joinToString(", "))
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.9f),
                        )
                }

                if (isManagementMode) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Fulfillment Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        if (mediaType == MediaType.TV) {
                            if (selectedSeasons.isNotEmpty()) {
                                Text(
                                    text =
                                        buildAnnotatedString {
                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append("Requested Seasons: ")
                                            }
                                            append(selectedSeasons.sorted().joinToString(", "))
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            } else {
                                Text(
                                    text = "Seasons: All / First available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (requestStatus == RequestStatus.PENDING && can4k) {
                            MinimalSwitchTile(
                                title = "Request in 4K",
                                checked = is4k,
                                onCheckedChange = onIs4kChange,
                                icon = R.drawable.ic_4k,
                                iconSize = 18.dp,
                            )
                        } else if (is4k) {
                            Text(
                                text = "Quality: 4K UHD",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        if (requestStatus == RequestStatus.PENDING) {
                            if (isLoadingServers) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else if (availableServers.isEmpty()) {
                                Text(
                                    text =
                                        "No fulfillment servers available. Check your admin settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            } else {
                                Text(
                                    "Override Options",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp),
                                )

                                MinimalSelectionTile(
                                    label = "Destination Server",
                                    selectedText =
                                        selectedServer?.name ?: manageServerName ?: "Default",
                                    items = availableServers,
                                    itemText = { it.name },
                                    onItemSelected = onServerSelected,
                                    isLoading = isLoadingServers,
                                )

                                if (selectedServer != null) {
                                    MinimalSelectionTile(
                                        label = "Quality Profile",
                                        selectedText =
                                            selectedProfile?.name ?: manageProfileName ?: "Default",
                                        items = availableProfiles,
                                        itemText = { it.name },
                                        onItemSelected = onProfileSelected,
                                        isLoading = isLoadingProfiles,
                                    )

                                    selectedRootFolder?.let { folder ->
                                        Row(
                                            modifier =
                                                Modifier.fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                            .copy(0.5f)
                                                    )
                                                    .padding(16.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    "Root Folder",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color =
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Text(
                                                    folder,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            manageServerName?.let {
                                Text(
                                    text = "Server: $it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            manageProfileName?.let {
                                Text(
                                    text = "Profile: $it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            manageRootFolder?.let {
                                Text(
                                    text = "Root Folder: $it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else if (!alreadyRequested) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Request this ${mediaType.toApiString()} on Seerr?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )

                        if (mediaType == MediaType.TV) {
                            SeasonSelector(
                                availableSeasons,
                                selectedSeasons,
                                onSeasonsChange,
                                disabledSeasons,
                            )
                        }

                        if (can4k) {
                            MinimalSwitchTile(
                                "Request in 4K",
                                is4k,
                                onIs4kChange,
                                R.drawable.ic_4k,
                                18.dp,
                            )
                        }

                        if (canAdvanced) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "Advanced Options",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                                val serverLabel =
                                    if (mediaType == MediaType.MOVIE) "Radarr Server"
                                    else "Sonarr Server"
                                MinimalSelectionTile(
                                    serverLabel,
                                    selectedServer?.name ?: "Default",
                                    availableServers,
                                    { it.name },
                                    onServerSelected,
                                    isLoadingServers,
                                )
                                if (selectedServer != null) {
                                    MinimalSelectionTile(
                                        "Quality Profile",
                                        selectedProfile?.name ?: "Default",
                                        availableProfiles,
                                        { it.name },
                                        onProfileSelected,
                                        isLoadingProfiles,
                                    )
                                    selectedRootFolder?.let { folder ->
                                        Row(
                                            modifier =
                                                Modifier.fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                            .copy(0.5f)
                                                    )
                                                    .padding(16.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    "Root Folder",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color =
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Text(
                                                    folder,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isManagementMode) {
                if (requestStatus == RequestStatus.PENDING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onUpdate,
                            enabled = !isLoading,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary
                                ),
                        ) {
                            if (isLoading)
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                )
                            else Text("Save")
                        }

                        Button(
                            onClick = onApprove,
                            enabled = !isLoading,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                        ) {
                            if (isLoading)
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            else Text("Approve")
                        }
                    }
                }
            } else {
                Button(
                    onClick = onConfirm,
                    enabled =
                        !alreadyRequested &&
                            !isLoading &&
                            (mediaType == MediaType.MOVIE ||
                                selectedSeasons.isNotEmpty() ||
                                availableSeasons == 0),
                ) {
                    if (isLoading)
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    else Text(if (alreadyRequested) "Already Requested" else "Request")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isManagementMode) {
                    if (requestStatus == RequestStatus.PENDING) {
                        TextButton(
                            onClick = onDecline,
                            enabled = !isLoading,
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                        ) {
                            Text("Decline")
                        }
                    } else {
                        TextButton(
                            onClick = onDelete,
                            enabled = !isLoading,
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                        ) {
                            Text("Delete Request")
                        }
                    }
                }
                TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun MetadataDot() {
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
internal fun CircleFlagIcon(url: String, modifier: Modifier = Modifier) {
    coil3.compose.AsyncImage(
        model =
            ImageRequest.Builder(LocalContext.current)
                .data(url)
                .decoderFactory(SvgDecoder.Factory())
                .crossfade(true)
                .build(),
        contentDescription = "Flag",
        modifier = modifier.size(14.dp).clip(CircleShape),
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun <T> MinimalSelectionTile(
    label: String,
    selectedText: String,
    items: List<T>,
    itemText: (T) -> String,
    onItemSelected: (T) -> Unit,
    isLoading: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable(enabled = !isLoading && items.isNotEmpty()) { expanded = true }
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (isLoading) {
                    Text(
                        text = "Loading...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                } else {
                    Text(
                        text = selectedText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Icon(
                painterResource(id = R.drawable.ic_keyboard_arrow_down),
                contentDescription = "Select",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            items.forEach { item ->
                val isSelected = itemText(item) == selectedText
                DropdownMenuItem(
                    text = {
                        Text(
                            text = itemText(item),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    },
                    trailingIcon =
                        if (isSelected) {
                            {
                                Icon(
                                    painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else null,
                )
            }
        }
    }
}

@Composable
fun MinimalSwitchTile(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: Int? = null,
    iconSize: Dp = 24.dp,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp).size(iconSize),
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.scale(0.8f),
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
        )
    }
}

@Composable
fun SeparatedFlowRow(
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 4.dp,
    separator: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val itemMeasurables = subcompose("content", content)
        val itemPlaceables = itemMeasurables.map { it.measure(constraints.copy(minWidth = 0)) }

        if (itemPlaceables.isEmpty()) {
            return@SubcomposeLayout layout(0, 0) {}
        }

        val separatorMeasurables = subcompose("separator_ref") { separator() }
        val separatorPlaceableRef =
            separatorMeasurables.firstOrNull()?.measure(constraints.copy(minWidth = 0))
        val separatorWidth = separatorPlaceableRef?.width ?: 0
        val spacingPx = itemSpacing.roundToPx()

        val sequences = mutableListOf<List<Placeable>>()
        var currentSequence = mutableListOf<Placeable>()
        var currentWidth = 0

        itemPlaceables.forEach { placeable ->
            val addedWidth =
                if (currentSequence.isEmpty()) {
                    placeable.width
                } else {
                    separatorWidth + (spacingPx * 2) + placeable.width
                }

            if (currentWidth + addedWidth <= constraints.maxWidth) {
                currentSequence.add(placeable)
                currentWidth += addedWidth
            } else {
                if (currentSequence.isNotEmpty()) sequences.add(currentSequence)
                currentSequence = mutableListOf(placeable)
                currentWidth = placeable.width
            }
        }
        if (currentSequence.isNotEmpty()) sequences.add(currentSequence)

        val totalSeparatorsNeeded = itemPlaceables.size - sequences.size
        val separatorList =
            subcompose("separators_real") { repeat(totalSeparatorsNeeded) { separator() } }
                .map { it.measure(constraints.copy(minWidth = 0)) }

        var separatorIndex = 0

        val verticalGap = 8.dp.roundToPx()
        val totalHeight =
            sequences.sumOf { line -> line.maxOf { it.height } } +
                ((sequences.size - 1) * verticalGap)

        layout(constraints.maxWidth, totalHeight) {
            var yOffset = 0
            sequences.forEach { line ->
                var xOffset = 0
                val lineHeight = line.maxOf { it.height }

                val lineWidth =
                    line.sumOf { it.width } + ((line.size - 1) * (separatorWidth + spacingPx * 2))
                xOffset = (constraints.maxWidth - lineWidth) / 2

                line.forEachIndexed { index, placeable ->
                    placeable.placeRelative(xOffset, yOffset + (lineHeight - placeable.height) / 2)
                    xOffset += placeable.width

                    if (index < line.lastIndex) {
                        xOffset += spacingPx

                        val sep = separatorList.getOrNull(separatorIndex++)
                        sep?.placeRelative(xOffset, yOffset + (lineHeight - sep.height) / 2)

                        xOffset += separatorWidth + spacingPx
                    }
                }
                yOffset += lineHeight + verticalGap
            }
        }
    }
}

internal fun getAutoFlagUrl(langCode: String): String? {
    if (langCode.isBlank()) return null
    val resolvedCode =
        if (langCode.length > 3) {
            Locale.getAvailableLocales()
                .firstOrNull {
                    it.displayLanguage.equals(langCode, ignoreCase = true) ||
                        it.getDisplayLanguage(Locale.ENGLISH).equals(langCode, ignoreCase = true)
                }
                ?.language ?: langCode
        } else {
            langCode
        }

    val manualMapping =
        when (resolvedCode.lowercase()) {
            "en" -> "us"
            "hi" -> "in"
            "ja" -> "jp"
            "ko" -> "kr"
            "zh" -> "cn"
            "es" -> "es"
            "fr" -> "fr"
            "it" -> "it"
            "de" -> "de"
            "ru" -> "ru"
            "pt" -> "br"
            "tr" -> "tr"
            "th" -> "th"
            "id" -> "id"
            "tl" -> "ph"
            "vi" -> "vn"
            "pl" -> "pl"
            "uk" -> "ua"
            "ta" -> "in"
            "te" -> "in"
            "ml" -> "in"
            "kn" -> "in"
            "pa" -> "in"
            "mr" -> "in"
            "bn" -> "in"
            "sv" -> "se"
            "da" -> "dk"
            "no" -> "no"
            "fi" -> "fi"
            "is" -> "is"
            "nl" -> "nl"
            "cs" -> "cz"
            "hu" -> "hu"
            "el" -> "gr"
            "he" -> "il"
            "ar" -> "sa"
            "fa" -> "ir"
            else -> null
        }

    val countryCode =
        manualMapping
            ?: run {
                val locale = Locale.forLanguageTag(resolvedCode)
                locale.country.ifBlank {
                    Locale.getAvailableLocales()
                        .firstOrNull { it.language == resolvedCode && it.country.isNotBlank() }
                        ?.country
                }
            }

    return if (!countryCode.isNullOrBlank()) {
        "https://hatscripts.github.io/circle-flags/flags/${countryCode.lowercase()}.svg"
    } else {
        null
    }
}

private fun formatReleaseDate(dateString: String): String {
    if (dateString.isBlank()) return ""
    return try {
        val date = LocalDate.parse(dateString)
        date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))
    } catch (e: DateTimeParseException) {
        dateString
    }
}

private fun formatRuntime(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) {
        "${hours}h ${mins}m"
    } else {
        "${mins}m"
    }
}
