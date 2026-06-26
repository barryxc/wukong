package io.github.barryxc.wukong.hook

import android.annotation.SuppressLint
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.BuildConfig
import io.github.barryxc.wukong.hook.core.HookDebugGuard
import io.github.barryxc.wukong.hook.core.Starter
import io.github.barryxc.wukong.hook.core.applicationRegistry
import io.github.barryxc.wukong.hook.core.earlyInstallers
import io.github.barryxc.wukong.hook.utils.Logger

// XposedModule,实例化&callback都是被执行在被hook的目标应用进程内
// 模块更新后，需要重新启动目标应用，才会更新模块代码
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
        Starter.startHook(lpparam, earlyInstallers, applicationRegistry)
    }

    @Throws(Throwable::class)
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        Logger.d("[HookModule] initZygote %s", "${startupParam?.modulePath}")
    }
}
