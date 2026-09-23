package com.makd.afinity.data.repository

import com.makd.afinity.data.models.external.ExternalTitles

interface ExternalTitlesRepository {
    suspend fun getCollectionParts(collectionTmdbId: Int): ExternalTitles?

    suspend fun getPersonCredits(personTmdbId: Int): ExternalTitles?
}