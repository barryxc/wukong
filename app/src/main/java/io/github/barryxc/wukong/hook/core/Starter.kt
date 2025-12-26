package io.github.barryxc.wukong.hook.core

import android.app.Application
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.hook.Bridge
import io.github.barryxc.wukong.hook.utils.Logger

object Starter {
    fun startHook(
        loadPackageParam: XC_LoadPackage.LoadPackageParam, registry: Array<Hook>
    ) {
        XposedHelpers.findAndHookMethod(
            Application::class.java, "onCreate", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    Logger.logHookMethod(param)
                    val targetApp = param.thisObject as Application
                    Bridge.init(targetApp)
                    registry.forEach {
                        if (it.hookScope() != null && !(it.hookScope()!!
                                .contains(loadPackageParam.packageName))
                        ) {
                            return@forEach
                        }
                        try {
                            it.doHook(targetApp, loadPackageParam)
                        } catch (e: Throwable) {
                            e.printStackTrace(System.err)
                        }
                    }
                }
            })
    }
}