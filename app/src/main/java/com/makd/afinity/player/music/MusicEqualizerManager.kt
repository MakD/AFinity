package com.makd.afinity.player.music

import android.content.Context
import com.makd.afinity.player.common.AudioEqualizerManager
import com.makd.afinity.player.common.EqualizerPreset
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicEqualizerManager
@Inject
constructor(@ApplicationContext context: Context) :
    AudioEqualizerManager(context, "music_equalizer", EqualizerPreset.MUSIC)