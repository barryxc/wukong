package io.github.barryxc.wukong.hook

import android.annotation.SuppressLint
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.BuildConfig
import io.github.barryxc.wukong.hook.core.HookDebugGuard
import io.github.barryxc.wukong.hook.core.HookInstaller
import io.github.barryxc.wukong.hook.core.applicationHookRegistry
import io.github.barryxc.wukong.hook.utils.Logger

// LSPosed/Xposed 会在需要注入的进程内加载模块入口类。
// 对普通应用来说，目标进程已经由 zygote fork 出来之后，模块实例才会在该目标进程内创建。
// initZygote() 属于 zygote 初始化阶段回调，适合做 zygote 级别的全局初始化；当前仅记录模块路径。
// handleLoadPackage() 在目标进程加载 package 时回调，发生在 Application.attach/onCreate 之前。
// 因此这里先注册 Application 生命周期 hook，真正的业务 hook 安装会延迟到 Application 可用时执行。
// 模块更新后，需要重新启动目标应用，目标进程才会重新加载新的模块代码。
class HookModule : IXposedHookLoadPackage, IXposedHookZygoteInit {

    init {
        Logger.d("[HookModule]")
    }

    @SuppressLint("PrivateApi")
    @Throws(Throwable::class)
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == BuildConfig.APPLICATION_ID) {
            return
        }
        Logger.logHookAPP(lpparam)
        //如果需要调试，必须在 install hooks 之前，让 adb-jdwp 先链接上，才能hook，否则会导致调试进程校验失败
        HookDebugGuard.waitForDebuggerBeforeInstallingHooks(lpparam.packageName, lpparam.appInfo)
        HookInstaller.installLifecycleHooksOnPackageLoad(lpparam, applicationHookRegistry)
    }

    @Throws(Throwable::class)
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        Logger.d("[HookModule] initZygote %s", "${startupParam?.modulePath}")
    }
}
