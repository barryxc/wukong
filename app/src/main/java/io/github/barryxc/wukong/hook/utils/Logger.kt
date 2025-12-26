package io.github.barryxc.wukong.hook.utils

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage

object Logger {
    private const val TAG = "LSPosed-XHook"
    fun d(msg: String) {
        Log.d(TAG, msg)
    }

    fun d(format: String, vararg args: Any?) {
        Log.d(TAG, String.format(format, *args))
    }

    fun i(msg: String) {
        Log.d(TAG, msg)
    }

    fun i(format: String, vararg args: Any?) {
        Log.d(TAG, String.format(format, *args))
    }

    fun e(msg: String) {
        Log.e(TAG, msg)
    }

    fun e(e: Throwable) {
        Log.e(TAG, Log.getStackTraceString(e))
    }

    fun e(format: String, vararg args: Any?) {
        Log.e(TAG, String.format(format, *args))
    }

    fun logHookMethod(param: XC_MethodHook.MethodHookParam, message: String? = "") {
        val simpleClassName = param.method.declaringClass.name
        val argsStr = param.args.joinToString(", ") { arg ->
            when (arg) {
                is String -> "\"$arg\""
                is Int -> arg.toString()
                is Long -> arg.toString()
                is Float -> arg.toString()
                is Double -> arg.toString()
                is Boolean -> arg.toString()
                is CharSequence -> "\"$arg\""
                null -> "null"
                else -> arg::class.java.simpleName
            }
        }
        val methodName = param.method.name
        Log.i(
            TAG,
            "[Hook#Method] $simpleClassName.$methodName($argsStr) $message"
        )
        Thread.dumpStack()
    }

    fun logHookAPP(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        Log.i(TAG, "[Hook#Package] ${loadPackageParam.packageName}")
    }
}