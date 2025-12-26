package io.github.barryxc.wukong.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.github.barryxc.wukong.hook.utils.Logger

class SharedService : Service() {
    private val serviceImpl = SharedServiceImpl()

    override fun onBind(intent: Intent): IBinder {
        Logger.d("SharedService onBind")
        return serviceImpl
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.d("SharedService onStartCommand")
        return super.onStartCommand(intent, flags, startId)
    }
}