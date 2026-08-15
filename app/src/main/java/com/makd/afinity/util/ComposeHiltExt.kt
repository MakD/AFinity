package com.makd.afinity.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.storage.StorageLocationProvider
import com.makd.afinity.ui.item.delegates.ItemDownloadDelegate
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
    return EntryPointAccessors.fromApplication(context, PreferencesRepositoryEntryPoint::class.java)
        .preferencesRepository()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ItemDownloadDelegateEntryPoint {
    fun itemDownloadDelegate(): ItemDownloadDelegate
}

@Composable
fun rememberItemDownloadDelegate(
    context: Context = LocalContext.current.applicationContext
): ItemDownloadDelegate {
    return EntryPointAccessors.fromApplication(
            context,
            ItemDownloadDelegateEntryPoint::class.java,
        )
        .itemDownloadDelegate()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StorageLocationProviderEntryPoint {
    fun storageLocationProvider(): StorageLocationProvider
}

@Composable
fun rememberStorageLocationProvider(
    context: Context = LocalContext.current.applicationContext
): StorageLocationProvider {
    return EntryPointAccessors.fromApplication(
            context,
            StorageLocationProviderEntryPoint::class.java,
        )
        .storageLocationProvider()
}
