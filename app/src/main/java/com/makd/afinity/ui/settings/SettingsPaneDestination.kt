package com.makd.afinity.ui.settings

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed interface SettingsPaneDestination : Parcelable {
    @Parcelize
    data object Appearance : SettingsPaneDestination

    @Parcelize
    data object Player : SettingsPaneDestination

    @Parcelize
    data object Downloads : SettingsPaneDestination

    @Parcelize
    data object ServerManagement : SettingsPaneDestination

    @Parcelize
    data object Licenses : SettingsPaneDestination

    @Parcelize
    data object SessionSwitcher : SettingsPaneDestination

    @Parcelize
    data object Language : SettingsPaneDestination

    @Parcelize
    data object QuickConnect : SettingsPaneDestination
}