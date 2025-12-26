package io.github.barryxc.wukong.hook

import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import de.robv.android.xposed.XposedBridge
import io.github.barryxc.wukong.BuildConfig
import io.github.barryxc.wukong.hook.utils.Logger
import io.github.barryxc.wukong.shared.ISharedService

@SuppressLint("StaticFieldLeak")
object Bridge {
    private var moduleContext: Context? = null
    private var targetAppContext: Context? = null
    private var moduleSharedService: ISharedService? = null
    fun init(targetApplication: Application) {
        initContext(targetApplication)
    }

    fun initContext(targetApplication: Application) {
        if (targetAppContext == null) {
            this.targetAppContext = targetApplication
        }
        if (moduleContext == null) {
            try {
                val modulePkg = BuildConfig.APPLICATION_ID
                moduleContext = targetAppContext?.createPackageContext(
                    modulePkg, Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE
                )
                Logger.d("Module context: $moduleContext")
                connectSharedService()
            } catch (e: Throwable) {
                XposedBridge.log("Failed to get module context: $e")
            }
        }
    }

    private fun connectSharedService() {
        bindRemoteService(targetAppContext!!)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Logger.d("Service connected: $name")
            // 通过 Stub.asInterface 获取远程服务实例

            synchronized(Bridge) {
                moduleSharedService = ISharedService.Stub.asInterface(service)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Logger.d("Service disconnected: $name")
            synchronized(Bridge) {
                moduleSharedService = null
            }
        }
    }

    private fun bindRemoteService(context: Context) {
        val intent = Intent().apply {
            setComponent(
                ComponentName(
                    BuildConfig.APPLICATION_ID, "io.github.barryxc.wukong.service.SharedService"
                )
            )
        }
        val bindService = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (!bindService) {
            Logger.e("Failed to bind to remote service")
        }
    }

    fun getSharedService(): ISharedService? {
        synchronized(Bridge) {
            return moduleSharedService
        }
    }
}