package io.github.barryxc.wukong.hook.core

import android.app.Application
import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.hook.Bridge
import io.github.barryxc.wukong.hook.utils.Logger

object Starter {
    fun startHook(
        loadPackageParam: XC_LoadPackage.LoadPackageParam, registry: Array<Hook>
    ) {
        hookApplicationAttach(loadPackageParam, registry)
        hookApplicationOnCreate(loadPackageParam, registry)
    }

    private fun hookApplicationAttach(
        loadPackageParam: XC_LoadPackage.LoadPackageParam, registry: Array<Hook>
    ) {
        XposedHelpers.findAndHookMethod(
            Application::class.java,
            "attach",
            Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    Logger.logHookMethod(param)
                    installHooks(param.thisObject as Application, loadPackageParam, registry)
                }
            })
    }

    private fun hookApplicationOnCreate(
        loadPackageParam: XC_LoadPackage.LoadPackageParam, registry: Array<Hook>
    ) {
        XposedHelpers.findAndHookMethod(
            Application::class.java, "onCreate", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    Logger.logHookMethod(param)
                    installHooks(param.thisObject as Application, loadPackageParam, registry)
                }
            })
    }

    private fun installHooks(
        targetApp: Application,
        loadPackageParam: XC_LoadPackage.LoadPackageParam,
        registry: Array<Hook>
    ) {
        Bridge.init(targetApp)
        registry.forEach {
            try {
                it.doHook(targetApp, loadPackageParam)
            } catch (e: Throwable) {
                e.printStackTrace(System.err)
            }
        }
    }
}
