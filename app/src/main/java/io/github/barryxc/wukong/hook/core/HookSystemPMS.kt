package io.github.barryxc.wukong.hook.core

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.hook.utils.Logger
import java.util.concurrent.atomic.AtomicBoolean

object HookSystemPMS : Hook {
    private val installed = AtomicBoolean(false)

    override fun doHook(
        application: Application,
        loadPackageParam: XC_LoadPackage.LoadPackageParam
    ) {
        if (!installed.compareAndSet(false, true)) {
            return
        }
        val activityThread = loadPackageParam.classLoader
            .loadClass("android.app.ActivityThread")
            .getMethod("currentActivityThread")
            .invoke(null)
        val packageManager = activityThread.javaClass
            .getMethod("getPackageManager")
            .invoke(activityThread) ?: return

        listOf("getInstalledPackages", "getInstalledApplications").forEach { methodName ->
            XposedBridge.hookAllMethods(
                packageManager.javaClass,
                methodName,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        LockHolder.protect {
                            val mockPackageName = HookConfig.packageName()
                            if (mockPackageName.isBlank()) {
                                return@protect
                            }
                            replaceResult(param.result, mockPackageName)
                            Logger.logHookMethod(param, "updated package manager result")
                        }
                    }
                }
            )
        }
    }

    private fun replaceResult(result: Any?, mockPackageName: String) {
        val packages = when (result) {
            is List<*> -> result
            null -> return
            else -> runCatching {
                val getList = result.javaClass.methods.firstOrNull {
                    it.name == "getList" && it.parameterTypes.isEmpty()
                } ?: return
                getList.invoke(result) as? List<*>
            }.getOrNull()
        } ?: return

        packages.forEach { packageEntry ->
            when (packageEntry) {
                is PackageInfo -> {
                    if (isTargetPackage(packageEntry.packageName)) {
                        packageEntry.packageName = mockPackageName
                        packageEntry.applicationInfo?.packageName = mockPackageName
                    }
                }

                is ApplicationInfo -> {
                    if (isTargetPackage(packageEntry.packageName)) {
                        packageEntry.packageName = mockPackageName
                    }
                }
            }
        }
    }
}
