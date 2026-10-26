package com.makd.afinity.ui.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

private const val NIGHT = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL

@Preview(name = "320dp small phone", group = "Widths", widthDp = 320)
@Preview(name = "360dp phone", group = "Widths", widthDp = 360)
@Preview(name = "411dp OnePlus 12", group = "Widths", widthDp = 411)
@Preview(name = "673dp foldable", group = "Widths", widthDp = 673)
@Preview(name = "840dp tablet", group = "Widths", widthDp = 840)
@Preview(name = "891dp phone landscape", group = "Widths", widthDp = 891)
annotation class WidthPreviews

@Preview(name = "Font 0.85", group = "Font scale", widthDp = 411, fontScale = 0.85f)
@Preview(name = "Font 1.0", group = "Font scale", widthDp = 411, fontScale = 1f)
@Preview(name = "Font 1.3", group = "Font scale", widthDp = 411, fontScale = 1.3f)
@Preview(name = "Font 1.5", group = "Font scale", widthDp = 411, fontScale = 1.5f)
@Preview(name = "Font 2.0", group = "Font scale", widthDp = 411, fontScale = 2f)
annotation class FontScalePreviews

@Preview(name = "320dp · font 2.0", group = "Stress", widthDp = 320, fontScale = 2f)
@Preview(name = "Landscape · font 1.3", group = "Stress", widthDp = 891, fontScale = 1.3f)
@Preview(
    name = "Russian · font 1.3",
    group = "Stress",
    widthDp = 411,
    fontScale = 1.3f,
    locale = "ru",
)
@Preview(
    name = "Tamil · font 1.3",
    group = "Stress",
    widthDp = 411,
    fontScale = 1.3f,
    locale = "ta",
)
@Preview(name = "Dark", group = "Stress", widthDp = 411, uiMode = NIGHT)
annotation class StressPreviews

@Preview(name = "Small phone 360×640", group = "Windows", device = "spec:width=360dp,height=640dp")
@Preview(name = "OnePlus 12 411×891", group = "Windows", device = "spec:width=411dp,height=891dp")
@Preview(
    name = "Phone landscape 891×411",
    group = "Windows",
    device = "spec:width=891dp,height=411dp",
)
@Preview(
    name = "Phone landscape · font 1.3",
    group = "Windows",
    device = "spec:width=891dp,height=411dp",
    fontScale = 1.3f,
)
@Preview(
    name = "Tablet landscape 1280×800",
    group = "Windows",
    device = "spec:width=1280dp,height=800dp",
)
@Preview(
    name = "OnePlus 12 · font 2.0 · dark",
    group = "Windows",
    device = "spec:width=411dp,height=891dp",
    fontScale = 2f,
    uiMode = NIGHT,
)
annotation class WindowPreviews
