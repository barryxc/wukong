package io.github.barryxc.wukong.hook.core

import android.app.Application
import de.robv.android.xposed.callbacks.XC_LoadPackage

interface Hook {
    fun hookScope(): List<String>?
    fun doHook(application: Application, loadPackageParam: XC_LoadPackage.LoadPackageParam);
}