package io.github.barryxc.wukong.service

import android.content.Context
import android.content.SharedPreferences
import io.github.barryxc.wukong.WuKongApplication
import io.github.barryxc.wukong.shared.ISharedService

class SharedServiceImpl : ISharedService.Stub() {
    private var sp: SharedPreferences? = null

    override fun getInt(a: String, b: Int): Int {
        return getCacheData()?.getInt(a, b) ?: b
    }

    override fun getString(a: String, b: String): String {
        val result = getCacheData()?.getString(a, b) ?: b
        return result
    }

    override fun getBoolean(a: String, b: Boolean): Boolean {
        return getCacheData()?.getBoolean(a, b) ?: b
    }

    private fun getCacheData(): SharedPreferences? {
        if (sp == null) {
            sp = WuKongApplication.mApp?.getSharedPreferences("setting_cache", Context.MODE_PRIVATE);
        }
        return sp
    }
}