package io.github.barryxc.wukong.hook.core

import android.app.Application
import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.core.os.bundleOf
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.hook.utils.Logger

object HookAndroidId : Hook {
    @Volatile
    private var installed = false

    override fun doHook(
        application: Application,
        loadPackageParam: XC_LoadPackage.LoadPackageParam
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
            doInstall()
            installed = true
        }
    }

    private fun doInstall() {
        hookSettingsSecureGetString()
        hookSettingsNameValueCache()
        hookContentResolverQuery()
        hookContentResolverCall()
    }

    private fun hookContentResolverCall() {
        val queryMethods = ContentResolver::class.java.declaredMethods.filter {
            it.name == "call" && it.parameterTypes.size == 4
        }
        queryMethods.forEach { method ->
            XposedHelpers.findAndHookMethod(
                ContentResolver::class.java,
                method.name,
                *method.parameterTypes,
                object : XC_MethodHook() {

                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val target = param.args.getOrNull(0)
                        val callMethod = param.args.getOrNull(1) as? String
                        val callMethodArg = param.args.getOrNull(2) as? String
                        if (isSecureSettingsCallTarget(target)
                            && callMethod == "GET_secure"
                            && callMethodArg == Settings.Secure.ANDROID_ID
                        ) {
                            val mockAndroidId = HookConfig.androidId()
                            if (mockAndroidId.isBlank()) {
                                return
                            }
                            Logger.logHookMethod(param, "set android_id result")
                            param.result = bundleOf("value" to mockAndroidId)
                        }
                    }
                })
        }
    }

    private fun hookSettingsSecureGetString() {

        // Hook getString 与部分系统版本可反射访问到的 getStringForUser。
        val queryMethods = Settings.Secure::class.java.declaredMethods.filter {
            (it.name == "getString" || it.name == "getStringForUser") && it.parameterTypes.size >= 2
        }
        queryMethods.forEach { method ->
            XposedHelpers.findAndHookMethod(
                Settings.Secure::class.java,
                method.name,
                *method.parameterTypes,
                object : XC_MethodHook() {

                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val contentResolver = param.args.getOrNull(0) as? ContentResolver
                        val key = param.args.getOrNull(1) as? String
                        if (contentResolver != null && key == Settings.Secure.ANDROID_ID
                        ) {
                            val mockAndroidId = HookConfig.androidId()
                            if (mockAndroidId.isBlank()) {
                                return
                            }
                            Logger.logHookMethod(param, "set android_id result")
                            param.result = mockAndroidId
                        }
                    }
                })
        }


    }

    private fun hookSettingsNameValueCache() {
        runCatching {
            Class.forName("android.provider.Settings\$NameValueCache")
        }.onSuccess { cacheClass ->
            cacheClass.declaredMethods
                .filter { it.name == "getString" || it.name == "getStringForUser" }
                .forEach { method ->
                    runCatching {
                        XposedHelpers.findAndHookMethod(
                            cacheClass,
                            method.name,
                            *method.parameterTypes,
                            object : XC_MethodHook() {
                                @Throws(Throwable::class)
                                override fun afterHookedMethod(param: MethodHookParam) {
                                    if (param.args.any { it == Settings.Secure.ANDROID_ID }) {
                                        val mockAndroidId = HookConfig.androidId()
                                        if (mockAndroidId.isBlank()) {
                                            return
                                        }
                                        Logger.logHookMethod(param, "set android_id cache result")
                                        param.result = mockAndroidId
                                    }
                                }
                            }
                        )
                    }.onFailure {
                        Logger.e("[AndroidId] hook NameValueCache.${method.name} failed: ${it.message}")
                    }
                }
        }.onFailure {
            Logger.e("[AndroidId] NameValueCache not found: ${it.message}")
        }
    }

    private fun hookContentResolverQuery() {
        try {
            // Hook多个重载的query方法
            val queryMethods = ContentResolver::class.java.declaredMethods.filter {
                it.name == "query" && it.parameterTypes.size >= 1
            }

            queryMethods.forEach { method ->
                XposedHelpers.findAndHookMethod(
                    ContentResolver::class.java,
                    method.name,
                    *method.parameterTypes,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                val uri = param.args.getOrNull(0) as? Uri
                                val projection = param.args.getStringArrayOrNull(1)
                                val selection = param.args.getOrNull(2) as? String
                                val queryBundle = param.args.getOrNull(2) as? Bundle
                                val selectionArgs = param.args.getStringArrayOrNull(3)
                                if (uri != null && isAndroidIdQuery(uri, selection, selectionArgs, queryBundle)) {
                                    val mockAndroidId = HookConfig.androidId()
                                    if (mockAndroidId.isBlank()) {
                                        return
                                    }
                                    Logger.logHookMethod(param, "set android_id cursor")
                                    param.result = buildAndroidIdCursor(projection, mockAndroidId)
                                }
                            } catch (e: Exception) {
                                XposedBridge.log("Error in query hook: ${e.message}")
                            }
                        }
                    })
            }
        } catch (e: Exception) {
            XposedBridge.log("Failed to hook ContentResolver query methods: ${e.message}")
        }
    }

    private fun isAndroidIdQuery(
        uri: Uri?, selection: String?, args: Array<String>?, queryBundle: Bundle?
    ): Boolean {
        if (!isSettingsUri(uri)) {
            return false
        }
        if (uri?.lastPathSegment == Settings.Secure.ANDROID_ID) {
            return true
        }
        return selection == Settings.Secure.ANDROID_ID
            || args?.any { it == Settings.Secure.ANDROID_ID } == true
            || queryBundle?.containsSettingName(Settings.Secure.ANDROID_ID) == true
    }

    private fun isSecureSettingsCallTarget(target: Any?): Boolean {
        return when (target) {
            is Uri -> isSettingsUri(target)
            is String -> target == "settings"
            else -> false
        }
    }

    private fun isSettingsUri(uri: Uri?): Boolean {
        if (uri == null || uri.authority != "settings") {
            return false
        }
        val segments = uri.pathSegments
        return segments.firstOrNull() == "secure" || segments.firstOrNull() == "system"
    }

    @Suppress("DEPRECATION")
    private fun Bundle.containsSettingName(name: String): Boolean {
        for (key in keySet()) {
            val value = get(key)
            if (value == name) {
                return true
            }
            if (value is Array<*> && value.any { it == name }) {
                return true
            }
            if (value is Iterable<*> && value.any { it == name }) {
                return true
            }
        }
        return false
    }

    private fun Array<Any?>.getStringArrayOrNull(index: Int): Array<String>? {
        val value = getOrNull(index) ?: return null
        if (value !is Array<*>) {
            return null
        }
        if (value.any { it != null && it !is String }) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return value as Array<String>
    }

    private fun buildAndroidIdCursor(projection: Array<String>?, mockAndroidId: String): MatrixCursor {
        val columns = projection
            ?.takeIf { it.isNotEmpty() }
            ?: arrayOf("_id", "name", "value")
        val row = arrayOfNulls<Any>(columns.size)
        columns.forEachIndexed { index, column ->
            row[index] = when (column) {
                "_id" -> 1
                "name" -> Settings.Secure.ANDROID_ID
                "value" -> mockAndroidId
                else -> null
            }
        }
        return MatrixCursor(columns).apply {
            addRow(row)
        }
    }

}
