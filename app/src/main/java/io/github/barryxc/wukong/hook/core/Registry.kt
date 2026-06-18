package io.github.barryxc.wukong.hook.core

import de.robv.android.xposed.callbacks.XC_LoadPackage

val earlyInstallers = arrayOf<(XC_LoadPackage.LoadPackageParam) -> Unit>(
    { HookDetectionProbe.install() },
    { HookBuildInfo.install() },
    { HookAndroidId.install() },
    { HookLocation.install() },
    { loadPackageParam -> HookInstalledPackages.install(loadPackageParam) },
    { loadPackageParam -> HookNetworkProxy.install(loadPackageParam) },
)

val applicationRegistry: Array<Hook> = arrayOf(
    HookDetectionProbe,
    HookBuildInfo,
    HookSystemPMS,
)
