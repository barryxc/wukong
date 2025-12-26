package io.github.barryxc.wukong.hook

import android.annotation.SuppressLint
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.hook.core.Starter
import io.github.barryxc.wukong.hook.core.TEST_SCOPE
import io.github.barryxc.wukong.hook.core.registry
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
        if (TEST_SCOPE.contains(lpparam.packageName)) {
            Logger.logHookAPP(lpparam)
            Starter.startHook(lpparam, registry)
        }
    }

    @Throws(Throwable::class)
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        Logger.d("[HookModule] initZygote %s", "${startupParam?.modulePath}")
    }
}