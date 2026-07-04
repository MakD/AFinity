package com.makd.afinity.ui.requests

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.CastMember
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.Permissions
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.jellyseerr.hasPermission
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.CircleFlagIcon
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.RequestConfirmationDialog
import com.makd.afinity.ui.components.SeparatedFlowRow
import com.makd.afinity.ui.components.getAutoFlagUrl
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth
import com.makd.afinity.ui.utils.rememberTopBarOpacity
import com.makd.afinity.ui.utils.verticalLayoutOffset
import java.util.Locale

@Composable
fun SeerrMediaDetailScreen(
    onItemClick: (jellyfinItemId: String, itemType: String?) -> Unit,
    onNavigateToFilteredMedia: (FilterParams) -> Unit,
    onNavigateToSeerrMedia: (item: SearchResultItem) -> Unit,
    onNavigateHome: () -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    viewModel: SeerrMediaDetailViewModel = hiltViewModel(),
    requestsViewModel: RequestsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading ->
                if (viewModel.previewTitle != null || viewModel.previewImageUrl != null) {
                    SeerrLoadingPreview(
                        title = viewModel.previewTitle,
                        imageUrl = viewModel.previewImageUrl,
                    )
                } else {
                    FullScreenLoading()
                }
            uiState.error != null -> FullScreenError(message = uiState.error!!)
            uiState.details != null -> {
                SeerrDetailContent(
                    uiState = uiState,
                    mediaType = viewModel.mediaType,
                    tmdbId = viewModel.tmdbId,
                    onItemClick = onItemClick,
                    onNavigateToFilteredMedia = onNavigateToFilteredMedia,
                    onNavigateToSeerrMedia = onNavigateToSeerrMedia,
                    onNavigateHome = onNavigateHome,
                    requestsViewModel = requestsViewModel,
                    widthSizeClass = widthSizeClass,
                )
            }
        }

        SeerrRequestDialogHost(requestsViewModel = requestsViewModel)
    }
}

@Composable
private fun SeerrDetailContent(
    uiState: SeerrMediaDetailUiState,
    mediaType: MediaType,
    tmdbId: Int,
    onItemClick: (String, String?) -> Unit,
    onNavigateToFilteredMedia: (FilterParams) -> Unit,
    onNavigateToSeerrMedia: (SearchResultItem) -> Unit,
    onNavigateHome: () -> Unit,
    requestsViewModel: RequestsViewModel,
    widthSizeClass: WindowWidthSizeClass,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val lazyListState = rememberLazyListState()
    val topBarOpacity by rememberTopBarOpacity(lazyListState)

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            SeerrLandscapeContent(
                uiState = uiState,
                mediaType = mediaType,
                tmdbId = tmdbId,
                onItemClick = onItemClick,
                onNavigateToFilteredMedia = onNavigateToFilteredMedia,
                onNavigateToSeerrMedia = onNavigateToSeerrMedia,
                requestsViewModel = requestsViewModel,
                widthSizeClass = widthSizeClass,
                lazyListState = lazyListState,
            )
        } else {
            SeerrPortraitContent(
                uiState = uiState,
                mediaType = mediaType,
                tmdbId = tmdbId,
                onItemClick = onItemClick,
                onNavigateToFilteredMedia = onNavigateToFilteredMedia,
                onNavigateToSeerrMedia = onNavigateToSeerrMedia,
                requestsViewModel = requestsViewModel,
                widthSizeClass = widthSizeClass,
                lazyListState = lazyListState,
            )
        }

        AfinityTopAppBar(
            title = {
                IconButton(onClick = onNavigateHome, modifier = Modifier.size(42.dp)) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_home),
                            contentDescription = "Go to Home",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            },
            backgroundOpacity = { topBarOpacity },
        )
    }
}

@Composable
private fun SeerrRequestActionButton(
    details: com.makd.afinity.data.models.jellyseerr.MediaDetails,
    mediaType: MediaType,
    tmdbId: Int,
    onItemClick: (String, String?) -> Unit,
    requestsViewModel: RequestsViewModel,
) {
    val status = MediaStatus.fromValue(details.mediaInfo?.status ?: MediaStatus.UNKNOWN.value)
    val jellyfinId = details.mediaInfo?.getJellyfinItemId()
    val mappedType = if (mediaType == MediaType.TV) "Series" else "Movie"

    when {
        status == MediaStatus.AVAILABLE && jellyfinId != null -> {
            Button(
                onClick = { onItemClick(jellyfinId, mappedType) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.seerr_detail_open_library))
            }
        }
        mediaType == MediaType.MOVIE &&
            (status == MediaStatus.PENDING ||
                status == MediaStatus.PROCESSING ||
                status == MediaStatus.AVAILABLE) -> {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(MediaStatus.getDisplayName(status))
            }
        }
        else -> {
            Button(
                onClick = {
                    requestsViewModel.showRequestDialog(
                        tmdbId = tmdbId,
                        mediaType = mediaType,
                        title = details.title ?: details.name ?: "",
                        posterUrl = details.getPosterUrl(),
                        availableSeasons = 0,
                        existingStatus = status,
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.seerr_detail_request))
            }
        }
    }
}

@Composable
private fun SeerrDetailSections(
    uiState: SeerrMediaDetailUiState,
    onItemClick: (String, String?) -> Unit,
    onNavigateToFilteredMedia: (FilterParams) -> Unit,
    onNavigateToSeerrMedia: (SearchResultItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
) {
    val details = uiState.details ?: return
    val cast = details.credits?.cast.orEmpty()

    val handleMediaItemClick: (SearchResultItem) -> Unit = { item ->
        if (item.mediaInfo?.isFullyAvailable() == true) {
            item.mediaInfo?.getJellyfinItemId()?.let { id ->
                onItemClick(id, if (item.mediaType.lowercase() == "tv") "Series" else "Movie")
            } ?: onNavigateToSeerrMedia(item)
        } else {
            onNavigateToSeerrMedia(item)
        }
    }

    if (cast.isNotEmpty()) {
        SeerrCastSection(
            cast = cast,
            onPersonClick = { member ->
                onNavigateToFilteredMedia(
                    FilterParams(type = FilterType.PERSON, id = member.id, name = member.name)
                )
            },
            widthSizeClass = widthSizeClass,
        )
    }

    SeerrRatingsSection(
        ratingsCombined = uiState.ratings ?: details.ratingsCombined,
        voteAverage = details.voteAverage,
    )

    if (uiState.collectionParts.isNotEmpty()) {
        DiscoverSection(
            title = uiState.collectionName ?: stringResource(R.string.seerr_collection_fallback),
            items = uiState.collectionParts,
            onItemClick = handleMediaItemClick,
            widthSizeClass = widthSizeClass,
            horizontalPadding = 0.dp,
        )
    }

    if (uiState.recommendations.isNotEmpty()) {
        DiscoverSection(
            title = stringResource(R.string.seerr_recommendations_title),
            items = uiState.recommendations,
            onItemClick = handleMediaItemClick,
            widthSizeClass = widthSizeClass,
            horizontalPadding = 0.dp,
        )
    }

    if (uiState.similar.isNotEmpty()) {
        DiscoverSection(
            title = stringResource(R.string.similar_items_title),
            items = uiState.similar,
            onItemClick = handleMediaItemClick,
            widthSizeClass = widthSizeClass,
            horizontalPadding = 0.dp,
        )
    }
}
@Composable
private fun SeerrCastSection(
    cast: List<CastMember>,
    onPersonClick: (CastMember) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
) {
    val cardWidth = widthSizeClass.portraitWidth

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.cast_section_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cast.take(15), key = { it.id }) { member ->
                Column(
                    modifier =
                        Modifier.width(cardWidth).clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            onPersonClick(member)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AsyncImage(
                        imageUrl =
                            member.profilePath?.let { "https://image.tmdb.org/t/p/w300$it" },
                        contentDescription = member.name,
                        blurHash = null,
                        targetWidth = cardWidth,
                        targetHeight = cardWidth,
                        modifier = Modifier.size(cardWidth).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(id = R.drawable.ic_person_placeholder),
                        error = painterResource(id = R.drawable.ic_person_placeholder),
                    )

                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = member.character.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.height(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeerrRequestDialogHost(requestsViewModel: RequestsViewModel) {
    val uiState by requestsViewModel.uiState.collectAsStateWithLifecycle()
    val currentUser by requestsViewModel.currentUser.collectAsStateWithLifecycle()

    if (uiState.showRequestDialog && uiState.pendingRequest != null) {
        val pending = uiState.pendingRequest!!
        RequestConfirmationDialog(
            mediaTitle = pending.title,
            mediaPosterUrl = pending.posterUrl,
            mediaType = pending.mediaType,
            availableSeasons = pending.availableSeasons,
            selectedSeasons = uiState.selectedSeasons,
            onSeasonsChange = { requestsViewModel.setSelectedSeasons(it) },
            disabledSeasons = uiState.disabledSeasons,
            canSelectSeasons = uiState.publicSettings?.partialRequestsEnabled ?: true,
            quota =
                uiState.userQuota?.let {
                    if (pending.mediaType == MediaType.TV) it.tv else it.movie
                },
            existingStatus = pending.existingStatus,
            isLoading = uiState.isCreatingRequest,
            detailsLoading = uiState.isFetchingTvDetails,
            onConfirm = { requestsViewModel.confirmRequest() },
            onDismiss = { requestsViewModel.dismissRequestDialog() },
            mediaBackdropUrl = pending.backdropUrl,
            mediaTagline = pending.tagline,
            mediaOverview = pending.overview,
            releaseDate = pending.releaseDate,
            runtime = pending.runtime,
            voteAverage = pending.voteAverage,
            certification = pending.certification,
            originalLanguage = pending.originalLanguage,
            director = pending.director,
            genres = pending.genres,
            ratingsCombined = pending.ratingsCombined,
            can4k =
                (uiState.publicSettings?.let {
                    if (pending.mediaType == MediaType.MOVIE) it.movie4kEnabled
                    else it.series4kEnabled
                } ?: true) &&
                    (currentUser?.let { user ->
                        user.hasPermission(Permissions.REQUEST_4K) ||
                            (pending.mediaType == MediaType.MOVIE &&
                                user.hasPermission(Permissions.REQUEST_4K_MOVIE)) ||
                            (pending.mediaType == MediaType.TV &&
                                user.hasPermission(Permissions.REQUEST_4K_TV))
                    } ?: false),
            is4k = uiState.is4kRequested,
            onIs4kChange = { requestsViewModel.setIs4kRequested(it) },
            canAdvanced =
                currentUser?.hasPermission(Permissions.REQUEST_ADVANCED) == true ||
                    currentUser?.hasPermission(Permissions.MANAGE_REQUESTS) == true,
            availableServers = uiState.availableServers,
            selectedServer = uiState.selectedServer,
            onServerSelected = { requestsViewModel.selectServer(it) },
            availableProfiles = uiState.availableProfiles,
            selectedProfile = uiState.selectedProfile,
            onProfileSelected = { requestsViewModel.selectProfile(it) },
            selectedRootFolder = uiState.selectedRootFolder,
            isLoadingServers = uiState.isLoadingServers,
            isLoadingProfiles = uiState.isLoadingProfiles,
            tvdbCandidates = uiState.tvdbCandidates,
            selectedTvdbId = uiState.selectedTvdbId,
            onTvdbSelected = { requestsViewModel.selectTvdbCandidate(it) },
            availableLanguageProfiles = uiState.availableLanguageProfiles,
            selectedLanguageProfile = uiState.selectedLanguageProfile,
            onLanguageProfileSelected = { requestsViewModel.selectLanguageProfile(it) },
            availableTags = uiState.availableTags,
            selectedTagIds = uiState.selectedTagIds,
            onTagToggle = { requestsViewModel.toggleTag(it) },
            availableUsers = uiState.availableUsers,
            selectedRequestUser = uiState.selectedRequestUser,
            onRequestUserSelected = { requestsViewModel.selectRequestUser(it) },
        )
    }
}

@Composable
private fun SeerrPortraitContent(
    uiState: SeerrMediaDetailUiState,
    mediaType: MediaType,
    tmdbId: Int,
    onItemClick: (String, String?) -> Unit,
    onNavigateToFilteredMedia: (FilterParams) -> Unit,
    onNavigateToSeerrMedia: (SearchResultItem) -> Unit,
    requestsViewModel: RequestsViewModel,
    widthSizeClass: WindowWidthSizeClass,
    lazyListState: LazyListState,
) {
    val details = uiState.details ?: return
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val playerOffset = LocalPlayerOffset.current

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = max(bottomPadding, playerOffset) + 16.dp),
    ) {
        item {
            SeerrHeroSection(imageUrl = details.getBackdropUrl() ?: details.getPosterUrl())
        }

        item {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalLayoutOffset((-110).dp)
                        .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = details.title ?: details.name ?: "",
                    style =
                        MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                        ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                SeerrMetadataRow(details = details, mediaType = mediaType)

                SeerrRequestActionButton(
                    details = details,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    onItemClick = onItemClick,
                    requestsViewModel = requestsViewModel,
                )

                SeerrOverviewBlock(details = details, mediaType = mediaType)

                SeerrDetailSections(
                    uiState = uiState,
                    onItemClick = onItemClick,
                    onNavigateToFilteredMedia = onNavigateToFilteredMedia,
                    onNavigateToSeerrMedia = onNavigateToSeerrMedia,
                    widthSizeClass = widthSizeClass,
                )
            }
        }
    }
}

@Composable
private fun SeerrLandscapeContent(
    uiState: SeerrMediaDetailUiState,
    mediaType: MediaType,
    tmdbId: Int,
    onItemClick: (String, String?) -> Unit,
    onNavigateToFilteredMedia: (FilterParams) -> Unit,
    onNavigateToSeerrMedia: (SearchResultItem) -> Unit,
    requestsViewModel: RequestsViewModel,
    widthSizeClass: WindowWidthSizeClass,
    lazyListState: LazyListState,
) {
    val details = uiState.details ?: return
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density)
    val displayCutoutLeft = WindowInsets.displayCutout.getLeft(density, LayoutDirection.Ltr)
    val baseColorScheme = MaterialTheme.colorScheme
    val playerOffset = LocalPlayerOffset.current

    val landscapeColorScheme =
        remember(baseColorScheme) {
            baseColorScheme.copy(
                onBackground = Color.White,
                onSurface = Color.White,
                onSurfaceVariant = Color.White.copy(alpha = 0.7f),
                outline = Color.White.copy(alpha = 0.5f),
            )
        }

    MaterialTheme(colorScheme = landscapeColorScheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            val backdropUrl = details.getBackdropUrl() ?: details.getPosterUrl()
            if (backdropUrl != null) {
                AsyncImage(
                    imageUrl = backdropUrl,
                    contentDescription = null,
                    blurHash = null,
                    targetWidth = 1920.dp,
                    targetHeight = 1080.dp,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                )
            }

            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

            Image(
                painter = painterResource(id = R.drawable.mask),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
            )

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        top = with(density) { statusBarHeight.toDp() } + 64.dp,
                        start = with(density) { displayCutoutLeft.toDp() + 16.dp },
                        end = 16.dp,
                        bottom = 16.dp + playerOffset,
                    ),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = details.title ?: details.name ?: "",
                            style =
                                MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 32.sp,
                                ),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )

                        SeerrMetadataRow(
                            details = details,
                            mediaType = mediaType,
                            centered = false,
                        )

                        Box(modifier = Modifier.widthIn(max = 240.dp)) {
                            SeerrRequestActionButton(
                                details = details,
                                mediaType = mediaType,
                                tmdbId = tmdbId,
                                onItemClick = onItemClick,
                                requestsViewModel = requestsViewModel,
                            )
                        }

                        SeerrOverviewBlock(details = details, mediaType = mediaType)

                        SeerrDetailSections(
                            uiState = uiState,
                            onItemClick = onItemClick,
                            onNavigateToFilteredMedia = onNavigateToFilteredMedia,
                            onNavigateToSeerrMedia = onNavigateToSeerrMedia,
                            widthSizeClass = widthSizeClass,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeerrHeroSection(imageUrl: String?) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val containerSize = windowInfo.containerSize
    val screenWidth = with(density) { containerSize.width.toDp() }
    val screenHeight = with(density) { containerSize.height.toDp() }

    Box(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.5f)) {
        AsyncImage(
            imageUrl = imageUrl,
            contentDescription = null,
            blurHash = null,
            targetWidth = screenWidth,
            targetHeight = screenHeight * 0.5f,
            modifier =
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithCache {
                        val gradient =
                            Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = size.height * 0.75f,
                                endY = size.height,
                            )
                        onDrawWithContent {
                            drawContent()
                            drawRect(gradient, blendMode = BlendMode.DstIn)
                        }
                    },
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
        )
    }
}

@Composable
private fun SeerrOverviewBlock(
    details: com.makd.afinity.data.models.jellyseerr.MediaDetails,
    mediaType: MediaType,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        details.tagline
            ?.takeIf { it.isNotBlank() }
            ?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

        details.overview
            ?.takeIf { it.isNotBlank() }
            ?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

        details.getDirector()?.let { director -> SeerrFactRow("Director", director) }

        SeerrDetailsCard(details = details, mediaType = mediaType)
    }
}

@Composable
private fun SeerrFactRow(label: String, value: String) {
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$label: ") }
                append(value)
            },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(0.9f),
    )
}

@Composable
private fun SeerrMetadataRow(
    details: com.makd.afinity.data.models.jellyseerr.MediaDetails,
    mediaType: MediaType,
    centered: Boolean = true,
) {
    val year = (details.releaseDate ?: details.firstAirDate)?.take(4)?.takeIf { it.isNotBlank() }
    val runtime =
        details.runtime?.takeIf { it > 0 }?.let { minutes ->
            val hours = minutes / 60
            val mins = minutes % 60
            if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        }
    val seasonCount =
        if (mediaType == MediaType.TV) details.getSeasonCount().takeIf { it > 0 } else null
    val certification = details.getCertification()?.takeIf { it.isNotBlank() }
    val genres = details.getGenreNames().take(2)

    SeparatedFlowRow(
        modifier = Modifier.fillMaxWidth(),
        centerLines = centered,
        separator = {
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        },
    ) {
        year?.let { MetaText(it) }
        seasonCount?.let { MetaText(stringResource(R.string.season_count_fmt, it)) }
        runtime?.let { MetaText(it) }
        certification?.let { MetaText(it) }
        if (genres.isNotEmpty()) MetaText(genres.joinToString(", "))
    }
}

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface.copy(0.9f),
    )
}


private data class SeerrDetailRowData(
    val label: String,
    val value: String,
    val isStatus: Boolean = false,
    val flagLanguageCode: String? = null,
)

private fun buildSeerrDetailRows(
    details: com.makd.afinity.data.models.jellyseerr.MediaDetails,
    mediaType: MediaType,
): List<SeerrDetailRowData> = buildList {
    val displayTitle = details.title ?: details.name
    val originalTitle =
        (details.originalTitle ?: details.originalName)?.takeIf {
            it.isNotBlank() && it != displayTitle
        }
    val language =
        details.spokenLanguages?.firstOrNull()?.let { it.englishName ?: it.name }
            ?: details.originalLanguage
                ?.takeIf { it.isNotBlank() }
                ?.let { code -> Locale.forLanguageTag(code).displayLanguage.ifBlank { code } }
    val countries =
        details.productionCountries?.mapNotNull { it.name ?: it.iso_3166_1 }?.takeIf {
            it.isNotEmpty()
        }

    originalTitle?.let { add(SeerrDetailRowData("Original Title", it)) }
    details.status?.takeIf { it.isNotBlank() }?.let {
        add(SeerrDetailRowData("Status", it, isStatus = true))
    }

    if (mediaType == MediaType.TV) {
        details.seriesType?.takeIf { it.isNotBlank() }?.let {
            add(SeerrDetailRowData("Series Type", it))
        }
        details.firstAirDate?.let { date ->
            formatSeerrDate(date)?.let { add(SeerrDetailRowData("First Air Date", it)) }
        }
        details.nextEpisodeToAir?.airDate?.let { date ->
            formatSeerrDate(date)?.let { add(SeerrDetailRowData("Next Air Date", it)) }
        }
        details.episodeRunTime?.firstOrNull()?.takeIf { it > 0 }?.let {
            add(SeerrDetailRowData("Episode Runtime", "$it min"))
        }
        details.networks?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() }?.let {
            add(SeerrDetailRowData("Networks", it.joinToString(", ")))
        }
    } else {
        val usReleases =
            details.releases?.results?.firstOrNull { it.iso_3166_1 == "US" }?.release_dates
        usReleases
            ?.firstOrNull { it.type == 3 && !it.release_date.isNullOrBlank() }
            ?.release_date
            ?.let { date ->
                formatSeerrDate(date.take(10))?.let {
                    add(SeerrDetailRowData("Release (Theatrical)", it))
                }
            }
        usReleases
            ?.firstOrNull { it.type == 4 && !it.release_date.isNullOrBlank() }
            ?.release_date
            ?.let { date ->
                formatSeerrDate(date.take(10))?.let {
                    add(SeerrDetailRowData("Release (Digital)", it))
                }
            }
        if (usReleases == null) {
            details.releaseDate?.let { date ->
                formatSeerrDate(date)?.let { add(SeerrDetailRowData("Release Date", it)) }
            }
        }
        details.budget?.takeIf { it > 0 }?.let {
            add(SeerrDetailRowData("Budget", String.format(Locale.US, "$%,d", it)))
        }
        details.revenue?.takeIf { it > 0 }?.let {
            add(SeerrDetailRowData("Revenue", String.format(Locale.US, "$%,d", it)))
        }
    }

    language?.takeIf { it.isNotBlank() }?.let {
        add(
            SeerrDetailRowData(
                "Original Language",
                it,
                flagLanguageCode = details.originalLanguage,
            )
        )
    }
    countries?.let { add(SeerrDetailRowData("Production Country", it.joinToString(", "))) }
}
private fun formatSeerrDate(dateString: String): String? {
    if (dateString.isBlank()) return null
    return try {
        java.time.LocalDate.parse(dateString)
            .format(
                java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)
            )
    } catch (e: Exception) {
        dateString
    }
}

@Composable
private fun SeerrRatingsSection(
    ratingsCombined: com.makd.afinity.data.models.jellyseerr.RatingsCombined?,
    voteAverage: Double?,
) {
    val imdbScore = ratingsCombined?.imdb?.criticsScore
    val rtCritic = ratingsCombined?.rt?.criticsScore
    val rtAudience = ratingsCombined?.rt?.audienceScore
    val tmdbScore = voteAverage?.takeIf { it > 0 }

    if (imdbScore == null && rtCritic == null && rtAudience == null && tmdbScore == null) return

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Ratings",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            imdbScore?.let {
                item {
                    SeerrScorecard(
                        sourceName = "IMDb",
                        iconRes = R.drawable.ic_imdb_logo,
                        score = String.format(Locale.US, "%.1f", it),
                        subtext = "/ 10",
                    )
                }
            }

            rtCritic?.let {
                item {
                    SeerrScorecard(
                        sourceName = "Rotten Tomatoes",
                        iconRes =
                            if (it > 60) R.drawable.ic_rotten_tomato_fresh
                            else R.drawable.ic_rotten_tomato_rotten,
                        score = "$it%",
                        subtext = if (it > 60) "Fresh" else "Rotten",
                    )
                }
            }

            rtAudience?.let {
                item {
                    SeerrScorecard(
                        sourceName = "Popcornmeter",
                        iconRes =
                            if (it >= 60) R.drawable.ic_rt_fresh_popcorn
                            else R.drawable.ic_rt_stale_popcorn,
                        score = "$it%",
                        subtext = if (it >= 60) "Hot" else "Stale",
                    )
                }
            }

            tmdbScore?.let {
                item {
                    SeerrScorecard(
                        sourceName = "TMDB",
                        iconRes = R.drawable.ic_tmdb,
                        score = "${(it * 10).toInt()}%",
                        subtext = "Score",
                    )
                }
            }
        }
    }
}

@Composable
private fun SeerrScorecard(sourceName: String, iconRes: Int?, score: String, subtext: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = Modifier.width(132.dp).height(96.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = sourceName,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = sourceName,
                    style =
                        MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = score,
                    style =
                        MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SeerrLoadingPreview(title: String?, imageUrl: String?) {
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val playerOffset = LocalPlayerOffset.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = max(bottomPadding, playerOffset) + 16.dp),
    ) {
        item { SeerrHeroSection(imageUrl = imageUrl) }

        item {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalLayoutOffset((-110).dp)
                        .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                title?.let {
                    Text(
                        text = it,
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeerrDetailsCard(
    details: com.makd.afinity.data.models.jellyseerr.MediaDetails,
    mediaType: MediaType,
) {
    val rows = remember(details, mediaType) { buildSeerrDetailRows(details, mediaType) }
    if (rows.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.seerr_details_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
        ) {
            Column {
                rows.forEachIndexed { index, row ->
                    SeerrDetailLedgerRow(row)
                    if (index < rows.lastIndex) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeerrDetailLedgerRow(row: SeerrDetailRowData) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                row.isStatus -> {
                    val active =
                        row.value.lowercase() !in listOf("ended", "canceled", "cancelled")
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color =
                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    ) {
                        Text(
                            text = row.value,
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color =
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                row.flagLanguageCode != null -> {
                    getAutoFlagUrl(row.flagLanguageCode)?.let { url ->
                        CircleFlagIcon(url = url)
                        Box(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = row.value,
                        style =
                            MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                }
                else -> {
                    Text(
                        text = row.value,
                        style =
                            MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}
