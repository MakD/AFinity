package com.makd.afinity.ui.livetv.models

import androidx.annotation.StringRes
import com.makd.afinity.R

enum class LiveTvCategory(@StringRes val displayNameRes: Int) {
    ON_NOW(R.string.livetv_cat_on_now),
    MOVIES(R.string.livetv_cat_movies),
    SHOWS(R.string.livetv_cat_shows),
    SPORTS(R.string.livetv_cat_sports),
    KIDS(R.string.livetv_cat_kids),
    NEWS(R.string.livetv_cat_news),
}
