package io.github.barryxc.wukong.activity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Process
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.barryxc.wukong.R
import io.github.barryxc.wukong.adapter.OnItemClickListener
import io.github.barryxc.wukong.adapter.SettingRecyclerAdapter
import io.github.barryxc.wukong.constant.Constant
import io.github.barryxc.wukong.databinding.ActivityMainBinding
import io.github.barryxc.wukong.hook.utils.Logger
import io.github.barryxc.wukong.model.ItemData
import io.github.barryxc.wukong.shared.DEFAULT_ANDROID_ID
import io.github.barryxc.wukong.shared.DEFAULT_BRAND
import io.github.barryxc.wukong.shared.DEFAULT_HOOK_PACKAGE_NAME
import io.github.barryxc.wukong.shared.DEFAULT_LOCATION
import io.github.barryxc.wukong.shared.DEFAULT_PROXY
import java.net.Inet4Address
import java.net.InetAddress
import java.security.SecureRandom


class MainActivity : AppCompatActivity(), OnItemClickListener {
    private val mCacheData by lazy {
        getSharedPreferences("setting_cache", Context.MODE_PRIVATE)
    }
    private val mItemData = ArrayList<ItemData>()
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingAdapter: SettingRecyclerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())
        setSupportActionBar(binding.toolbar)
        initData()
        initView()
        Logger.d("java tid:" + Thread.currentThread().id)
        Logger.d("Process tid:" + Process.myTid())
    }

    fun checkPermission(): Boolean {
        return hasFineLocationPermission() || hasCoarseLocationPermission()
    }

    fun initData() {
        val savedBrand =
            mCacheData.getString(Constant.Companion.KEY_MOCK_BRAND, DEFAULT_BRAND) ?: DEFAULT_BRAND
        val savedModel = mCacheData.getString(
            Constant.Companion.KEY_MOCK_MODEL,
            SettingRecyclerAdapter.defaultModelForBrand(savedBrand)
        )
        val model = savedModel?.takeIf { SettingRecyclerAdapter.isModelForBrand(savedBrand, it) }
            ?: SettingRecyclerAdapter.defaultModelForBrand(savedBrand)
        mItemData.add(
            ItemData(
                Constant.KEY_SAVE,
                mCacheData.getString(Constant.Companion.KEY_MOCK_GPS_LOCATION, DEFAULT_LOCATION),
                "Location (lat,lng,alt,accuracy)",
                SettingRecyclerAdapter.Companion.TYPE_EDITTEXT
            )
        )
        mItemData.add(
            ItemData(
                Constant.KEY_SAVE,
                mCacheData.getString(Constant.Companion.KEY_MOCK_ANDROID_ID, DEFAULT_ANDROID_ID),
                "Android ID",
                SettingRecyclerAdapter.Companion.TYPE_EDITTEXT
            )
        )
        mItemData.add(
            ItemData(
                Constant.KEY_SAVE, savedBrand, "Brand", SettingRecyclerAdapter.Companion.TYPE_SELECT
            )
        )
        mItemData.add(
            ItemData(
                Constant.KEY_SAVE, model, "Model", SettingRecyclerAdapter.Companion.TYPE_SELECT
            )
        )
        mItemData.add(
            ItemData(
                Constant.KEY_SAVE, mCacheData.getString(
                    Constant.Companion.KEY_MOCK_PACKAGE_NAME, DEFAULT_HOOK_PACKAGE_NAME
                ), "Mock package name", SettingRecyclerAdapter.Companion.TYPE_EDITTEXT
            )
        )
        mItemData.add(
            ItemData(
                Constant.KEY_SAVE,
                currentProxyDefault(),
                "HTTP proxy (host:port)",
                SettingRecyclerAdapter.Companion.TYPE_EDITTEXT
            )
        )
    }

    fun initView() {
        settingAdapter = SettingRecyclerAdapter(mItemData)
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        settingAdapter.apply {
            setOnItemClickListener(this@MainActivity)
        }
        binding.rvSettingSwitch.apply {
            this.layoutManager = layoutManager
            this.adapter = settingAdapter
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String?>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return
        }
        if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            fillCurrentLocation()
        } else {
            val canAskAgain = permissions.filterNotNull().any {
                ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }
            val message = if (canAskAgain) {
                "定位权限未授权"
            } else {
                "定位权限已被拒绝，请到系统设置中开启"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_save -> {
                saveAllSettings()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onItemClick(
        position: Int, isChecked: Boolean, input: String?,
    ) {
        when (position) {
            LOCATION_POSITION -> {
                fillCurrentLocation()
            }

            ANDROID_ID_POSITION -> {
                val androidId = generateAndroidId()
                mItemData[ANDROID_ID_POSITION].value = androidId
                settingAdapter.notifyItemChanged(ANDROID_ID_POSITION)
                Toast.makeText(this, "已生成 Android ID，点击右上角保存", Toast.LENGTH_SHORT).show()
            }

            BRAND_POSITION -> {
                val brand = input ?: valueAt(BRAND_POSITION)
                mItemData[BRAND_POSITION].value = brand
                mItemData[MODEL_POSITION].value = SettingRecyclerAdapter.defaultModelForBrand(brand)
                settingAdapter.notifyItemChanged(MODEL_POSITION)
            }

            PACKAGE_NAME_POSITION -> {
                val packageName = generatePackageName()
                mItemData[PACKAGE_NAME_POSITION].value = packageName
                settingAdapter.notifyItemChanged(PACKAGE_NAME_POSITION)
                Toast.makeText(this, "已生成包名，点击右上角保存", Toast.LENGTH_SHORT).show()
            }

            PROXY_POSITION -> {
                fillMacProxyIp()
            }
        }
    }

    private fun saveAllSettings() {
        mCacheData.edit {
            putString(Constant.Companion.KEY_MOCK_GPS_LOCATION, valueAt(LOCATION_POSITION))
            putString(Constant.Companion.KEY_MOCK_ANDROID_ID, valueAt(ANDROID_ID_POSITION))
            putString(Constant.Companion.KEY_MOCK_BRAND, valueAt(BRAND_POSITION))
            putString(Constant.Companion.KEY_MOCK_MODEL, valueAt(MODEL_POSITION))
            putString(
                Constant.Companion.KEY_MOCK_PACKAGE_NAME, valueAt(PACKAGE_NAME_POSITION)
            )
            putString(Constant.Companion.KEY_MOCK_PROXY, valueAt(PROXY_POSITION).trim())
        }
        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
    }

    private fun valueAt(position: Int): String {
        return mItemData.getOrNull(position)?.value as? String ?: ""
    }

    private fun generateAndroidId(): String {
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun generatePackageName(): String {
        return "com.${randomLowercase(6)}.${randomAlphaNumeric(8)}"
    }

    private fun randomLowercase(length: Int): String {
        return buildString(length) {
            repeat(length) {
                append(('a'.code + secureRandom.nextInt(26)).toChar())
            }
        }
    }

    private fun randomAlphaNumeric(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return buildString(length) {
            repeat(length) {
                append(chars[secureRandom.nextInt(chars.length)])
            }
        }
    }

    private fun fillCurrentLocation() {
        if (!hasFineLocationPermission()) {
            Toast.makeText(this, "正在请求定位权限", Toast.LENGTH_SHORT).show()
            requestLocationPermission()
            return
        }
        val location = currentLocation()
        if (location == null) {
            Toast.makeText(this, "未获取到定位信息", Toast.LENGTH_SHORT).show()
            return
        }
        mItemData[LOCATION_POSITION].value = formatLocation(location)
        settingAdapter.notifyItemChanged(LOCATION_POSITION)
        Toast.makeText(this, "已填入当前位置，点击右上角保存", Toast.LENGTH_SHORT).show()
    }

    private fun requestLocationPermission() {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) && hasRequestedLocationPermission()
        ) {
            Toast.makeText(this, "定位权限已被拒绝，请到系统设置中开启", Toast.LENGTH_SHORT).show()
            return
        }
        markLocationPermissionRequested()
        ActivityCompat.requestPermissions(
            this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            ), LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun hasFineLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCoarseLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasRequestedLocationPermission(): Boolean {
        return mCacheData.getBoolean(KEY_LOCATION_PERMISSION_REQUESTED, false)
    }

    private fun markLocationPermissionRequested() {
        mCacheData.edit {
            putBoolean(KEY_LOCATION_PERMISSION_REQUESTED, true)
        }
    }

    private fun currentLocation(): Location? {
        val locationManager =
            getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        return providers.mapNotNull { provider ->
            runCatching {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.getLastKnownLocation(provider)
                } else {
                    null
                }
            }.getOrNull()
        }.maxByOrNull { it.time }
    }

    private fun formatLocation(location: Location): String {
        return listOf(
            location.latitude.toString(),
            location.longitude.toString(),
            location.altitude.toString(),
            location.accuracy.toString(),
        ).joinToString(",")
    }

    private fun currentProxyDefault(): String {
        mCacheData.getString(Constant.Companion.KEY_MOCK_PROXY, null)?.let {
            return it
        }
        return DEFAULT_PROXY
    }

    @Suppress("DEPRECATION")
    private fun currentWifiGatewayIp(): String? {
        val wifiManager =
            applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val gateway = wifiManager.dhcpInfo?.gateway ?: return null
        if (gateway == 0) {
            return null
        }
        return intToIpv4(gateway)
    }

    @Suppress("DEPRECATION")
    private fun currentWifiDeviceIp(): String? {
        val wifiManager =
            applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val ipAddress = wifiManager.connectionInfo?.ipAddress ?: return null
        if (ipAddress == 0) {
            return null
        }
        return intToIpv4(ipAddress)
    }

    private fun intToIpv4(value: Int): String {
        return listOf(
            value and 0xff,
            value shr 8 and 0xff,
            value shr 16 and 0xff,
            value shr 24 and 0xff,
        ).joinToString(".")
    }

    private fun fillMacProxyIp() {
        Toast.makeText(this, "正在解析 $MAC_LOCAL_HOST", Toast.LENGTH_SHORT).show()
        Thread {
            var ip = resolveMacLocalIp()
            runOnUiThread {
                if (ip == null) {
                    Toast.makeText(
                        this,
                        "未解析到 $MAC_LOCAL_HOST，请确认手机和 Mac 在同一局域网",
                        Toast.LENGTH_SHORT
                    ).show()
                    ip = currentWifiGatewayIp()
                }
                if (ip == null) {
                    return@runOnUiThread
                }
                mItemData[PROXY_POSITION].value = "$ip:$DEFAULT_PROXY_PORT"
                settingAdapter.notifyItemChanged(PROXY_POSITION)
                Toast.makeText(this, "已填入ip地址，点击右上角保存", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun resolveMacLocalIp(): String? {
        return MAC_LOCAL_HOST_CANDIDATES.asSequence().mapNotNull { host ->
            runCatching {
                InetAddress.getAllByName(host)
                    .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
            }.getOrNull()
        }.firstOrNull()
    }

    private companion object {
        val secureRandom = SecureRandom()
        const val MAC_LOCAL_HOST = "barrydeMacBook-Pro.local"
        val MAC_LOCAL_HOST_CANDIDATES = listOf(
            MAC_LOCAL_HOST
        )
        const val DEFAULT_PROXY_PORT = 8888
        const val LOCATION_PERMISSION_REQUEST_CODE = 1
        const val LOCATION_POSITION = 0
        const val ANDROID_ID_POSITION = 1
        const val BRAND_POSITION = 2
        const val MODEL_POSITION = 3
        const val PACKAGE_NAME_POSITION = 4
        const val PROXY_POSITION = 5
        const val KEY_LOCATION_PERMISSION_REQUESTED = "location_permission_requested"
    }
}
