package com.makd.afinity.data.models.common

enum class CollectionType(val type: String) {
    Movies("movies"),
    TvShows("tvshows"),
    HomeVideos("homevideos"),
    Photos("photos"),
    MusicVideos("musicvideos"),
    Music("music"),
    Playlists("playlists"),
    Books("books"),
    LiveTv("livetv"),
    BoxSets("boxsets"),
    Mixed("null"),
    Unknown("unknown");

    companion object {
        val defaultValue = Unknown

        val supported =
            listOf(
                Movies,
                TvShows,
                BoxSets,
                LiveTv,
                Music,
                Playlists,
                Mixed,
                Books,
                MusicVideos,
                HomeVideos,
                Photos,
            )

        val gallery = setOf(HomeVideos, Photos)

        fun fromString(string: String?): CollectionType {
            if (string == null) {
                return Mixed
            }

            return try {
                entries.first { it.type == string }
            } catch (e: NoSuchElementException) {
                defaultValue
            }
        }
    }
}
