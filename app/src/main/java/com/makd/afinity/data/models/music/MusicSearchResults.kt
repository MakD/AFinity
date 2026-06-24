package com.makd.afinity.data.models.music

data class MusicSearchResults(
    val tracks: List<AfinityTrack>,
    val albums: List<AfinityAlbum>,
    val artists: List<AfinityArtist>,
    val playlists: List<AfinityPlaylist>,
)