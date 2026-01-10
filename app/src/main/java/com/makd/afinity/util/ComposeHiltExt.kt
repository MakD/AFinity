package com.makd.afinity.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.makd.afinity.data.repository.PreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PreferencesRepositoryEntryPoint {
    fun preferencesRepository(): PreferencesRepository
}

@Composable
fun rememberPreferencesRepository(
    context: Context = LocalContext.current.applicationContext
): PreferencesRepository {
    return EntryPointAccessors.fromApplication(
        context,
        PreferencesRepositoryEntryPoint::class.java
    ).preferencesRepository()
}