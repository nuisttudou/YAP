package com.revosleap.yap.playback

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

/**
 * Music服务
 */
class MusicService : Service() {
    private val mIBinder = LocalBinder()

    var mediaPlayerHolder: MediaPlayerHolder? = null
        private set//私有设置器

    var musicNotificationManager: MusicNotificationManager? = null
        private set//私有设置器

    var isRestoredFromPause = false//是否是从暂停处开始

    /**
     * onStartCommand返回的常量：如果服务被启动（从onStartCommand返回后）后，服务所在进程被杀死，服务会被重启，
     * 并将上次的Intent通过onStartCommand重新传递。这个Intent将会一直保持用来传递，
     * 直至服务使用提供给onStartCommand的ID调用stopSelf()。
     * 因为如果不是尚有未处理完的Intent（在重启时，所有这些Intent都会被发送），
     * 服务不会重启，因此服务不会收到带有空Intent的onStartCommand调用。
     */
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {//开始
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        mediaPlayerHolder!!.registerNotificationActionsReceiver(false)
        musicNotificationManager = null
        mediaPlayerHolder!!.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        if (mediaPlayerHolder == null) {
            mediaPlayerHolder = MediaPlayerHolder(this)
            musicNotificationManager = MusicNotificationManager(this)
            mediaPlayerHolder!!.registerNotificationActionsReceiver(true)
        }
        return mIBinder
    }

    inner class LocalBinder : Binder() {
        val instance: MusicService
            get() = this@MusicService
    }
}
