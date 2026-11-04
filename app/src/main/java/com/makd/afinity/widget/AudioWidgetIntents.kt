package com.makd.afinity.widget

object AudioWidgetIntents {
    const val ACTION_PLAY_PAUSE = "com.makd.afinity.widget.action.PLAY_PAUSE"
    const val ACTION_PREVIOUS = "com.makd.afinity.widget.action.PREVIOUS"
    const val ACTION_NEXT = "com.makd.afinity.widget.action.NEXT"
    const val ACTION_REWIND = "com.makd.afinity.widget.action.REWIND"
    const val ACTION_FORWARD = "com.makd.afinity.widget.action.FORWARD"
    const val ACTION_SHUFFLE_ALL = "com.makd.afinity.widget.action.SHUFFLE_ALL"
    const val ACTION_RESUME_MUSIC = "com.makd.afinity.widget.action.RESUME_MUSIC"
    const val ACTION_PLAY_ABS_ITEM = "com.makd.afinity.widget.action.PLAY_ABS_ITEM"

    const val EXTRA_OPEN_PLAYER = "com.makd.afinity.widget.extra.OPEN_PLAYER"
    const val EXTRA_ABS_ITEM_ID = "com.makd.afinity.widget.extra.ABS_ITEM_ID"
    const val EXTRA_ABS_EPISODE_ID = "com.makd.afinity.widget.extra.ABS_EPISODE_ID"

    const val PLAYER_MUSIC = "music"
    const val PLAYER_ABS = "abs"
}
