package com.makd.afinity.ui.admin.devices

import java.time.LocalDateTime
import java.util.UUID

enum class DeviceKind {
    PHONE,
    TABLET,
    TV,
    BROWSER,
    MUSIC,
    SERVICE,
    OTHER,
}

enum class DeviceFilter {
    ALL,
    THIS_WEEK,
    IDLE,
}

data class DeviceRecord(
    val id: String,
    val name: String,
    val appName: String,
    val appVersion: String,
    val userId: UUID?,
    val userName: String?,
    val lastActivity: LocalDateTime?,
    val iconUrl: String?,
    val logoUrl: String?,
    val isThisDevice: Boolean,
)

data class AdminDeviceEntry(
    val id: String,
    val version: String,
    val lastActivity: LocalDateTime?,
    val isThisDevice: Boolean,
)

data class AdminDeviceGroup(
    val key: String,
    val name: String,
    val app: String,
    val kind: DeviceKind,
    val iconUrl: String?,
    val logoUrl: String?,
    val userId: UUID?,
    val userName: String?,
    val userImageUrl: String?,
    val entries: List<AdminDeviceEntry>,
) {
    val lastActivity: LocalDateTime?
        get() = entries.firstOrNull()?.lastActivity

    val isThisDevice: Boolean
        get() = entries.any { it.isThisDevice }

    val removableIds: List<String>
        get() = entries.filterNot { it.isThisDevice }.map { it.id }
}

private val serviceApps = listOf("seerr", "home assistant", "jellystat", "streamystats")
private val musicApps = listOf("finamp", "symfonium", "feishin", "sonixd", "jellify", "amcfy")
private val browserNames = listOf("chrome", "firefox", "safari", "edge", "opera", "brave")
private val tvWords =
    listOf(
        "tv",
        "androidtv",
        "firetv",
        "appletv",
        "tvos",
        "roku",
        "kodi",
        "webos",
        "tizen",
        "wholphin",
        "shield",
        "chromecast",
        "xbox",
        "ps4",
    )
private val phoneApps =
    listOf("android", "ios", "afinity", "findroid", "streamyfin", "swiftfin", "mobile")
private val phoneNames = listOf("iphone", "pixel", "galaxy", "phone", "android", "oneplus", "redmi")
private val tabletPattern = Regex("\\b(ipad|tablet|tab)\\b")
private val tvPattern = Regex("\\b(${tvWords.joinToString("|")})\\b")

internal fun deviceKind(appName: String, deviceName: String): DeviceKind {
    val app = appName.lowercase()
    val name = deviceName.lowercase()
    return when {
        serviceApps.any { it in app } -> DeviceKind.SERVICE
        musicApps.any { it in app } -> DeviceKind.MUSIC
        tvPattern.containsMatchIn(app) || tvPattern.containsMatchIn(name) -> DeviceKind.TV
        "ipados" in app || tabletPattern.containsMatchIn(name) -> DeviceKind.TABLET
        "web" in app || browserNames.any { name.startsWith(it) } -> DeviceKind.BROWSER
        phoneApps.any { it in app } || phoneNames.any { it in name } -> DeviceKind.PHONE
        else -> DeviceKind.OTHER
    }
}

private const val WEB_APP_NAME = "jellyfin web"
private const val WEB_LOGO_PATH = "/web/assets/img/devices/"

private val appLogos =
    mapOf(
        "samsung smart tv" to "samsungtv",
        "xbox one" to "xbox",
        "sony ps4" to "playstation",
        "kodi" to "kodi",
        "kodi jellycon" to "kodi",
        "jellyfin android" to "android",
        "androidtv" to "android",
        "android tv" to "android",
        "jellyfin android tv" to "android",
        "jellyfin for android" to "android",
        "jellyfin for android tv" to "android",
        "jellyfin mobile (ios)" to "apple",
        "jellyfin mobile (ipados)" to "apple",
        "jellyfin ios" to "apple",
        "jellyfin ipados" to "apple",
        "jellyfin tvos" to "apple",
        "swiftfin ipados" to "apple",
        "swiftfin ios" to "apple",
        "swiftfin tvos" to "apple",
        "infuse" to "apple",
        "infuse-direct" to "apple",
        "infuse-library" to "apple",
        "home assistant" to "home-assistant",
        "jellyfin for webos" to "webos",
        "lg smart tv" to "webos",
        "jellyfin roku" to "roku",
        "jellyfin for titan os" to "titanos",
        "finamp" to "finamp",
    )

private val browserLogos =
    mapOf(
        "opera" to "opera",
        "opera tv" to "opera",
        "opera android" to "opera",
        "chrome" to "chrome",
        "chrome android" to "chrome",
        "firefox" to "firefox",
        "firefox android" to "firefox",
        "safari" to "safari",
        "safari ipad" to "safari",
        "safari iphone" to "safari",
        "edge chromium" to "edgechromium",
        "edge chromium android" to "edgechromium",
        "edge chromium ipad" to "edgechromium",
        "edge chromium iphone" to "edgechromium",
        "edge" to "edge",
        "internet explorer" to "msie",
        "titan os" to "titanos",
        "vega os" to "firetv",
    )

internal fun webDeviceLogo(appName: String, deviceName: String): String? {
    val app = appName.trim().lowercase()
    return if (app == WEB_APP_NAME) browserLogos[deviceName.trim().lowercase()] else appLogos[app]
}

internal fun webDeviceLogoUrl(baseUrl: String, appName: String, deviceName: String): String? =
    webDeviceLogo(appName, deviceName)?.let { "${baseUrl.trimEnd('/')}$WEB_LOGO_PATH$it.svg" }

internal fun buildDeviceGroups(
    records: List<DeviceRecord>,
    userImageUrls: Map<UUID, String>,
): List<AdminDeviceGroup> =
    records
        .groupBy { Triple(it.name, it.appName, it.userId) }
        .map { (key, members) ->
            val sorted = members.sortedWith(compareByDescending<DeviceRecord> { it.lastActivity })
            val lead = sorted.first()
            AdminDeviceGroup(
                key = "${key.first}|${key.second}|${key.third}",
                name = lead.name,
                app = lead.appName,
                kind = deviceKind(lead.appName, lead.name),
                iconUrl = sorted.firstNotNullOfOrNull { it.iconUrl },
                logoUrl = sorted.firstNotNullOfOrNull { it.logoUrl },
                userId = lead.userId,
                userName = lead.userName,
                userImageUrl = lead.userId?.let { userImageUrls[it] },
                entries =
                    sorted.map { record ->
                        AdminDeviceEntry(
                            id = record.id,
                            version = record.appVersion,
                            lastActivity = record.lastActivity,
                            isThisDevice = record.isThisDevice,
                        )
                    },
            )
        }
        .sortedWith(
            compareByDescending<AdminDeviceGroup> { it.isThisDevice }
                .thenByDescending { it.lastActivity }
        )

internal fun AdminDeviceGroup.matches(
    filter: DeviceFilter,
    weekAgo: LocalDateTime,
    idleBefore: LocalDateTime,
): Boolean {
    val latest = lastActivity
    return when (filter) {
        DeviceFilter.ALL -> true
        DeviceFilter.THIS_WEEK -> isThisDevice || latest?.isAfter(weekAgo) == true
        DeviceFilter.IDLE -> !isThisDevice && latest?.isAfter(idleBefore) != true
    }
}
