package com.makd.afinity.ui.livetv.models

import androidx.annotation.StringRes
import com.makd.afinity.R
import com.makd.afinity.data.models.livetv.ProgramCategory

enum class LiveTvCategory(
    @param:StringRes val displayNameRes: Int,
    val programCategory: ProgramCategory?,
) {
    ON_NOW(R.string.livetv_cat_on_now, null),
    MOVIES(R.string.livetv_cat_movies, ProgramCategory.MOVIES),
    SHOWS(R.string.livetv_cat_shows, ProgramCategory.SHOWS),
    SPORTS(R.string.livetv_cat_sports, ProgramCategory.SPORTS),
    KIDS(R.string.livetv_cat_kids, ProgramCategory.KIDS),
    NEWS(R.string.livetv_cat_news, ProgramCategory.NEWS),
}
