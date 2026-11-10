package com.makd.afinity.data.models.media

import java.util.UUID

data class ThemeSong(val id: UUID, val ownerId: UUID)

data class ThemeSongLookup(val song: ThemeSong?, val ownerId: UUID?)
