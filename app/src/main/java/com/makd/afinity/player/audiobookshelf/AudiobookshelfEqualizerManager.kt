package com.makd.afinity.player.audiobookshelf

import android.content.Context
import com.makd.afinity.player.common.AudioEqualizerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfEqualizerManager @Inject constructor(@ApplicationContext context: Context) :
    AudioEqualizerManager(context, "audiobookshelf_equalizer")
