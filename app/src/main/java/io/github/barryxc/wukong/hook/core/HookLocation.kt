package io.github.barryxc.wukong.hook.core

import android.app.Application
import android.location.Location
import android.location.LocationManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.constant.Constant
import io.github.barryxc.wukong.hook.Bridge
import io.github.barryxc.wukong.hook.utils.Logger
import io.github.barryxc.wukong.shared.DEFAULT_LOCATION

object HookLocation : Hook {
    override fun hookScope(): List<String>? {
        return TEST_SCOPE
    }

    override fun doHook(
        application: Application, loadPackageParam: XC_LoadPackage.LoadPackageParam
    ) {
        XposedHelpers.findAndHookMethod(
            LocationManager::class.java,
            "getLastKnownLocation",
            String::class.java,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    Bridge.getSharedService()?.let {
                        val locationInfo = it.getString(
                            Constant.KEY_MOCK_GPS_LOCATION, DEFAULT_LOCATION
                        )
                        val mockResult = Location("gps")
                        val parts = locationInfo.split(",")
                        val latitude = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                        val longitude = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                        val altitude = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                        val accuracy = parts.getOrNull(3)?.toFloatOrNull() ?: 0f
                        mockResult.latitude = latitude
                        mockResult.longitude = longitude
                        mockResult.altitude = altitude
                        mockResult.accuracy = accuracy
                        param.result = mockResult
                        Logger.logHookMethod(param, "set result $mockResult")
                    }
                }
            })
    }

}