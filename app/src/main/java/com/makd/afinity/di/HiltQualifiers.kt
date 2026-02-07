package com.makd.afinity.di

import javax.inject.Qualifier

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class JellyfinApiClient

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class BackgroundApiClient

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CurrentServerUrl

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CurrentUserToken

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainDispatcher

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AudiobookshelfClient
