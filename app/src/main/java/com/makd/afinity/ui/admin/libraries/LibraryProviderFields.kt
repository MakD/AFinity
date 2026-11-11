package com.makd.afinity.ui.admin.libraries

import androidx.annotation.StringRes
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.LibraryProvider
import com.makd.afinity.data.models.admin.LibraryProviders
import com.makd.afinity.data.models.admin.LibraryTypeProviders
import com.makd.afinity.ui.admin.config.ConfigField
import com.makd.afinity.ui.admin.config.ConfigSection
import com.makd.afinity.ui.admin.config.ImageOptionValue
import com.makd.afinity.ui.admin.config.ImageOptionsField
import com.makd.afinity.ui.admin.config.ImageTypeChoice
import com.makd.afinity.ui.admin.config.ProviderEntry
import com.makd.afinity.ui.admin.config.ProviderListField
import com.makd.afinity.ui.admin.config.ProviderSelection
import com.makd.afinity.ui.admin.config.providerOrder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val TYPE_OPTIONS_KEY = "TypeOptions"
private const val TYPE_KEY = "Type"
private const val METADATA_SAVERS_KEY = "MetadataSavers"
private const val READER_ORDER_KEY = "LocalMetadataReaderOrder"
private const val MIN_ORDERABLE_READERS = 2
private const val IMAGE_OPTIONS_KEY = "ImageOptions"
private const val PRIMARY_IMAGE_TYPE = "Primary"
private const val BACKDROP_IMAGE_TYPE = "Backdrop"

private val IMAGE_TYPE_LABELS =
    mapOf(
        "primary" to R.string.admin_image_type_primary,
        "backdrop" to R.string.admin_image_type_backdrop,
        "logo" to R.string.admin_image_type_logo,
        "thumb" to R.string.admin_image_type_thumb,
        "banner" to R.string.admin_image_type_banner,
        "art" to R.string.admin_image_type_art,
        "disc" to R.string.admin_image_type_disc,
    )

private class DisabledList(
    val disabledKey: String,
    val orderKey: String,
    @param:StringRes val label: Int,
    @param:StringRes val help: Int,
    val providers: (LibraryProviders) -> List<LibraryProvider>,
)

private class TypedList(
    val enabledKey: String,
    val orderKey: String,
    val providers: (LibraryTypeProviders) -> List<LibraryProvider>,
)

private val SUBTITLE_LIST =
    DisabledList(
        disabledKey = "DisabledSubtitleFetchers",
        orderKey = "SubtitleFetcherOrder",
        label = R.string.library_opt_subtitle_downloaders,
        help = R.string.library_opt_subtitle_downloaders_help,
        providers = { it.subtitleFetchers },
    )

private val LYRIC_LIST =
    DisabledList(
        disabledKey = "DisabledLyricFetchers",
        orderKey = "LyricFetcherOrder",
        label = R.string.library_opt_lyric_downloaders,
        help = R.string.library_opt_lyric_downloaders_help,
        providers = { it.lyricFetchers },
    )

private val SEGMENT_LIST =
    DisabledList(
        disabledKey = "DisabledMediaSegmentProviders",
        orderKey = "MediaSegmentProviderOrder",
        label = R.string.library_opt_segment_providers,
        help = R.string.library_opt_segment_providers_help,
        providers = { it.mediaSegmentProviders },
    )

private val METADATA_FETCHERS =
    TypedList("MetadataFetchers", "MetadataFetcherOrder") { it.metadataFetchers }

private val IMAGE_FETCHERS = TypedList("ImageFetchers", "ImageFetcherOrder") { it.imageFetchers }

private val SIMILAR_PROVIDERS =
    TypedList("SimilarItemProviders", "SimilarItemProviderOrder") { it.similarItemProviders }

private val TYPE_LABELS =
    mapOf(
        "movie" to R.string.library_opt_type_movie,
        "series" to R.string.library_opt_type_series,
        "season" to R.string.library_opt_type_season,
        "episode" to R.string.library_opt_type_episode,
        "musicartist" to R.string.library_opt_type_music_artist,
        "musicalbum" to R.string.library_opt_type_music_album,
        "audio" to R.string.library_opt_type_audio,
        "musicvideo" to R.string.library_opt_type_music_video,
        "book" to R.string.library_opt_type_book,
        "audiobook" to R.string.library_opt_type_audio_book,
        "video" to R.string.library_opt_type_video,
        "photo" to R.string.library_opt_type_photo,
        "boxset" to R.string.library_opt_type_box_set,
        "trailer" to R.string.library_opt_type_trailer,
    )

private fun List<LibraryProvider>.names(): List<String> = map { it.name }

private fun stringList(element: JsonElement?): List<String> =
    (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

private fun names(values: List<String>): JsonArray = JsonArray(values.map { JsonPrimitive(it) })

private fun selectedNames(
    available: List<LibraryProvider>,
    order: List<String>,
    saved: JsonElement?,
    listedByDefault: (LibraryProvider) -> Boolean,
): List<String> {
    val listed =
        if (saved is JsonArray) stringList(saved).map { it.lowercase() }.toSet()
        else available.filter(listedByDefault).map { it.name.lowercase() }.toSet()
    return order.filter { it.lowercase() in listed }
}

private fun JsonElement.isType(type: String): Boolean {
    val value = ((this as? JsonObject)?.get(TYPE_KEY) as? JsonPrimitive)?.contentOrNull
    return value.equals(type, ignoreCase = true)
}

internal fun LibraryProviders.hasMetadataFetchers(): Boolean = types.any {
    it.metadataFetchers.isNotEmpty()
}

internal fun LibraryProviders.hasImageFetchers(): Boolean = types.any {
    it.imageFetchers.isNotEmpty()
}

internal fun normalizeProviders(options: JsonObject, providers: LibraryProviders): JsonObject {
    val result = options.toMutableMap()
    if (providers.metadataSavers.isNotEmpty()) {
        val order = providers.metadataSavers.names()
        result[METADATA_SAVERS_KEY] =
            names(
                selectedNames(providers.metadataSavers, order, options[METADATA_SAVERS_KEY]) {
                    it.defaultEnabled
                }
            )
    }
    if (providers.metadataReaders.isNotEmpty()) {
        val saved = stringList(options[READER_ORDER_KEY])
        result[READER_ORDER_KEY] = names(providerOrder(providers.metadataReaders.names(), saved))
    }
    listOf(SUBTITLE_LIST, LYRIC_LIST, SEGMENT_LIST).forEach { list ->
        val available = list.providers(providers)
        if (available.isEmpty()) return@forEach
        val order = providerOrder(available.names(), stringList(options[list.orderKey]))
        result[list.orderKey] = names(order)
        result[list.disabledKey] =
            names(selectedNames(available, order, options[list.disabledKey]) { !it.defaultEnabled })
    }
    normalizeTypeOptions(options[TYPE_OPTIONS_KEY], providers.types)?.let {
        result[TYPE_OPTIONS_KEY] = it
    }
    return JsonObject(result)
}

private fun normalizeTypeOptions(
    existing: JsonElement?,
    types: List<LibraryTypeProviders>,
): JsonArray? {
    val entries = (existing as? JsonArray).orEmpty().toMutableList()
    var touched = false
    types.forEach { type ->
        val lists =
            listOf(METADATA_FETCHERS, IMAGE_FETCHERS, SIMILAR_PROVIDERS).filter {
                it.providers(type).isNotEmpty()
            }
        if (lists.isEmpty()) return@forEach
        val index = entries.indexOfFirst { it.isType(type.type) }
        val entry =
            entries.getOrNull(index) as? JsonObject
                ?: JsonObject(mapOf(TYPE_KEY to JsonPrimitive(type.type)))
        val updated = entry.toMutableMap()
        lists.forEach { list ->
            val available = list.providers(type)
            val order = providerOrder(available.names(), stringList(entry[list.orderKey]))
            updated[list.orderKey] = names(order)
            updated[list.enabledKey] =
                names(selectedNames(available, order, entry[list.enabledKey]) { it.defaultEnabled })
        }
        if (index >= 0) entries[index] = JsonObject(updated) else entries += JsonObject(updated)
        touched = true
    }
    return if (touched) JsonArray(entries) else null
}

internal fun metadataSaversField(providers: LibraryProviders): ProviderListField? =
    providers.metadataSavers
        .takeIf { it.isNotEmpty() }
        ?.let { savers ->
            ProviderListField(
                key = METADATA_SAVERS_KEY,
                label = R.string.library_opt_metadata_savers,
                providers = savers.names(),
                selection = ProviderSelection.ENABLED_LIST,
                selectionKey = METADATA_SAVERS_KEY,
                orderKey = null,
                summary = R.string.library_opt_metadata_savers_help,
            )
        }

internal fun metadataReadersField(providers: LibraryProviders): ProviderListField? =
    providers.metadataReaders
        .takeIf { it.size >= MIN_ORDERABLE_READERS }
        ?.let { readers ->
            ProviderListField(
                key = READER_ORDER_KEY,
                label = R.string.library_opt_metadata_readers,
                providers = readers.names(),
                selection = ProviderSelection.NONE,
                selectionKey = null,
                orderKey = READER_ORDER_KEY,
                summary = R.string.library_opt_metadata_readers_help,
            )
        }

private fun disabledListField(list: DisabledList, providers: LibraryProviders): ProviderListField? =
    list
        .providers(providers)
        .takeIf { it.isNotEmpty() }
        ?.let { available ->
            ProviderListField(
                key = list.orderKey,
                label = list.label,
                providers = available.names(),
                selection = ProviderSelection.DISABLED_LIST,
                selectionKey = list.disabledKey,
                orderKey = list.orderKey,
                summary = list.help,
            )
        }

internal fun subtitleFetchersField(providers: LibraryProviders): ProviderListField? =
    disabledListField(SUBTITLE_LIST, providers)

internal fun lyricFetchersField(providers: LibraryProviders): ProviderListField? =
    disabledListField(LYRIC_LIST, providers)

internal fun segmentProvidersField(providers: LibraryProviders): ProviderListField? =
    disabledListField(SEGMENT_LIST, providers)

private fun hasImageSettings(type: LibraryTypeProviders): Boolean {
    val supported = type.supportedImageTypes
    return supported.size > 1 ||
        (supported.size == 1 && !supported.first().equals(PRIMARY_IMAGE_TYPE, ignoreCase = true))
}

private fun imageOptionsField(type: LibraryTypeProviders, @StringRes fallback: Int): ConfigField {
    val label = TYPE_LABELS[type.type.lowercase()]
    return ImageOptionsField(
        key = "$TYPE_OPTIONS_KEY/${type.type}/$IMAGE_OPTIONS_KEY",
        label = label ?: fallback,
        entry = ProviderEntry(TYPE_OPTIONS_KEY, TYPE_KEY, type.type),
        optionsKey = IMAGE_OPTIONS_KEY,
        types =
            type.supportedImageTypes.map { imageType ->
                ImageTypeChoice(imageType, IMAGE_TYPE_LABELS[imageType.lowercase()])
            },
        defaults =
            type.defaultImageOptions.map { ImageOptionValue(it.type, it.limit, it.minWidth) },
        countedType =
            type.supportedImageTypes.firstOrNull {
                it.equals(BACKDROP_IMAGE_TYPE, ignoreCase = true)
            },
        labelText = if (label == null) type.type else null,
    )
}

private fun typedSection(
    providers: LibraryProviders,
    list: TypedList,
    @StringRes title: Int,
    @StringRes help: Int,
    withImageSettings: Boolean = false,
): ConfigSection? {
    val fields: List<ConfigField> =
        providers.types.flatMap { type ->
            val available = list.providers(type)
            if (available.isEmpty()) return@flatMap emptyList()
            val label = TYPE_LABELS[type.type.lowercase()]
            val listField =
                ProviderListField(
                    key = "$TYPE_OPTIONS_KEY/${type.type}/${list.enabledKey}",
                    label = label ?: title,
                    providers = available.names(),
                    selection = ProviderSelection.ENABLED_LIST,
                    selectionKey = list.enabledKey,
                    orderKey = list.orderKey,
                    entry = ProviderEntry(TYPE_OPTIONS_KEY, TYPE_KEY, type.type),
                    labelText = if (label == null) type.type else null,
                )
            if (withImageSettings && hasImageSettings(type)) {
                listOf(listField, imageOptionsField(type, title))
            } else {
                listOf(listField)
            }
        }
    return if (fields.isEmpty()) null else ConfigSection(title, fields, note = help)
}

internal fun providerSections(providers: LibraryProviders): List<ConfigSection> =
    listOfNotNull(
        typedSection(
            providers,
            METADATA_FETCHERS,
            R.string.library_opt_section_metadata_downloaders,
            R.string.library_opt_metadata_downloaders_help,
        ),
        typedSection(
            providers,
            IMAGE_FETCHERS,
            R.string.library_opt_section_image_fetchers,
            R.string.library_opt_image_fetchers_help,
            withImageSettings = true,
        ),
        typedSection(
            providers,
            SIMILAR_PROVIDERS,
            R.string.library_opt_section_similar_providers,
            R.string.library_opt_similar_providers_help,
        ),
    )
