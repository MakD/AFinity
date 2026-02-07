package com.makd.afinity.di

import com.makd.afinity.data.repository.PreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PreferencesEntryPoint {
    fun preferencesRepository(): PreferencesRepository
}
