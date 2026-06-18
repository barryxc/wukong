package io.github.barryxc.wukong.hook.core

import android.os.Looper
import io.github.barryxc.wukong.constant.Constant
import io.github.barryxc.wukong.hook.Bridge
import io.github.barryxc.wukong.shared.DEFAULT_ANDROID_ID
import io.github.barryxc.wukong.shared.DEFAULT_BRAND
import io.github.barryxc.wukong.shared.DEFAULT_HOOK_PACKAGE_NAME
import io.github.barryxc.wukong.shared.DEFAULT_LOCATION
import io.github.barryxc.wukong.shared.DEFAULT_MODEL
import io.github.barryxc.wukong.shared.DEFAULT_PROXY
import io.github.barryxc.wukong.shared.ISharedService

object HookConfig {
    fun androidId(): String = getString(Constant.KEY_MOCK_ANDROID_ID, DEFAULT_ANDROID_ID)

    fun hasAndroidId(): Boolean = androidId().isNotBlank()

    fun packageName(): String =
        getString(Constant.KEY_MOCK_PACKAGE_NAME, DEFAULT_HOOK_PACKAGE_NAME)

    fun hasPackageName(): Boolean = packageName().isNotBlank()

    fun brand(): String = getString(Constant.KEY_MOCK_BRAND, DEFAULT_BRAND)

    fun model(): String = getString(Constant.KEY_MOCK_MODEL, DEFAULT_MODEL)

    fun hasBuildInfo(): Boolean = brand().isNotBlank() && model().isNotBlank()

    fun location(): MockLocation? {
        val config = getString(Constant.KEY_MOCK_GPS_LOCATION, DEFAULT_LOCATION)
        if (config.isBlank()) {
            return null
        }
        val parts = config.split(",")
        return MockLocation(
            latitude = parts.getOrNull(0)?.toDoubleOrNull() ?: DEFAULT_LATITUDE,
            longitude = parts.getOrNull(1)?.toDoubleOrNull() ?: DEFAULT_LONGITUDE,
            altitude = parts.getOrNull(2)?.toDoubleOrNull() ?: DEFAULT_ALTITUDE,
            accuracy = parts.getOrNull(3)?.toFloatOrNull() ?: DEFAULT_ACCURACY,
        )
    }

    fun proxy(): String = getString(Constant.KEY_MOCK_PROXY, DEFAULT_PROXY)

    private fun getString(key: String, defaultValue: String): String {
        return awaitSharedService()
            ?.getString(key, defaultValue)
            ?.trim()
            ?.takeIf { it.isNotEmpty() || defaultValue.isEmpty() }
            ?: defaultValue
    }

    private fun awaitSharedService(): ISharedService? {
        Bridge.getSharedService()?.let { return it }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return null
        }
        var waited = 0L
        while (waited < SERVICE_WAIT_TIMEOUT_MS) {
            try {
                Thread.sleep(SERVICE_WAIT_INTERVAL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return Bridge.getSharedService()
            }
            waited += SERVICE_WAIT_INTERVAL_MS
            Bridge.getSharedService()?.let { return it }
        }
        return null
    }

    data class MockLocation(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
        val accuracy: Float,
    )

    private const val SERVICE_WAIT_TIMEOUT_MS = 1000L
    private const val SERVICE_WAIT_INTERVAL_MS = 50L

    private const val DEFAULT_LATITUDE = 30.0
    private const val DEFAULT_LONGITUDE = 120.0
    private const val DEFAULT_ALTITUDE = 0.0
    private const val DEFAULT_ACCURACY = 0.5f
}
