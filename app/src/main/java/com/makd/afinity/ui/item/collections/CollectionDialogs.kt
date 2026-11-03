package com.makd.afinity.ui.item.collections

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.ui.admin.components.AdminRemoveButton
import com.makd.afinity.ui.components.AfinityModalBottomSheet
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.CreateNamedItemDialog
import com.makd.afinity.ui.components.PickerChoice
import com.makd.afinity.ui.components.PickerListDialog

private const val POSTER_ASPECT = 2f / 3f
private val ThumbHeight = 44.dp

fun CollectionResult.message(context: Context): String =
    when (this) {
        is CollectionResult.Added ->
            context.getString(R.string.collection_added_fmt, collectionName)
        is CollectionResult.Created ->
            context.getString(R.string.collection_created_fmt, collectionName)
        is CollectionResult.Removed ->
            context.getString(R.string.collection_removed_fmt, collectionName)
        CollectionResult.Failed -> context.getString(R.string.collection_update_failed)
    }

@Composable
private fun CollectionResultEffect(
    viewModel: CollectionsViewModel,
    onResult: (CollectionResult) -> Unit,
    onFinished: (() -> Unit)?,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.result) {
        val result = state.result ?: return@LaunchedEffect
        onResult(result)
        viewModel.clearResult()
        onFinished?.invoke()
    }
}

enum class CollectionDialog {
    Add,
    Remove,
    Manage,
}

class CollectionMenuActions(
    val onAdd: (() -> Unit)?,
    val onRemove: (() -> Unit)?,
    val onManage: (() -> Unit)?,
)

fun collectionMenuActions(
    item: AfinityItem,
    containingBoxSets: List<AfinityBoxSet>,
    canManageCollections: Boolean,
    open: (CollectionDialog) -> Unit,
): CollectionMenuActions {
    if (!canManageCollections) return CollectionMenuActions(null, null, null)
    val isVideo =
        item is AfinityMovie ||
            item is AfinityShow ||
            item is AfinitySeason ||
            item is AfinityEpisode
    return CollectionMenuActions(
        onAdd = if (isVideo) ({ open(CollectionDialog.Add) }) else null,
        onRemove =
            if (isVideo && containingBoxSets.isNotEmpty()) ({ open(CollectionDialog.Remove) })
            else null,
        onManage = if (item is AfinityBoxSet) ({ open(CollectionDialog.Manage) }) else null,
    )
}

@Composable
fun CollectionDialogHost(
    dialog: CollectionDialog?,
    item: AfinityItem,
    containingBoxSets: List<AfinityBoxSet>,
    boxSetItems: List<AfinityItem>,
    onDismiss: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    onCollectionCreated: (collectionId: String, message: String) -> Unit,
) {
    val context = LocalContext.current
    val onResult: (CollectionResult) -> Unit = { result ->
        if (result is CollectionResult.Created) {
            onCollectionCreated(result.collectionId, result.message(context))
        } else {
            onShowSnackbar(result.message(context))
        }
    }
    when (dialog) {
        CollectionDialog.Add ->
            AddToCollectionDialog(
                itemIds = listOf(item.id.toString()),
                onDismiss = onDismiss,
                onResult = onResult,
            )
        CollectionDialog.Remove ->
            RemoveFromCollectionDialog(
                itemId = item.id.toString(),
                itemName = item.name,
                collections = containingBoxSets,
                onDismiss = onDismiss,
                onResult = onResult,
            )
        CollectionDialog.Manage ->
            (item as? AfinityBoxSet)?.let { collection ->
                ManageCollectionItemsSheet(
                    collection = collection,
                    items = boxSetItems,
                    onDismiss = onDismiss,
                    onResult = onResult,
                )
            }
        null -> Unit
    }
}

private enum class AddScreen {
    List,
    Create,
}

@Composable
fun AddToCollectionDialog(
    itemIds: List<String>,
    onDismiss: () -> Unit,
    onResult: (CollectionResult) -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(AddScreen.List) }

    LaunchedEffect(Unit) { viewModel.loadCollections() }
    CollectionResultEffect(viewModel = viewModel, onResult = onResult, onFinished = onDismiss)

    when (screen) {
        AddScreen.List -> {
            val choices =
                state.collections.map { collection ->
                    PickerChoice(
                        key = collection.id,
                        name = collection.name,
                        subtitle =
                            collection.itemCount?.let { count ->
                                pluralStringResource(R.plurals.collection_item_count, count, count)
                            },
                        imageUrl = collection.imageUrl,
                    )
                }
            PickerListDialog(
                title = stringResource(R.string.collection_add_title),
                choices = choices,
                isLoading = state.isLoading,
                isSubmitting = state.isSubmitting,
                thumbAspectRatio = POSTER_ASPECT,
                emptyText = stringResource(R.string.collection_none_yet),
                createLabel = stringResource(R.string.collection_new_title),
                onCreateNew = { screen = AddScreen.Create },
                onDismiss = onDismiss,
                onSelect = { choice ->
                    state.collections
                        .firstOrNull { it.id == choice.key }
                        ?.let { viewModel.add(it, itemIds) }
                },
            )
        }

        AddScreen.Create ->
            CreateNamedItemDialog(
                title = stringResource(R.string.collection_new_title),
                nameLabel = stringResource(R.string.collection_name_label),
                toggleLabel = stringResource(R.string.collection_search_metadata),
                toggleHint = { on ->
                    stringResource(
                        if (on) R.string.collection_search_metadata_on
                        else R.string.collection_search_metadata_off
                    )
                },
                isLoading = state.isSubmitting,
                onDismiss = { screen = AddScreen.List },
                onCreate = { name, searchMetadata ->
                    viewModel.create(name, itemIds, searchMetadata)
                },
            )
    }
}

@Composable
fun RemoveFromCollectionDialog(
    itemId: String,
    itemName: String,
    collections: List<AfinityBoxSet>,
    onDismiss: () -> Unit,
    onResult: (CollectionResult) -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectionResultEffect(viewModel = viewModel, onResult = onResult, onFinished = onDismiss)

    PickerListDialog(
        title = stringResource(R.string.collection_remove_title),
        message = stringResource(R.string.collection_remove_message, itemName),
        choices =
            collections.map { collection ->
                PickerChoice(
                    key = collection.id.toString(),
                    name = collection.name,
                    subtitle = null,
                    imageUrl = collection.images.primaryImageUrl,
                    blurHash = collection.images.primaryBlurHash,
                )
            },
        isLoading = false,
        isSubmitting = state.isSubmitting,
        thumbAspectRatio = POSTER_ASPECT,
        trailingIconRes = R.drawable.ic_close,
        trailingTint = MaterialTheme.colorScheme.error,
        onDismiss = onDismiss,
        onSelect = { choice -> viewModel.remove(choice.key, choice.name, listOf(itemId)) },
    )
}

@Composable
fun ManageCollectionItemsSheet(
    collection: AfinityBoxSet,
    items: List<AfinityItem>,
    onDismiss: () -> Unit,
    onResult: (CollectionResult) -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectionResultEffect(viewModel = viewModel, onResult = onResult, onFinished = null)

    AfinityModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.collection_action_manage),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = items.size.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.collection_manage_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(items, key = { it.id.toString() }) { item ->
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(start = 24.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier.size(
                                        width = ThumbHeight * POSTER_ASPECT,
                                        height = ThumbHeight,
                                    )
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                imageUrl = item.images.primaryImageUrl,
                                contentDescription = item.name,
                                blurHash = item.images.primaryBlurHash,
                                targetWidth = ThumbHeight * POSTER_ASPECT,
                                targetHeight = ThumbHeight,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            (item as? AfinityEpisode)?.seriesName?.let { seriesName ->
                                Text(
                                    text = seriesName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        AdminRemoveButton(
                            contentDescription =
                                stringResource(R.string.cd_collection_remove_item, item.name),
                            enabled = !state.isSubmitting,
                            onClick = {
                                viewModel.remove(
                                    collection.id.toString(),
                                    collection.name,
                                    listOf(item.id.toString()),
                                )
                            },
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.collection_manage_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
            )
        }
    }
}
