package io.github.barryxc.wukong.hook.core

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.hook.utils.Logger
import java.util.concurrent.atomic.AtomicBoolean

object HookSystemPMS : ApplicationHook {
    private val installed = AtomicBoolean(false)

    override fun installWithApplication(
        application: Application,
        loadPackageParam: XC_LoadPackage.LoadPackageParam,
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
        val realPackageName = loadPackageParam.packageName

        listOf("getInstalledPackages", "getInstalledApplications").forEach { methodName ->
            val hookedMethods = XposedBridge.hookAllMethods(
                packageManager.javaClass,
                methodName,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        LockHolder.protect {
                            val mockPackageName = HookConfig.packageName()
                            if (mockPackageName.isBlank()) {
                                return@protect
                            }
                            replaceResult(
                                result = param.result,
                                realPackageName = realPackageName,
                                mockPackageName = mockPackageName
                            )
                            Logger.logHookMethod(
                                param,
                                "replace PMS package $realPackageName with $mockPackageName"
                            )
                        }
                    }
                }
            )
            if (hookedMethods.isNotEmpty()) {
                Logger.i(
                    "[Hook#SystemPMS] installed ${packageManager.javaClass.name}.$methodName"
                )
            }
        }
    }

    private fun replaceResult(
        result: Any?,
        realPackageName: String,
        mockPackageName: String
    ) {
        val packages = extractPackages(result) ?: return
        packages.forEach { packageEntry ->
            when (packageEntry) {
                is PackageInfo -> replacePackageInfo(
                    info = packageEntry,
                    realPackageName = realPackageName,
                    mockPackageName = mockPackageName
                )

                is ApplicationInfo -> replaceApplicationInfo(
                    info = packageEntry,
                    realPackageName = realPackageName,
                    mockPackageName = mockPackageName
                )
            }
        }
    }

    private fun extractPackages(result: Any?): List<*>? {
        return when (result) {
            is List<*> -> result
            null -> null
            else -> runCatching {
                val getList = result.javaClass.methods.firstOrNull {
                    it.name == "getList" && it.parameterTypes.isEmpty()
                } ?: return null
                getList.invoke(result) as? List<*>
            }.getOrNull()
        }
    }

    private fun replacePackageInfo(
        info: PackageInfo,
        realPackageName: String,
        mockPackageName: String
    ) {
        if (info.packageName != realPackageName) {
            return
        }
        info.packageName = mockPackageName
        info.applicationInfo?.let {
            replaceApplicationInfo(it, realPackageName, mockPackageName)
        }
    }

    private fun replaceApplicationInfo(
        info: ApplicationInfo,
        realPackageName: String,
        mockPackageName: String
    ) {
        if (info.packageName == realPackageName) {
            info.packageName = mockPackageName
        }
    }
}
