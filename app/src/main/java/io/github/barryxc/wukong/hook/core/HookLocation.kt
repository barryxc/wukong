package io.github.barryxc.wukong.hook.core

import android.app.Application
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.hook.utils.Logger
import java.util.concurrent.Executor
import java.util.function.Consumer

object HookLocation : ApplicationHook {
    @Volatile
    private var installed = false

    override fun installWithApplication(
        application: Application, loadPackageParam: XC_LoadPackage.LoadPackageParam
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
            hookLastKnownLocation()
            hookRequestLocationUpdates()
            hookCurrentLocation()
            installed = true
        }
    }

    private fun hookLastKnownLocation() {
        XposedHelpers.findAndHookMethod(
            LocationManager::class.java,
            "getLastKnownLocation",
            String::class.java,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val provider = param.args.getOrNull(0) as? String
                    val mockResult = buildMockLocation(provider) ?: return
                    param.result = mockResult
                    Logger.logHookMethod(param, "set mock location")
                }
            })
    }

    private fun hookRequestLocationUpdates() {
        LocationManager::class.java.declaredMethods
            .filter { it.name == "requestLocationUpdates" }
            .forEach { method ->
                runCatching {
                    XposedHelpers.findAndHookMethod(
                        LocationManager::class.java,
                        method.name,
                        *method.parameterTypes,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val provider = param.args.firstOrNull { it is String } as? String
                                val listener = param.args.firstOrNull { it is LocationListener } as? LocationListener
                                val mockLocation = buildMockLocation(provider) ?: return
                                listener?.onLocationChanged(mockLocation)
                                if (listener != null) {
                                    Logger.logHookMethod(param, "dispatch mock location")
                                }
                            }
                        }
                    )
                }.onFailure {
                    Logger.e("[Location] hook ${method.name} failed: ${it.message}")
                }
            }
    }

    private fun hookCurrentLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }
        LocationManager::class.java.declaredMethods
            .filter { it.name == "getCurrentLocation" }
            .forEach { method ->
                runCatching {
                    XposedHelpers.findAndHookMethod(
                        LocationManager::class.java,
                        method.name,
                        *method.parameterTypes,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                @Suppress("UNCHECKED_CAST")
                                val consumer = param.args.firstOrNull { it is Consumer<*> } as? Consumer<Location>
                                    ?: return
                                val provider = param.args.firstOrNull { it is String } as? String
                                val executor = param.args.firstOrNull { it is Executor } as? Executor
                                val mockLocation = buildMockLocation(provider) ?: return
                                if (executor != null) {
                                    executor.execute { consumer.accept(mockLocation) }
                                } else {
                                    consumer.accept(mockLocation)
                                }
                                param.result = null
                                Logger.logHookMethod(param, "dispatch current mock location")
                            }
                        }
                    )
                }.onFailure {
                    Logger.e("[Location] hook ${method.name} failed: ${it.message}")
                }
            }
    }

    private fun buildMockLocation(provider: String?): Location? {
        val mock = HookConfig.location() ?: return null
        return Location(provider ?: LocationManager.GPS_PROVIDER).apply {
            latitude = mock.latitude
            longitude = mock.longitude
            altitude = mock.altitude
            accuracy = mock.accuracy
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
    }
}
