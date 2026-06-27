package io.github.barryxc.wukong.hook.core

import android.content.pm.ApplicationInfo
import android.os.Debug
import io.github.barryxc.wukong.hook.utils.Logger

object HookDebugGuard {
    private val waitedPackages = mutableSetOf<String>()

    fun waitForDebuggerBeforeInstallingHooks(
        packageName: String,
        appInfo: ApplicationInfo?,
    ) {
        val config = readConfig()
        val appDebuggable = appInfo.isDebuggable()
        Logger.i(
            "[HookDebug] early wait_for_debugger=${config.waitForDebugger} app_debuggable=$appDebuggable for $packageName"
        )
        if (!config.waitForDebugger) {
            return
        }
        if (!appDebuggable) {
            Logger.i("[HookDebug] skip early wait: app is not debuggable for $packageName")
            return
        }
        waitForDebuggerOnce(packageName, "before LSPosed Java hooks")
    }

    fun shouldSkipBusinessHooks(packageName: String): Boolean {
        val config = readConfig()
        Logger.i("[HookDebug] skip_java_hooks=${config.skipJavaHooks} for $packageName")
        if (config.skipJavaHooks) {
            Logger.i("[HookDebug] skip Java hooks for $packageName")
        }
        return config.skipJavaHooks
    }

    private fun readConfig(): Config {
        return runCatching {
            Config(
                waitForDebugger = systemProperty(PROP_WAIT_FOR_DEBUGGER).toBoolean(),
                skipJavaHooks = systemProperty(PROP_SKIP_JAVA_HOOKS).toBoolean(),
            )
        }.onFailure {
            Logger.e("[HookDebug] read early config props failed: ${it.message}")
        }.getOrDefault(Config())
    }

    private fun waitForDebuggerOnce(packageName: String, stage: String) {
        synchronized(waitedPackages) {
            if (!waitedPackages.add(packageName)) {
                Logger.i("[HookDebug] debugger wait already handled for $packageName")
                return
            }
        }
        if (Debug.isDebuggerConnected()) {
            Logger.i("[HookDebug] debugger already connected for $packageName")
            return
        }
        Logger.i("[HookDebug] waiting for debugger $stage: $packageName")
        Debug.waitForDebugger()
        Logger.i("[HookDebug] debugger connected, continue hooks: $packageName")
    }

    private fun ApplicationInfo?.isDebuggable(): Boolean {
        return this != null && (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun systemProperty(key: String): String {
        val systemPropertiesClass = Class.forName("android.os.SystemProperties")
        return systemPropertiesClass
            .getDeclaredMethod("get", String::class.java, String::class.java)
            .invoke(null, key, "") as? String ?: ""
    }

    private data class Config(
        val waitForDebugger: Boolean = false,
        val skipJavaHooks: Boolean = false,
    )

    private const val PROP_WAIT_FOR_DEBUGGER = "debug.wukong.wait_for_debugger"
    private const val PROP_SKIP_JAVA_HOOKS = "debug.wukong.skip_java_hooks"
}
