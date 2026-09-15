package com.makd.afinity.data.repository.server

import android.content.Context
import com.makd.afinity.R
import org.jellyfin.sdk.model.ServerVersion

object ServerVersionSupport {

    val minimum: ServerVersion = ServerVersion(12, 0, 0)

    val minimumDisplay: String = minimum.toString(2)

    fun isSupported(version: String?): Boolean {
        val parsed = version?.let { ServerVersion.fromString(it) } ?: return true
        return parsed >= minimum
    }

    fun unsupportedMessage(context: Context, version: String?): String =
        if (version.isNullOrBlank()) {
            context.getString(R.string.server_unsupported_version_unknown, minimumDisplay)
        } else {
            context.getString(R.string.server_unsupported_version, version, minimumDisplay)
        }
}
