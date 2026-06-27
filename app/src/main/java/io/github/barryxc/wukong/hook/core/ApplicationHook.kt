package io.github.barryxc.wukong.hook.core

import android.app.Application
import de.robv.android.xposed.callbacks.XC_LoadPackage

interface ApplicationHook {
    fun installWithApplication(
        application: Application,
        loadPackageParam: XC_LoadPackage.LoadPackageParam,
    )
}
