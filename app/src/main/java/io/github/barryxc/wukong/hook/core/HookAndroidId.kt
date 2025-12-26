package io.github.barryxc.wukong.hook.core

import android.app.Application
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.core.os.bundleOf
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.constant.Constant
import io.github.barryxc.wukong.hook.Bridge
import io.github.barryxc.wukong.hook.utils.Logger
import io.github.barryxc.wukong.shared.DEFAULT_ANDROID_ID

object HookAndroidId : Hook {
    override fun hookScope(): List<String>? {
        return TEST_SCOPE
    }

    override fun doHook(
        application: Application,
        loadPackageParam: XC_LoadPackage.LoadPackageParam
    ) {
        hookSettingsSecureGetString()
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
                        val uri = param.args.getOrNull(0) as? Uri
                        val callMethod = param.args.getOrNull(1) as? String
                        val callMethodArg = param.args.getOrNull(2) as? String
                        if (uri != null && callMethod.equals("GET_secure") && callMethodArg?.contains(
                                "android_id", true
                            ) == true
                        ) {
                            Bridge.getSharedService()?.let { service ->
                                service.getString(Constant.KEY_MOCK_ANDROID_ID, DEFAULT_ANDROID_ID)
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { mockAndroidId ->
                                        val originResult = param.result as? Bundle;
                                        Logger.logHookMethod(
                                            param,
                                            "set result $mockAndroidId,original result is ${
                                                originResult?.getString(
                                                    "value"
                                                )
                                            }"
                                        )
                                        param.result = bundleOf("value" to mockAndroidId)
                                    }
                            }
                        }
                    }
                })
        }
    }

    private fun hookSettingsSecureGetString() {

        // Hook多个重载的query方法
        val queryMethods = Settings.Secure::class.java.declaredMethods.filter {
            it.name == "getString" && it.parameterTypes.size >= 2
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
                        if (contentResolver != null && key?.contains(
                                Settings.Secure.ANDROID_ID, true
                            ) == true
                        ) {
                            Bridge.getSharedService()?.let { service ->
                                service.getString(Constant.KEY_MOCK_ANDROID_ID, DEFAULT_ANDROID_ID)
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { mockAndroidId ->
                                        Logger.logHookMethod(
                                            param,
                                            "set result $mockAndroidId,original result is ${param.result}"
                                        )
                                        param.result = mockAndroidId
                                    }
                            }
                        }
                    }
                })
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
                                val uri = param.args.getOrNull(0) as? android.net.Uri
                                val selection = param.args.getOrNull(2) as? String
                                val args = param.args.getOrNull(3) as? Array<String>?
                                if (uri != null && isAndroidIdQuery(uri, selection, args)) {
                                    Bridge.getSharedService()?.let { service ->
                                        service.getString(
                                            Constant.KEY_MOCK_ANDROID_ID,
                                            DEFAULT_ANDROID_ID
                                        )
                                            .takeIf { it.isNotEmpty() }
                                            ?.let { mockAndroidId ->
                                                Logger.logHookMethod(
                                                    param, "set result $mockAndroidId"
                                                )
                                                // 在这里可以替换返回的Cursor对象
                                                param.result = MockAndroidIdCursor(mockAndroidId)
                                            }
                                    }
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
        uri: android.net.Uri?, selection: String?, args: Array<String>?
    ): Boolean {
        return (uri == Settings.Secure.CONTENT_URI || uri == Settings.System.CONTENT_URI) && (selection?.contains(
            "android_id"
        ) == true || args?.contains("android_id") == true)
    }

}


class MockAndroidIdCursor(private val mockAndroidId: String) : android.database.AbstractCursor() {
    private val columnNames = arrayOf("_id", "name", "value")
    private var isAfterLast = false

    override fun getColumnNames(): Array<String> = columnNames

    override fun getCount(): Int = 1
    override fun getDouble(column: Int): Double {
        return 0.0
    }

    override fun getFloat(column: Int): Float {
        return 0.0f
    }

    override fun getString(columnIndex: Int): String? {
        return when (columnIndex) {
            1 -> "android_id" // name column
            2 -> mockAndroidId // value column
            else -> null
        }
    }

    override fun getInt(columnIndex: Int): Int = if (columnIndex == 0) 1 else 0 // _id column

    override fun getLong(columnIndex: Int): Long = getInt(columnIndex).toLong()
    override fun getShort(column: Int): Short {
        return 0
    }

    override fun getType(columnIndex: Int): Int = when (columnIndex) {
        0 -> Cursor.FIELD_TYPE_INTEGER
        1, 2 -> Cursor.FIELD_TYPE_STRING
        else -> Cursor.FIELD_TYPE_NULL
    }

    override fun isNull(columnIndex: Int): Boolean = moveToCurrentRow().not()

    override fun onMove(oldPosition: Int, newPosition: Int): Boolean {
        isAfterLast = newPosition >= count
        return !isAfterLast
    }

    private fun moveToCurrentRow(): Boolean = position >= 0 && position < count && !isAfterLast
}
