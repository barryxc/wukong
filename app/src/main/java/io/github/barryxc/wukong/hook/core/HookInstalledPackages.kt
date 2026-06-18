package io.github.barryxc.wukong.hook.core

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.barryxc.wukong.hook.utils.Logger
import java.util.concurrent.atomic.AtomicBoolean

object HookInstalledPackages {
    private val installed = AtomicBoolean(false)

    fun install() {
        if (!installed.compareAndSet(false, true)) {
            return
        }
        hookContextPackageName()
        hookApplicationPackageManager()
    }

    private fun hookContextPackageName() {
        val contextImpl = XposedHelpers.findClassIfExists(
            "android.app.ContextImpl",
            ClassLoader.getSystemClassLoader()
        ) ?: return

        XposedBridge.hookAllMethods(
            contextImpl,
            "getPackageName",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    LockHolder.protect {
                        val mockPackageName = HookConfig.packageName()
                        if (mockPackageName.isBlank()) {
                            return@protect
                        }
                        val originalPackageName = param.result as? String
                        if (!isTargetPackage(originalPackageName)) {
                            return@protect
                        }
                        Logger.logHookMethod(
                            param,
                            "set result $mockPackageName, original package name is $originalPackageName"
                        )
                        param.result = mockPackageName
                    }
                }
            }
        )

        XposedBridge.hookAllMethods(
            contextImpl,
            "getOpPackageName",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    LockHolder.protect {
                        if (!HookConfig.hasPackageName()) {
                            return@protect
                        }
                        // AppOps validates package/uid ownership, so attribution must stay real.
                        param.result = PRIMARY_TARGET_PACKAGE_NAME
                    }
                }
            }
        )
    }

    private fun hookApplicationPackageManager() {
        val packageManagerClass = XposedHelpers.findClassIfExists(
            "android.app.ApplicationPackageManager",
            ClassLoader.getSystemClassLoader()
        ) ?: return

        listOf(
            "getPackageInfo",
            "getApplicationInfo",
            "getInstalledPackages",
            "getInstalledApplications",
        ).forEach { methodName ->
            XposedBridge.hookAllMethods(
                packageManagerClass,
                methodName,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        LockHolder.protect {
                            val mockPackageName = HookConfig.packageName()
                            if (mockPackageName.isBlank()) {
                                return@protect
                            }
                            val firstArgument = param.args.firstOrNull() as? String
                            if (firstArgument == mockPackageName) {
                                param.args[0] = PRIMARY_TARGET_PACKAGE_NAME
                            }
                        }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        LockHolder.protect {
                            val mockPackageName = HookConfig.packageName()
                            if (mockPackageName.isBlank()) {
                                return@protect
                            }
                            replaceResult(param.result, mockPackageName)
                        }
                    }
                }
            )
        }
    }

    private fun replaceResult(result: Any?, mockPackageName: String) {
        when (result) {
            is PackageInfo -> replacePackageInfo(result, mockPackageName)
            is ApplicationInfo -> replaceApplicationInfo(result, mockPackageName)
            is List<*> -> result.forEach { replaceResult(it, mockPackageName) }
            else -> replaceParceledList(result, mockPackageName)
        }
    }

    private fun replaceParceledList(result: Any?, mockPackageName: String) {
        if (result == null) {
            return
        }
        runCatching {
            val getList = result.javaClass.methods.firstOrNull {
                it.name == "getList" && it.parameterTypes.isEmpty()
            } ?: return
            (getList.invoke(result) as? List<*>)?.forEach {
                replaceResult(it, mockPackageName)
            }
        }
    }

    private fun replacePackageInfo(info: PackageInfo, mockPackageName: String) {
        if (!isTargetPackage(info.packageName)) {
            return
        }
        info.packageName = mockPackageName
        info.applicationInfo?.let { replaceApplicationInfo(it, mockPackageName) }
    }

    private fun replaceApplicationInfo(info: ApplicationInfo, mockPackageName: String) {
        if (isTargetPackage(info.packageName)) {
            info.packageName = mockPackageName
        }
    }
}
