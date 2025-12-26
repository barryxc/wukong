package io.github.barryxc.wukong.hook.core

import io.github.barryxc.wukong.BuildConfig

val registry = arrayOf(
    HookAndroidId,
    HookLocation,
    HookInstalledPackages,
    HookSystemPMS,
)

const val TARGET_PACKAGE_NAME = BuildConfig.TEST_SCOPE

val TEST_SCOPE = listOf<String>(
    TARGET_PACKAGE_NAME
)