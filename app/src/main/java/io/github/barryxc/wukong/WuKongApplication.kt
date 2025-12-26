package io.github.barryxc.wukong

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.tencent.mmkv.MMKV

class WuKongApplication : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        var mApp: Context? = null
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        mApp = base?.applicationContext;
        MMKV.initialize(this)
    }

    override fun onCreate() {
        super.onCreate()
        mApp = this
    }
}