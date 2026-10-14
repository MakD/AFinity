package com.makd.afinity.player.mpv

import android.content.Context
import android.view.Surface
import `is`.xyz.mpv.MPVLib

class MpvHandle private constructor() {

    private val observers = mutableListOf<MPVLib.EventObserver>()

    val isActive: Boolean
        get() = current === this

    fun init() {
        if (isActive) MPVLib.init()
    }

    fun destroy() {
        synchronized(lock) {
            if (current !== this) return
            current = null
            removeObservers()
            MPVLib.destroy()
        }
    }

    fun attachSurface(surface: Surface) {
        if (isActive) MPVLib.attachSurface(surface)
    }

    fun detachSurface() {
        if (isActive) MPVLib.detachSurface()
    }

    fun command(vararg cmd: String) {
        if (isActive) MPVLib.command(*cmd)
    }

    fun setOptionString(name: String, value: String): Int =
        if (isActive) MPVLib.setOptionString(name, value) else -1

    fun getPropertyInt(property: String): Int? =
        if (isActive) MPVLib.getPropertyInt(property) else null

    fun getPropertyDouble(property: String): Double? =
        if (isActive) MPVLib.getPropertyDouble(property) else null

    fun getPropertyBoolean(property: String): Boolean? =
        if (isActive) MPVLib.getPropertyBoolean(property) else null

    fun getPropertyString(property: String): String? =
        if (isActive) MPVLib.getPropertyString(property) else null

    fun setPropertyInt(property: String, value: Int) {
        if (isActive) MPVLib.setPropertyInt(property, value)
    }

    fun setPropertyDouble(property: String, value: Double) {
        if (isActive) MPVLib.setPropertyDouble(property, value)
    }

    fun setPropertyBoolean(property: String, value: Boolean) {
        if (isActive) MPVLib.setPropertyBoolean(property, value)
    }

    fun setPropertyString(property: String, value: String) {
        if (isActive) MPVLib.setPropertyString(property, value)
    }

    fun observeProperty(property: String, format: Int) {
        if (isActive) MPVLib.observeProperty(property, format)
    }

    fun addObserver(observer: MPVLib.EventObserver) {
        if (!isActive) return
        observers.add(observer)
        MPVLib.addObserver(observer)
    }

    fun removeObserver(observer: MPVLib.EventObserver) {
        observers.remove(observer)
        MPVLib.removeObserver(observer)
    }

    private fun removeObservers() {
        observers.forEach { MPVLib.removeObserver(it) }
        observers.clear()
    }

    companion object {
        private val lock = Any()

        @Volatile private var current: MpvHandle? = null

        fun create(context: Context): MpvHandle =
            synchronized(lock) {
                current?.destroy()
                MPVLib.create(context.applicationContext)
                MpvHandle().also { current = it }
            }
    }
}
