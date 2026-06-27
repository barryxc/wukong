package io.github.barryxc.wukong.hook.core

import android.app.Application
import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.hook.Bridge
import io.github.barryxc.wukong.hook.utils.Logger

// Hook 安装分两步：
// 1. package load 时机：handleLoadPackage 阶段调用 installLifecycleHooksOnPackageLoad()。
//    此时通常还没有可用的 Application 实例，所以这里只注册 Application.attach/onCreate 的 hook。
// 2. Application 可用时机：目标应用执行 Application.attach 或 onCreate 时，
//    调用 installBusinessHooksWithApplication()，拿到 Application 后按统一注册表安装业务 Java hooks。
object HookInstaller {
    private val installedPackages = mutableSetOf<String>()

    // 加载时机：目标 app 进程内，handleLoadPackage 回调期间。
    // 职责：只安装 Application.attach/onCreate 的生命周期 hook，不直接安装依赖 Application/Context 的业务 hook。
    fun installLifecycleHooksOnPackageLoad(
        loadPackageParam: XC_LoadPackage.LoadPackageParam,
        registry: Array<ApplicationHook>,
    ) {
        hookApplicationAttach(loadPackageParam, registry)
        hookApplicationOnCreate(loadPackageParam, registry)
    }

    // Application.attach(Context) 是较早能拿到 Application/Context 的时机。
    // afterHookedMethod 保证原 attach 已经完成，targetApp 上下文状态更完整。
    private fun hookApplicationAttach(
        loadPackageParam: XC_LoadPackage.LoadPackageParam,
        registry: Array<ApplicationHook>,
    ) {
        XposedHelpers.findAndHookMethod(
            Application::class.java,
            "attach",
            Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    Logger.logHookMethod(param)
                    installBusinessHooksWithApplication(
                        param.thisObject as Application,
                        loadPackageParam,
                        registry
                    )
                }
            })
    }

    // onCreate 作为兜底入口：如果 attach hook 未触发或部分环境行为不同，
    // 仍可在 Application.onCreate 执行前完成 hooks 安装。
    private fun hookApplicationOnCreate(
        loadPackageParam: XC_LoadPackage.LoadPackageParam,
        registry: Array<ApplicationHook>,
    ) {
        XposedHelpers.findAndHookMethod(
            Application::class.java, "onCreate", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    Logger.logHookMethod(param)
                    installBusinessHooksWithApplication(
                        param.thisObject as Application,
                        loadPackageParam,
                        registry
                    )
                }
            })
    }

    // 加载时机：目标 app 的 Application.attach 已完成后，或 Application.onCreate 执行前。
    // 职责：真正安装业务 hooks。
    // 同一个目标进程内只按 packageName 安装一次，避免 attach/onCreate 两个入口重复安装。
    private fun installBusinessHooksWithApplication(
        targetApp: Application,
        loadPackageParam: XC_LoadPackage.LoadPackageParam,
        registry: Array<ApplicationHook>,
    ) {
        synchronized(installedPackages) {
            if (!installedPackages.add(loadPackageParam.packageName)) {
                return
            }
        }
        if (HookDebugGuard.shouldSkipBusinessHooks(loadPackageParam.packageName)) {
            return
        }
        Bridge.init(targetApp)
        Logger.i("[HookDebug] install Java hooks for ${loadPackageParam.packageName}")
        registry.forEach { hook ->
            try {
                hook.installWithApplication(targetApp, loadPackageParam)
            } catch (e: Throwable) {
                e.printStackTrace(System.err)
            }
        }
    }
}
