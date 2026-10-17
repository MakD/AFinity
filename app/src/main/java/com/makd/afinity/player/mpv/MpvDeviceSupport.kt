package com.makd.afinity.player.mpv

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.view.Display

data class MpvDeviceCapabilities(
    val vulkanVersion: String?,
    val vulkanSupported: Boolean,
    val hdrTypes: List<Int>,
)

object MpvDeviceSupport {
    private const val MIN_VULKAN_VERSION = 0x402000
    const val MIN_VULKAN_VERSION_LABEL = "1.2"

    fun supportsVulkan(context: Context): Boolean =
        context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_VULKAN_HARDWARE_VERSION,
            MIN_VULKAN_VERSION,
        )

    fun capabilities(context: Context): MpvDeviceCapabilities =
        MpvDeviceCapabilities(
            vulkanVersion = vulkanVersion(context),
            vulkanSupported = supportsVulkan(context),
            hdrTypes = hdrTypes(context),
        )

    fun isVulkanVerified(capabilities: MpvDeviceCapabilities, savedSignature: String?): Boolean =
        capabilities.vulkanSupported && savedSignature == signature(capabilities)

    fun signature(capabilities: MpvDeviceCapabilities): String =
        "vulkan=${capabilities.vulkanVersion ?: "none"};hdr=${capabilities.hdrTypes.isNotEmpty()}"

    private fun vulkanVersion(context: Context): String? =
        context.packageManager.systemAvailableFeatures
            .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
            ?.version
            ?.let { "${(it ushr 22) and 0x7f}.${(it ushr 12) and 0x3ff}.${it and 0xfff}" }

    private fun hdrTypes(context: Context): List<Int> {
        val displayManager =
            context.getSystemService(DisplayManager::class.java) ?: return emptyList()
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptyList()
        return display.mode.supportedHdrTypes.toList()
    }
}
