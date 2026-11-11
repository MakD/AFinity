package com.makd.afinity.data.models.admin

data class LibraryProvider(val name: String, val defaultEnabled: Boolean)

data class LibraryImageOption(val type: String, val limit: Int, val minWidth: Int)

data class LibraryTypeProviders(
    val type: String,
    val metadataFetchers: List<LibraryProvider>,
    val imageFetchers: List<LibraryProvider>,
    val similarItemProviders: List<LibraryProvider>,
    val supportedImageTypes: List<String> = emptyList(),
    val defaultImageOptions: List<LibraryImageOption> = emptyList(),
)

data class LibraryProviders(
    val metadataSavers: List<LibraryProvider>,
    val metadataReaders: List<LibraryProvider>,
    val subtitleFetchers: List<LibraryProvider>,
    val lyricFetchers: List<LibraryProvider>,
    val mediaSegmentProviders: List<LibraryProvider>,
    val types: List<LibraryTypeProviders>,
)
