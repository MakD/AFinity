package com.makd.afinity.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.makd.afinity.R

val GoogleSansFlex =
    FontFamily(
        Font(R.font.google_sans_flex_thin, FontWeight.Thin),
        Font(R.font.google_sans_flex_extra_light, FontWeight.ExtraLight),
        Font(R.font.google_sans_flex_light, FontWeight.Light),
        Font(R.font.google_sans_flex_regular, FontWeight.Normal),
        Font(R.font.google_sans_flex_medium, FontWeight.Medium),
        Font(R.font.google_sans_flex_semi_bold, FontWeight.SemiBold),
        Font(R.font.google_sans_flex_bold, FontWeight.Bold),
        Font(R.font.google_sans_flex_extra_bold, FontWeight.ExtraBold),
        Font(R.font.google_sans_flex_black, FontWeight.Black),
    )

val Quicksand =
    FontFamily(
        Font(R.font.quicksand_light, FontWeight.Light),
        Font(R.font.quicksand_regular, FontWeight.Normal),
        Font(R.font.quicksand_medium, FontWeight.Medium),
        Font(R.font.quicksand_semibold, FontWeight.SemiBold),
        Font(R.font.quicksand_bold, FontWeight.Bold),
    )

val IBMPlexSans =
    FontFamily(
        Font(R.font.ibmplexsans_regular, FontWeight.Normal),
        Font(R.font.ibmplexsans_medium, FontWeight.Medium),
        Font(R.font.ibmplexsans_bold, FontWeight.Bold),
    )

val IBMPlexSansCondensed =
    FontFamily(
        Font(R.font.ibmplexsans_condensed_regular, FontWeight.Normal),
        Font(R.font.ibmplexsans_condensed_medium, FontWeight.Medium),
        Font(R.font.ibmplexsans_condensed_bold, FontWeight.Bold),
    )

val NetflixSans =
    FontFamily(
        Font(R.font.netflix_sans_bold, FontWeight.Bold),
        Font(R.font.netflix_sans_medium, FontWeight.Medium),
        Font(R.font.netflix_sans_light, FontWeight.Light),
    )

val CenterLineHeightStyle =
    LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

private val NoPaddingPlatformTextStyle = PlatformTextStyle(includeFontPadding = false)

fun getAppTypography(appFont: AppFont): Typography {
    val selectedFontFamily =
        when (appFont) {
            AppFont.DEFAULT -> FontFamily.Default
            AppFont.GOOGLE_SANS -> GoogleSansFlex
            AppFont.QUICKSAND -> Quicksand
            AppFont.IBM_PLEX_SANS -> IBMPlexSans
            AppFont.IBM_PLEX_SANS_CONDENSED -> IBMPlexSansCondensed
            AppFont.NETFLIX_SANS -> NetflixSans
        }

    return Typography(
        displayLarge =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.25).sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        displayMedium =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = 0.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        displaySmall =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = 0.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        titleLarge =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        titleMedium =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        titleSmall =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        bodySmall =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        labelLarge =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        labelMedium =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        labelSmall =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
    )
}
