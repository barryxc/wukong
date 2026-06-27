package io.github.barryxc.wukong.hook.core

import android.app.Application
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.hook.utils.Logger
import java.io.File
import java.lang.ProcessBuilder

object HookDetectionProbe : ApplicationHook {
    @Volatile
    private var installed = false

    override fun installWithApplication(
        application: Application,
        loadPackageParam: XC_LoadPackage.LoadPackageParam,
    ) {
        install()
    }

    fun install() {
        if (installed) {
            return
        }
        synchronized(this) {
            if (installed) {
                return
            }
            hookRuntimeExec()
            hookProcessBuilder()
            hookFileChecks()
            installed = true
        }
    }

    private fun hookRuntimeExec() {
        Runtime::class.java.declaredMethods
            .filter { it.name == "exec" }
            .forEach { method ->
                runCatching {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val command = param.args.firstOrNull()?.commandString() ?: return
                            if (isSuspiciousCommand(command)) {
                                Logger.i("[Probe#Runtime.exec] $command")
                            }
                        }
                    })
                }.onFailure {
                    Logger.e("[Probe] hook Runtime.exec failed: ${it.message}")
                }
            }
    }

    private fun hookProcessBuilder() {
        runCatching {
            XposedHelpers.findAndHookMethod(
                ProcessBuilder::class.java,
                "start",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val builder = param.thisObject as? ProcessBuilder ?: return
                        val command = builder.command().joinToString(" ")
                        if (isSuspiciousCommand(command)) {
                            Logger.i("[Probe#ProcessBuilder.start] $command")
                        }
                    }
                }
            )
        }.onFailure {
            Logger.e("[Probe] hook ProcessBuilder.start failed: ${it.message}")
        }
    }

    private fun hookFileChecks() {
        val methodNames = setOf(
            "exists",
            "canExecute",
            "canRead",
            "isFile",
            "isDirectory",
            "list",
            "listFiles",
        )
        File::class.java.declaredMethods
            .filter { it.name in methodNames && it.parameterTypes.isEmpty() }
            .forEach { method ->
                runCatching {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val path = (param.thisObject as? File)?.path ?: return
                            if (isSuspiciousPath(path)) {
                                Logger.i("[Probe#File.${method.name}] $path")
                            }
                        }
                    })
                }.onFailure {
                    Logger.e("[Probe] hook File.${method.name} failed: ${it.message}")
                }
            }
    }

    private fun Any.commandString(): String? {
        return when (this) {
            is String -> this
            is Array<*> -> joinToString(" ") { it?.toString().orEmpty() }
            else -> toString()
        }
    }

    private fun isSuspiciousCommand(command: String): Boolean {
        val lower = command.lowercase()
        return listOf(
            "getprop",
            "build.prop",
            "ro.product.",
            "ro.build.",
            "which su",
            "/su",
            "magisk",
            "zygisk",
            "riru",
            "lsposed",
            "xposed",
            "frida",
        ).any { lower.contains(it) }
    }

    private fun isSuspiciousPath(path: String): Boolean {
        val lower = path.lowercase()
        return listOf(
            "build.prop",
            "/proc/self/maps",
            "/proc/self/status",
            "/proc/self/cmdline",
            "/proc/self/mounts",
            "/proc/self/task",
            "/proc/self/fd",
            "/proc/net/",
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/magisk",
            "zygisk",
            "riru",
            "lsposed",
            "xposed",
            "frida",
            "substrate",
            "libwukong",
        ).any { lower.contains(it) }
    }
}
