package io.github.barryxc.wukong.hook.core

import de.robv.android.xposed.callbacks.XC_LoadPackage

object HookTarget {
    @Volatile
    private var currentPackageName = ""

    fun set(packageName: String) {
        currentPackageName = packageName
    }

    fun packageName(): String = currentPackageName
}

val earlyInstallers = arrayOf<(XC_LoadPackage.LoadPackageParam) -> Unit>(
    { HookDetectionProbe.install() },
    { HookBuildInfo.install() },
    { HookAndroidId.install() },
    { HookLocation.install() },
    { HookInstalledPackages.install() },
    { loadPackageParam -> HookNetworkProxy.install(loadPackageParam) },
)

val applicationRegistry: Array<Hook> = arrayOf(
    HookDetectionProbe,
    HookBuildInfo,
    HookSystemPMS,
)

val TARGET_PACKAGE_NAMES: List<String>
    get() = listOfNotNull(HookTarget.packageName().takeIf { it.isNotBlank() })

val PRIMARY_TARGET_PACKAGE_NAME: String
    get() = HookTarget.packageName()

fun isTargetPackage(packageName: String?): Boolean {
    return !packageName.isNullOrBlank() && packageName in TARGET_PACKAGE_NAMES
}
