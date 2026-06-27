package io.github.barryxc.wukong.hook.core

// 统一注册表：所有 hooks 都在 Application 可用后安装。
val applicationHookRegistry: Array<ApplicationHook> = arrayOf(
    HookDetectionProbe,
    HookBuildInfo,
    HookAndroidId,
    HookLocation,
    HookInstalledPackages,
    HookNetworkProxy,
    HookSystemPMS,
)
