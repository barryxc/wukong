package io.github.barryxc.wukong.hook.core

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.hook.utils.Logger
import java.lang.reflect.Field
import java.lang.reflect.Method

object HookSystemPMS : Hook {

    // 目标包名（要替换的包名）
    override fun hookScope(): List<String>? {
        return listOf("")
    }

    @SuppressLint("PrivateApi")
    override fun doHook(
        application: Application, loadPackageParam: XC_LoadPackage.LoadPackageParam
    ) {
        // ========== 额外：Hook pms getInstalledPackages ==========
        // 1. 获取 ActivityThread 的 currentActivityThread 实例
        val activityThread = loadPackageParam.classLoader.loadClass("android.app.ActivityThread")
            .getMethod("currentActivityThread").invoke(null)

        // 2. 通过 ActivityThread 获取 PackageManager（适配新版本）
        val pmsInstance =
            activityThread.javaClass.getMethod("getPackageManager").invoke(activityThread)
        if (pmsInstance == null) {
            Logger.e("pmsInstance is null")
            return
        }
        val realPmsClassName = pmsInstance::class.java.name // 打印此值，替换原类名
        Logger.d("system_server process pms class: $realPmsClassName")
        val pmsClass = try {
            loadPackageParam.classLoader.loadClass(realPmsClassName)
        } catch (e: ClassNotFoundException) {
            Logger.e(e)
            return
        }
        val methods = pmsClass.declaredMethods.filter {
            (it.name.equals("getInstalledPackages") || it.name.equals("getInstalledApplications"))
        }
        if (methods.isEmpty()) {
            Logger.e("No suitable methods found in PackageManager")
            return
        }
        methods.forEach { method ->
            XposedHelpers.findAndHookMethod(
                pmsClass, method.name, *method.parameterTypes, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        LockHolder.protect {
                            val originalResult = param.result
                            var parceledListSliceClass: Class<*>? = null
                            val originalPackages: MutableList<*>? = try {
                                // 1. 反射获取 ParceledListSlice 类（避免IDE识别问题）
                                parceledListSliceClass =
                                    Class.forName("android.content.pm.ParceledListSlice")
                                if (parceledListSliceClass.isInstance(originalResult)) {
                                    // 2. 反射获取内部 mList 字段
                                    val getListMethod = getMethodIncludingSuper(
                                        parceledListSliceClass, "getList"
                                    )
                                    getListMethod?.isAccessible = true // 即使是public方法，部分场景仍需显式开启访问
                                    getListMethod?.invoke(originalResult) as? MutableList<*>
                                } else {
                                    // 低版本直接强转
                                    originalResult as? MutableList<*>?
                                }
                            } catch (e: Exception) {
                                Logger.e(e)
                                originalResult as MutableList<*>?
                            }
                            if (originalPackages == null || originalPackages.isEmpty()) {
                                Logger.logHookMethod(param, "original packages is empty")
                                return@protect
                            }
                            //system 进程启动时，module进程还没有启动，这里无法获取到 shared Service
                            val mockPackageName = "hook.bb.com"
                            // 2. 遍历列表，替换目标包名
                            for (pkgInfo in originalPackages) {
                                var originalPackageName: String? = null
                                when (pkgInfo) {
                                    is PackageInfo -> {
                                        originalPackageName = pkgInfo.packageName
                                    }

                                    is ApplicationInfo -> {
                                        originalPackageName = pkgInfo.packageName
                                    }
                                }
                                Logger.logHookMethod(param, "pkgInfo $pkgInfo")
                                if (TARGET_PACKAGE_NAME == originalPackageName) {
                                    // 替换包名为自定义包名（直接修改 PackageInfo 的 packageName 字段）
                                    when (pkgInfo) {
                                        is PackageInfo -> {
                                            pkgInfo.packageName = mockPackageName
                                        }

                                        is ApplicationInfo -> {
                                            pkgInfo.packageName = mockPackageName
                                        }
                                    }
                                    // 可选：同时修改 applicationInfo 中的包名（避免部分场景露馅）
                                    if (pkgInfo is PackageInfo && pkgInfo.applicationInfo != null) {
                                        pkgInfo.applicationInfo!!.packageName = mockPackageName
                                    }
                                }
                            }

                            // 3.更新
                            val newResult =
                                buildByReflection(parceledListSliceClass, originalPackages)

                            if (newResult != null) {
                                param.setResult(newResult)
                                Logger.logHookMethod(param, "update result success")
                            }
                        }
                    }
                });
        }
    }


    fun getMethodIncludingSuper(clazz: Class<*>, methodName: String): Method? {
        var currentClass: Class<*>? = clazz
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredMethod(methodName)
            } catch (e: NoSuchMethodException) {
                currentClass = currentClass.superclass // 向上找父类
            }
        }
        throw NoSuchMethodException("Method $methodName not found in class $clazz")
    }

    fun getFieldIncludingSuper(clazz: Class<*>, fieldName: String): Field? {
        var currentClass: Class<*>? = clazz
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(fieldName)
            } catch (e: NoSuchFieldException) {
                currentClass = currentClass.superclass // 向上找父类
            }
        }
        throw NoSuchFieldException("Field $fieldName not found in class $clazz")
    }

    fun buildByReflection(
        parceledListSliceClass: Class<*>?, modifiedList: List<*>?
    ): Any? {
        return try {
            // 1. 反射创建空实例（依赖空构造存在）
            val constructor = parceledListSliceClass?.getDeclaredConstructor(List::class.java);
            constructor?.isAccessible = true
            constructor?.newInstance(modifiedList)
        } catch (e: Exception) {
            null // 构造/方法不存在时返回null
        }
    }
}