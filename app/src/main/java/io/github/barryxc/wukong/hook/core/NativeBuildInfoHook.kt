package io.github.barryxc.wukong.hook.core

import android.os.Build
import io.github.barryxc.wukong.hook.Bridge
import io.github.barryxc.wukong.hook.utils.Logger
import java.io.File
import java.util.zip.ZipFile

object NativeBuildInfoHook {
    @Volatile
    private var loaded = false

    fun install(properties: Map<String, String>) {
        if (!ensureLoaded()) {
            return
        }
        val entries = properties
            .filter { it.key.isNotEmpty() && it.value.isNotEmpty() }
            .toSortedMap()
        if (entries.isEmpty()) {
            return
        }
        runCatching {
            nativeInstall(entries.keys.toTypedArray(), entries.values.toTypedArray())
        }.onFailure {
            Logger.e("[NativeBuildInfo] install failed: ${it.message}")
        }
    }

    private fun ensureLoaded(): Boolean {
        if (loaded) {
            return true
        }
        synchronized(this) {
            if (loaded) {
                return true
            }
            return runCatching {
                loadLibrary()
                loaded = true
                Logger.i("[NativeBuildInfo] library loaded")
                true
            }.getOrElse {
                Logger.e("[NativeBuildInfo] load failed: ${it.message}")
                false
            }
        }
    }

    private fun loadLibrary() {
        runCatching {
            System.loadLibrary(LIBRARY_NAME)
            return
        }.onFailure {
            Logger.e("[NativeBuildInfo] loadLibrary failed: ${it.message}")
        }

        loadFromNativeLibraryDir()?.let {
            System.load(it.absolutePath)
            return
        }

        extractFromModuleApk()?.let {
            System.load(it.absolutePath)
            return
        }

        error("unable to locate $LIB_FILE_NAME")
    }

    private fun loadFromNativeLibraryDir(): File? {
        val nativeLibraryDir = Bridge.moduleNativeLibraryDir()?.takeIf { it.isNotEmpty() }
            ?: return null
        val file = File(nativeLibraryDir, LIB_FILE_NAME)
        return file.takeIf { it.isFile }
    }

    private fun extractFromModuleApk(): File? {
        val sourceDir = Bridge.moduleSourceDir()?.takeIf { it.isNotEmpty() } ?: return null
        val outputRoot = Bridge.targetCodeCacheDir()
            ?.takeIf { it.isNotEmpty() }
            ?.let { File(it, "wukong-native") }
            ?: return null
        ZipFile(sourceDir).use { zip ->
            for (abi in Build.SUPPORTED_ABIS) {
                val entry = zip.getEntry("lib/$abi/$LIB_FILE_NAME") ?: continue
                val output = File(outputRoot, "$abi/$LIB_FILE_NAME")
                output.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    output.outputStream().use { outputStream ->
                        input.copyTo(outputStream)
                    }
                }
                output.setReadable(true, false)
                output.setExecutable(true, false)
                Logger.i("[NativeBuildInfo] extracted ${entry.name} to ${output.absolutePath}")
                return output
            }
        }
        return null
    }

    private external fun nativeInstall(keys: Array<String>, values: Array<String>)

    private const val LIBRARY_NAME = "wukong_native"
    private const val LIB_FILE_NAME = "libwukong_native.so"
}
