package io.github.barryxc.wukong.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.barryxc.wukong.adapter.OnItemClickListener
import io.github.barryxc.wukong.adapter.SettingRecyclerAdapter
import io.github.barryxc.wukong.constant.Constant
import io.github.barryxc.wukong.databinding.ActivityMainBinding
import io.github.barryxc.wukong.hook.utils.Logger
import io.github.barryxc.wukong.model.ItemData
import io.github.barryxc.wukong.shared.DEFAULT_ANDROID_ID
import io.github.barryxc.wukong.shared.DEFAULT_HOOK_PACKAGE_NAME
import io.github.barryxc.wukong.shared.DEFAULT_LOCATION


class MainActivity : AppCompatActivity(), OnItemClickListener {
    private val mCacheData by lazy {
        getSharedPreferences("setting_cache", Context.MODE_PRIVATE)
    }
    private val mItemData = ArrayList<ItemData>()
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())
        setSupportActionBar(binding.toolbar)
        initData()
        initView()
        if (!checkPermission()) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), 1
            )
        }
        Logger.d("java tid:"+Thread.currentThread().id)
        Logger.d("Process tid:"+ Process.myTid())
    }

    fun checkPermission(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return true
    }

    fun initData() {
        mItemData.add(
            ItemData(
                Constant.KEY_SAVE,
                mCacheData.getString(Constant.Companion.KEY_MOCK_GPS_LOCATION, DEFAULT_LOCATION),
                "location",
                SettingRecyclerAdapter.Companion.TYPE_EDITTEXT
            )
        )
        mItemData.add(
            ItemData(
                Constant.KEY_SAVE,
                mCacheData.getString(Constant.Companion.KEY_MOCK_ANDROID_ID, DEFAULT_ANDROID_ID),
                "android_id",
                SettingRecyclerAdapter.Companion.TYPE_EDITTEXT
            )
        )
        mItemData.add(
            ItemData(
                Constant.KEY_SAVE, mCacheData.getString(
                    Constant.Companion.KEY_MOCK_PACKAGE_NAME, DEFAULT_HOOK_PACKAGE_NAME
                ), "package_name", SettingRecyclerAdapter.Companion.TYPE_EDITTEXT
            )
        )
    }

    @SuppressLint("HardwareIds")
    fun initView() {
        val adapter = SettingRecyclerAdapter(mItemData)
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        adapter.apply {
            setOnItemClickListener(this@MainActivity)
        }
        binding.rvSettingSwitch.apply {
            this.layoutManager = layoutManager
            this.adapter = adapter
        }
    }


    @SuppressLint("HardwareIds")
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun hookTest(v: View) {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Logger.d("androidId: $androidId")

        val bundle =
            contentResolver.call(Settings.Secure.CONTENT_URI, "GET_secure", "android_id", null)
        val androidId2 = bundle?.getCharSequence("value")
        Logger.d("androidId2: $androidId2")


        val packName = packageName
        Logger.d("packName: $packName")

        val locationService = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (checkPermission()) {
            locationService.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String?>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onItemClick(
        position: Int, isChecked: Boolean, input: String?
    ) {
        when (position) {
            0 -> {
                mCacheData.edit { putString(Constant.Companion.KEY_MOCK_GPS_LOCATION, input) }
            }

            1 -> {
                mCacheData.edit { putString(Constant.Companion.KEY_MOCK_ANDROID_ID, input) }
            }

            2 -> {
                mCacheData.edit { putString(Constant.Companion.KEY_MOCK_PACKAGE_NAME, input) }
            }
        }
        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
    }
}