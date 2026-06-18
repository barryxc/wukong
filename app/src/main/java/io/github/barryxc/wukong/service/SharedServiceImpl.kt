package io.github.barryxc.wukong.service

import android.content.Context
import android.content.SharedPreferences
import io.github.barryxc.wukong.WuKongApplication
import io.github.barryxc.wukong.constant.Constant
import io.github.barryxc.wukong.shared.ISharedService

class SharedServiceImpl : ISharedService.Stub() {
    private var sp: SharedPreferences? = null
    private val allowedKeys = setOf(
        Constant.KEY_MOCK_ANDROID_ID,
        Constant.KEY_MOCK_BRAND,
        Constant.KEY_MOCK_MODEL,
        Constant.KEY_MOCK_GPS_LOCATION,
        Constant.KEY_MOCK_PACKAGE_NAME,
        Constant.KEY_MOCK_PROXY,
    )

    override fun getInt(a: String, b: Int): Int {
        if (a !in allowedKeys) {
            return b
        }
        return getCacheData()?.getInt(a, b) ?: b
    }

    override fun getString(a: String, b: String): String {
        if (a !in allowedKeys) {
            return b
        }
        return getCacheData()?.getString(a, b) ?: b
    }

    override fun getBoolean(a: String, b: Boolean): Boolean {
        if (a !in allowedKeys) {
            return b
        }
        return getCacheData()?.getBoolean(a, b) ?: b
    }

    private fun getCacheData(): SharedPreferences? {
        if (sp == null) {
            sp = WuKongApplication.mApp?.getSharedPreferences("setting_cache", Context.MODE_PRIVATE);
        }
        return sp
    }
}
