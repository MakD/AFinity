package com.makd.afinity.data.models.admin

data class LibraryProvider(val name: String, val defaultEnabled: Boolean)

data class LibraryTypeProviders(
    val type: String,
    val metadataFetchers: List<LibraryProvider>,
    val imageFetchers: List<LibraryProvider>,
    val similarItemProviders: List<LibraryProvider>,
)

data class LibraryProviders(
    val metadataSavers: List<LibraryProvider>,
    val metadataReaders: List<LibraryProvider>,
    val subtitleFetchers: List<LibraryProvider>,
    val lyricFetchers: List<LibraryProvider>,
    val mediaSegmentProviders: List<LibraryProvider>,
    val types: List<LibraryTypeProviders>,
)
