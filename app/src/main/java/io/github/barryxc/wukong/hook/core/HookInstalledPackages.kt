package io.github.barryxc.wukong.hook.core

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.constant.Constant
import io.github.barryxc.wukong.hook.Bridge
import io.github.barryxc.wukong.hook.utils.Logger
import io.github.barryxc.wukong.shared.DEFAULT_HOOK_PACKAGE_NAME

object HookInstalledPackages : Hook {
    override fun hookScope(): List<String>? {
        return TEST_SCOPE
    }


    override fun doHook(
        application: Application, loadPackageParam: XC_LoadPackage.LoadPackageParam
    ) {
        hookPackageName(application, loadPackageParam)
        hookPackageManagerGetPackageInfo(application, loadPackageParam)
    }

    @SuppressLint("PrivateApi")
    private fun hookPackageName(
        application: Application, loadPackageParam: XC_LoadPackage.LoadPackageParam
    ) {
        // ========== Hook Context.getPackageName() ==========
        //仅仅 hook 目标包名
        XposedHelpers.findAndHookMethod(
            Class.forName("android.app.ContextImpl"),  // 要Hook的类
            "getPackageName", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    LockHolder.protect {
                        val originPackageName = param.result as String?
                        Bridge.getSharedService()?.let {
                            it.getString(Constant.KEY_MOCK_PACKAGE_NAME, DEFAULT_HOOK_PACKAGE_NAME)
                                .takeIf { it.isNotEmpty() }?.let { mockPackageName ->
                                    Logger.logHookMethod(
                                        param,
                                        "set result $mockPackageName,original package name is $originPackageName"
                                    )
                                    param.result = mockPackageName
                                }
                        }
                    }
                }
            });
    }

    @SuppressLint("PrivateApi")
    private fun hookPackageManagerGetPackageInfo(
        application: Application, loadPackageParam: XC_LoadPackage.LoadPackageParam
    ) {
        // 目标是 Hook 具体实现类：ApplicationPackageManager
        val packageManagerClass = Class.forName("android.app.ApplicationPackageManager")
        Logger.d("packageManagerClass: $packageManagerClass")
        packageManagerClass.declaredMethods.filter {
            it.name == "getInstalledPackages" || it.name == "getInstalledApplications"
        }.forEach { method ->
            XposedHelpers.findAndHookMethod(
                packageManagerClass,  // 要Hook的类
                method.name, *method.parameterTypes, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        LockHolder.protect {
                            val originResult = param.result as? List<*>?
                            if (originResult.isNullOrEmpty()) {
                                Logger.logHookMethod(param, "originResult is empty")
                                return@protect
                            }
                            Logger.logHookMethod(
                                param, "original installed packages is $originResult"
                            )
                            Bridge.getSharedService()?.let {
                                it.getString(
                                    Constant.KEY_MOCK_PACKAGE_NAME,
                                    DEFAULT_HOOK_PACKAGE_NAME
                                )
                                    .takeIf { it.isNotEmpty() }?.let { mockPackageName ->
                                        for (pkInfo in originResult) {
                                            when (pkInfo) {
                                                is PackageInfo -> {
                                                    if (TEST_SCOPE.isNotEmpty() && !(TEST_SCOPE.contains(
                                                            pkInfo.packageName
                                                        ))
                                                    ) {
                                                        continue
                                                    }
                                                    Logger.logHookMethod(
                                                        param,
                                                        "set result $mockPackageName,original package name is ${pkInfo.packageName}"
                                                    )
                                                    pkInfo.packageName = mockPackageName
                                                    pkInfo.applicationInfo?.packageName =
                                                        mockPackageName
                                                }

                                                is ApplicationInfo -> {
                                                    if (TEST_SCOPE.isNotEmpty() && !(TEST_SCOPE.contains(
                                                            pkInfo.packageName
                                                        ))
                                                    ) {
                                                        continue
                                                    }
                                                    Logger.logHookMethod(
                                                        param,
                                                        "set result $mockPackageName,original package name is ${pkInfo.packageName}"
                                                    )
                                                    pkInfo.packageName = mockPackageName

                                                }
                                            }
                                        }
                                        param.result = originResult
                                    }
                            }
                        }
                    }
                })
        }
    }
}