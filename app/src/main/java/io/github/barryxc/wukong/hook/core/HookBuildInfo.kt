package io.github.barryxc.wukong.hook.core

import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.hook.utils.Logger
import io.github.barryxc.wukong.shared.DeviceProfiles

object HookBuildInfo : Hook {
    @Volatile
    private var installed = false

    override fun doHook(
        application: Application,
        loadPackageParam: XC_LoadPackage.LoadPackageParam
    ) {
        applyBuildInfo()
        scheduleBuildInfoRefreshes()
    }

    fun install() {
        if (installed) {
            return
        }
        synchronized(this) {
            if (installed) {
                return
            }
            applyBuildInfo()
            hookSystemProperties()
            hookBuildGetString()
            installed = true
        }
    }

    private fun applyBuildInfo() {
        if (!HookConfig.hasBuildInfo()) {
            Logger.i("[BuildInfo] skip empty config")
            return
        }
        val buildInfo = currentBuildInfo()
        NativeBuildInfoHook.install(buildPropertyMap(buildInfo))
        Logger.i("[BuildInfo] apply brand=${buildInfo.brand}, model=${buildInfo.model}")
        setBuildField("BRAND", buildInfo.brand)
        setBuildField("MANUFACTURER", buildInfo.manufacturer)
        setBuildField("MODEL", buildInfo.model)
        setBuildField("DEVICE", buildInfo.device)
        setBuildField("PRODUCT", buildInfo.product)
        setBuildField("BOARD", buildInfo.board)
        setBuildField("HARDWARE", buildInfo.hardware)
        setBuildField("DISPLAY", buildInfo.display)
        setBuildField("HOST", buildInfo.host)
        setBuildField("TAGS", buildInfo.tags)
        setBuildField("FINGERPRINT", buildInfo.fingerprint)
        setBuildField("ID", buildInfo.id)
    }

    private fun scheduleBuildInfoRefreshes() {
        val handler = Handler(Looper.getMainLooper())
        listOf(300L, 1000L, 2000L, 5000L).forEach { delay ->
            handler.postDelayed({ applyBuildInfo() }, delay)
        }
        Thread {
            listOf(300L, 1000L, 2000L, 5000L).forEach { delay ->
                runCatching {
                    Thread.sleep(delay)
                    applyBuildInfo()
                }
            }
        }.apply {
            name = "wukong-build-info-refresh"
            isDaemon = true
            start()
        }
    }

    private fun hookSystemProperties() {
        runCatching {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            systemPropertiesClass.declaredMethods
                .filter {
                    (it.name == "get" || it.name == "native_get")
                        && it.returnType == String::class.java
                        && it.parameterTypes.firstOrNull() == String::class.java
                }
                .forEach { method ->
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val key = param.args.firstOrNull() as? String ?: return
                            val value = buildPropertyValue(key) ?: return
                            param.result = value
                        }
                    })
                    Logger.i("[BuildInfo] hook SystemProperties.${method.name}")
                }
        }.onFailure {
            Logger.e("[BuildInfo] hook SystemProperties failed: ${it.message}")
        }
    }

    private fun hookBuildGetString() {
        runCatching {
            Build::class.java.declaredMethods
                .filter {
                    it.name == "getString"
                        && it.returnType == String::class.java
                        && it.parameterTypes.contentEquals(arrayOf(String::class.java))
                }
                .forEach { method ->
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val key = param.args.firstOrNull() as? String ?: return
                            val value = buildPropertyValue(key) ?: return
                            param.result = value
                        }
                    })
                    Logger.i("[BuildInfo] hook Build.getString")
                }
        }.onFailure {
            Logger.e("[BuildInfo] hook Build.getString failed: ${it.message}")
        }
    }

    private fun buildPropertyValue(key: String): String? {
        if (!HookConfig.hasBuildInfo()) {
            return null
        }
        return buildPropertyMap(currentBuildInfo())[key]
    }

    private fun buildPropertyMap(buildInfo: BuildInfoSnapshot): Map<String, String> {
        val props = mutableMapOf<String, String>()
        listOf(
            "ro.product",
            "ro.product.odm",
            "ro.product.product",
            "ro.product.system",
            "ro.product.system_ext",
            "ro.product.vendor",
        ).forEach { prefix ->
            props["$prefix.brand"] = buildInfo.brand
            props["$prefix.manufacturer"] = buildInfo.manufacturer
            props["$prefix.model"] = buildInfo.model
            props["$prefix.device"] = buildInfo.device
            props["$prefix.name"] = buildInfo.product
        }
        props["ro.build.display.id"] = buildInfo.display
        props["ro.build.fingerprint"] = buildInfo.fingerprint
        props["ro.build.host"] = buildInfo.host
        props["ro.build.id"] = buildInfo.id
        props["ro.build.product"] = buildInfo.product
        props["ro.build.tags"] = buildInfo.tags
        props["ro.build.type"] = "user"
        props["ro.build.user"] = "android-build"
        props["ro.board.platform"] = buildInfo.hardware
        props["ro.boot.hardware"] = buildInfo.hardware
        props["ro.boot.hardware.platform"] = buildInfo.hardware
        props["ro.hardware"] = buildInfo.hardware
        props["ro.product.board"] = buildInfo.board
        props["ro.product.hardware"] = buildInfo.hardware
        return props
    }

    private fun currentBuildInfo(): BuildInfoSnapshot {
        val profile = DeviceProfiles.profileFor(HookConfig.brand(), HookConfig.model())
        val brand = profile.brand
        val model = profile.model
        val manufacturer = DeviceProfiles.manufacturerForBrand(brand)
        val device = profile.device
        val product = profile.product
        val board = profile.board
        val hardware = profile.hardware
        val id = "AP1A.240505.005"
        val display = "$id.${device.take(8)}"
        val tags = "release-keys"
        return BuildInfoSnapshot(
            brand = brand,
            manufacturer = manufacturer,
            model = model,
            device = device,
            product = product,
            board = board,
            hardware = hardware,
            display = display,
            host = "android-build",
            tags = tags,
            fingerprint = "$brand/$product/$device:${Build.VERSION.RELEASE}/$id/1234567:user/$tags",
            id = id,
        )
    }

    private fun setBuildField(name: String, value: String) {
        runCatching {
            XposedHelpers.setStaticObjectField(Build::class.java, name, value)
            Logger.i("[BuildInfo] set $name")
        }.onFailure {
            Logger.e("[BuildInfo] set $name failed: ${it.message}")
        }
    }

    private data class BuildInfoSnapshot(
        val brand: String,
        val manufacturer: String,
        val model: String,
        val device: String,
        val product: String,
        val board: String,
        val hardware: String,
        val display: String,
        val host: String,
        val tags: String,
        val fingerprint: String,
        val id: String,
    )
}
