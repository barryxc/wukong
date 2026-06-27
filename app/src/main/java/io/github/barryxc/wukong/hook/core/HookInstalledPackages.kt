package io.github.barryxc.wukong.hook.core

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.hook.utils.Logger
import java.util.concurrent.atomic.AtomicBoolean

object HookInstalledPackages : ApplicationHook {
    private val installed = AtomicBoolean(false)

    override fun installWithApplication(
        application: Application,
        loadPackageParam: XC_LoadPackage.LoadPackageParam,
    ) {
        install(loadPackageParam)
    }

    fun install(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        if (!installed.compareAndSet(false, true)) {
            return
        }
        hookApplicationPackageManager(loadPackageParam.packageName)
        hookTrustDecisionPackageCollectors(loadPackageParam.classLoader)
    }

    private fun hookApplicationPackageManager(realPackageName: String) {
        val packageManagerClass = XposedHelpers.findClassIfExists(
            "android.app.ApplicationPackageManager",
            ClassLoader.getSystemClassLoader()
        ) ?: return

        QUERY_METHODS.forEach { methodName ->
            XposedBridge.hookAllMethods(
                packageManagerClass,
                methodName,
                packageManagerHook(realPackageName)
            )
        }
    }

    private fun hookTrustDecisionPackageCollectors(classLoader: ClassLoader) {
        hookStringResult(
            classLoader = classLoader,
            className = "com.trustdecision.android.core.common.Gether",
            methodName = "packageName",
        ) { original, mockPackageName ->
            val versionSuffix = original
                ?.substringAfter(PACKAGE_VERSION_SEPARATOR, missingDelimiterValue = "")
                .orEmpty()
            if (versionSuffix.isEmpty()) {
                mockPackageName
            } else {
                "$mockPackageName$PACKAGE_VERSION_SEPARATOR$versionSuffix"
            }
        }
        hookStringResult(
            classLoader = classLoader,
            className = "com.trustdecision.android.core.utils.PackageUtils",
            methodName = "getPackageName",
        ) { _, mockPackageName ->
            mockPackageName
        }
        hookStringResult(
            classLoader = classLoader,
            className = "com.trustdecision.android.core.common.PackageNameGetter",
            methodName = "get",
        ) { original, mockPackageName ->
            val versionSuffix = original
                ?.substringAfter(PACKAGE_VERSION_SEPARATOR, missingDelimiterValue = "")
                .orEmpty()
            if (versionSuffix.isEmpty()) {
                mockPackageName
            } else {
                "$mockPackageName$PACKAGE_VERSION_SEPARATOR$versionSuffix"
            }
        }
        hookTrustDecisionLogMetadata(classLoader)
    }

    private fun hookStringResult(
        classLoader: ClassLoader,
        className: String,
        methodName: String,
        transform: (original: String?, mockPackageName: String) -> String
    ) {
        val targetClass = XposedHelpers.findClassIfExists(className, classLoader) ?: return
        val hookedMethods = XposedBridge.hookAllMethods(
            targetClass,
            methodName,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    LockHolder.protect {
                        val mockPackageName = HookConfig.packageName()
                        if (mockPackageName.isBlank()) {
                            return@protect
                        }
                        val original = param.result as? String
                        param.result = transform(original, mockPackageName)
                        Logger.logHookMethod(
                            param,
                            "replace package collector result with $mockPackageName"
                        )
                    }
                }
            }
        )
        if (hookedMethods.isNotEmpty()) {
            Logger.i("[Hook#Package] installed $className.$methodName")
        }
    }

    private fun hookTrustDecisionLogMetadata(classLoader: ClassLoader) {
        val targetClass = XposedHelpers.findClassIfExists(
            "com.trustdecision.android.log.g99q9ggq99qgq9gq9q_LG",
            classLoader
        ) ?: return
        val hookedMethods = XposedBridge.hookAllMethods(
            targetClass,
            "n0",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    LockHolder.protect {
                        val mockPackageName = HookConfig.packageName()
                        if (mockPackageName.isBlank() || param.args.size <= LOG_PACKAGE_ARGUMENT) {
                            return@protect
                        }
                        param.args[LOG_PACKAGE_ARGUMENT] = mockPackageName
                        Logger.logHookMethod(
                            param,
                            "replace log metadata package with $mockPackageName"
                        )
                    }
                }
            }
        )
        if (hookedMethods.isNotEmpty()) {
            Logger.i("[Hook#Package] installed TrustDecision log metadata hook")
        }
    }

    private fun packageManagerHook(realPackageName: String) =
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                LockHolder.protect {
                    val mockPackageName = HookConfig.packageName()
                    if (mockPackageName.isBlank()) {
                        return@protect
                    }
                    replaceMockPackageArgument(
                        param = param,
                        mockPackageName = mockPackageName,
                        realPackageName = realPackageName
                    )
                }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                LockHolder.protect {
                    val mockPackageName = HookConfig.packageName()
                    if (mockPackageName.isBlank()) {
                        return@protect
                    }
                    replaceResult(
                        result = param.result,
                        realPackageName = realPackageName,
                        mockPackageName = mockPackageName
                    )
                }
            }
        }

    private fun replaceMockPackageArgument(
        param: XC_MethodHook.MethodHookParam,
        mockPackageName: String,
        realPackageName: String
    ) {
        val firstArgument = param.args.firstOrNull()
        if (firstArgument is String && firstArgument == mockPackageName) {
            param.args[0] = realPackageName
            Logger.logHookMethod(
                param,
                "map package query $mockPackageName to real package $realPackageName"
            )
        }
    }

    private fun replaceResult(
        result: Any?,
        realPackageName: String,
        mockPackageName: String
    ) {
        when (result) {
            is PackageInfo ->
                replacePackageInfo(result, realPackageName, mockPackageName)

            is ApplicationInfo ->
                replaceApplicationInfo(result, realPackageName, mockPackageName)

            is Array<*> ->
                replacePackageNameArray(result, realPackageName, mockPackageName)

            is List<*> ->
                result.forEach { replaceResult(it, realPackageName, mockPackageName) }

            else ->
                replaceParceledList(result, realPackageName, mockPackageName)
        }
    }

    private fun replaceParceledList(
        result: Any?,
        realPackageName: String,
        mockPackageName: String
    ) {
        if (result == null) {
            return
        }
        runCatching {
            val getList = result.javaClass.methods.firstOrNull {
                it.name == "getList" && it.parameterTypes.isEmpty()
            } ?: return
            (getList.invoke(result) as? List<*>)?.forEach {
                replaceResult(it, realPackageName, mockPackageName)
            }
        }
    }

    private fun replacePackageNameArray(
        result: Array<*>,
        realPackageName: String,
        mockPackageName: String
    ) {
        @Suppress("UNCHECKED_CAST")
        val packageNames = result as? Array<String> ?: return
        packageNames.indices.forEach { index ->
            if (packageNames[index] == realPackageName) {
                packageNames[index] = mockPackageName
            }
        }
    }

    private fun replacePackageInfo(
        info: PackageInfo,
        realPackageName: String,
        mockPackageName: String
    ) {
        if (info.packageName != realPackageName) {
            return
        }
        info.packageName = mockPackageName
        info.applicationInfo?.let {
            replaceApplicationInfo(it, realPackageName, mockPackageName)
        }
    }

    private fun replaceApplicationInfo(
        info: ApplicationInfo,
        realPackageName: String,
        mockPackageName: String
    ) {
        if (info.packageName == realPackageName) {
            info.packageName = mockPackageName
        }
    }

    private val QUERY_METHODS = listOf(
        "getPackageInfo",
        "getPackageInfoAsUser",
        "getApplicationInfo",
        "getApplicationInfoAsUser",
        "getInstalledPackages",
        "getInstalledApplications",
        "getPackageUid",
        "getPackageGids",
        "getPackagesForUid",
        "getInstallerPackageName",
        "getInstallSourceInfo",
    )

    private const val PACKAGE_VERSION_SEPARATOR = "*&"
    private const val LOG_PACKAGE_ARGUMENT = 6
}
