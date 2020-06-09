package com.revosleap.yap.ui.activities

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.revosleap.yap.R
import com.revosleap.yap.models.Song
import com.revosleap.yap.playback.MusicNotificationManager
import com.revosleap.yap.playback.MusicService
import com.revosleap.yap.playback.PlaybackInfoListener
import com.revosleap.yap.playback.PlayerAdapter
import com.revosleap.yap.ui.blueprints.MainActivityBluePrint
import com.revosleap.yap.utils.EqualizerUtils
import com.revosleap.yap.utils.RecyclerAdapter
import com.revosleap.yap.utils.SongProvider
import com.revosleap.yap.utils.Utils
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.controls.*

class MainActivity : MainActivityBluePrint(), View.OnClickListener, RecyclerAdapter.SongClicked {

    private var seekBar: SeekBar? = null
    private var playPause: ImageButton? = null
    private var next: ImageButton? = null
    private var previous: ImageButton? = null
    private var songTitle: TextView? = null
    private var mMusicService: MusicService? = null
    private var mIsBound: Boolean? = null
    private var mPlayerAdapter: PlayerAdapter? = null
    private var mUserIsSeeking = false
    private var mPlaybackListener: PlaybackListener? = null
    private var deviceSongs: MutableList<Song>? = null
    private var mMusicNotificationManager: MusicNotificationManager? = null

    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {

            mMusicService = (iBinder as MusicService.LocalBinder).instance
            mPlayerAdapter = mMusicService!!.mediaPlayerHolder
            mMusicNotificationManager = mMusicService!!.musicNotificationManager

            if (mPlaybackListener == null) {
                mPlaybackListener = PlaybackListener()//实现播放信息类
                mPlayerAdapter!!.setPlaybackInfoListener(mPlaybackListener!!)
            }
            if (mPlayerAdapter != null && mPlayerAdapter!!.isPlaying()) {

                restorePlayerStatus()
            }
            checkReadStoragePermissions()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            mMusicService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        doBindService()//初始化MusicService
        setViews()//初始化控件
        initializeSeekBar()//初始化进度条
    }

    override fun onPause() {
        super.onPause()
        doUnbindService()
        if (mPlayerAdapter != null && mPlayerAdapter!!.isMediaPlayer()) {
            mPlayerAdapter!!.onPauseActivity()
        }
    }

    override fun onResume() {
        super.onResume()
        doBindService()
        if (mPlayerAdapter != null && mPlayerAdapter!!.isPlaying()) {
            restorePlayerStatus()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            finish()
        }else getMusic()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_activity_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {//设置选项元素
        when (item?.itemId) {
            R.id.action_equalizer -> {
                EqualizerUtils.openEqualizer(this, mPlayerAdapter?.getMediaPlayer())
            }//设置均衡器
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setViews() {//初始化控件
        playPause = findViewById(R.id.buttonPlayPause)
        next = findViewById(R.id.buttonNext)
        previous = findViewById(R.id.buttonPrevious)
        seekBar = findViewById(R.id.seekBar)
        songTitle = findViewById(R.id.songTitle)
        playPause!!.setOnClickListener(this)
        next!!.setOnClickListener(this)
        previous!!.setOnClickListener(this)
        deviceSongs = SongProvider.getAllDeviceSongs(this)
        setSupportActionBar(toolbar)
    }

    private fun checkReadStoragePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }else getMusic()
    }

    private fun updatePlayingInfo(restore: Boolean, startPlay: Boolean) {

        if (startPlay) {
            mPlayerAdapter!!.getMediaPlayer()?.start()
            Handler().postDelayed({
                mMusicService!!.startForeground(MusicNotificationManager.NOTIFICATION_ID,
                        mMusicNotificationManager!!.createNotification())
            }, 0) //            }, 200)
        }

        val selectedSong = mPlayerAdapter!!.getCurrentSong()

        songTitle?.text = selectedSong?.title
        val duration = selectedSong?.duration
        seekBar?.max = duration!!
        imageViewControl?.setImageBitmap(Utils.songArt(selectedSong.path!!, this@MainActivity))

        if (restore) {
            seekBar!!.progress = mPlayerAdapter!!.getPlayerPosition()
            updatePlayingStatus()//更新播放图标


            Handler().postDelayed({
                //stop foreground if coming from pause state
                if (mMusicService!!.isRestoredFromPause) {
                    mMusicService!!.stopForeground(false)
                    mMusicService!!.musicNotificationManager!!.notificationManager
                            .notify(MusicNotificationManager.NOTIFICATION_ID,
                                    mMusicService!!.musicNotificationManager!!.notificationBuilder!!.build())
                    mMusicService!!.isRestoredFromPause = false
                }
            }, 0) //            }, 200)
        }
    }

    private fun updatePlayingStatus() {//更新播放图标
        val drawable = if (mPlayerAdapter!!.getState() != PlaybackInfoListener.State.PAUSED)
            R.drawable.ic_pause
        else
            R.drawable.ic_play
        playPause!!.post { playPause!!.setImageResource(drawable) }
    }

    private fun restorePlayerStatus() {
        seekBar!!.isEnabled = mPlayerAdapter!!.isMediaPlayer()

        //if we are playing and the activity was restarted
        //update the controls panel
        //如果我们正在播放并且活动已重新开始
        //更新控制面板
        if (mPlayerAdapter != null && mPlayerAdapter!!.isMediaPlayer()) {

            mPlayerAdapter!!.onResumeActivity()
            updatePlayingInfo(true, false)
        }
    }

    private fun doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        //与服务建立连接 使用MusicService  它将在我们自己的进程中运行（因此不会支持其他应用程序替换组件）。

        bindService(Intent(this,
                MusicService::class.java), mConnection, Context.BIND_AUTO_CREATE)
        mIsBound = true

        val startNotStickyIntent = Intent(this, MusicService::class.java)
        startService(startNotStickyIntent)
    }

    private fun doUnbindService() {
        if (mIsBound!!) {
            // Detach our existing connection.
            unbindService(mConnection)
            mIsBound = false
        }
    }

    private fun onSongSelected(song: Song, songs: List<Song>) {
        if (!seekBar!!.isEnabled) {
            seekBar!!.isEnabled = true
        }
        try {
            mPlayerAdapter!!.setCurrentSong(song, songs)
            mPlayerAdapter!!.initMediaPlayer()
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun skipPrev() {
        if (checkIsPlayer()) {
            mPlayerAdapter!!.instantReset()
        }
    }

    private fun resumeOrPause() {
        if (checkIsPlayer()) {
            mPlayerAdapter!!.resumeOrPause()
        } else {
            val songs = SongProvider.getAllDeviceSongs(this)
            if (songs.isNotEmpty()) {
                onSongSelected(songs[0], songs)

            }
        }
    }

    private fun skipNext() {
        if (checkIsPlayer()) {
            mPlayerAdapter!!.skip(true)
        }
    }

    private fun checkIsPlayer(): Boolean {
        return mPlayerAdapter!!.isMediaPlayer()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.buttonPlayPause -> {
                resumeOrPause()

            }
            R.id.buttonNext -> {
                skipNext()
            }
            R.id.buttonPrevious -> {
                skipPrev()
            }
        }
    }

    private fun initializeSeekBar() {//初始化进度条
        seekBar!!.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    var userSelectedPosition = 0

                    override fun onStartTrackingTouch(seekBar: SeekBar) {
                        mUserIsSeeking = true
                    }

                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {

                        if (fromUser) {
                            userSelectedPosition = progress

                        }

                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar) {

                        if (mUserIsSeeking) {

                        }
                        mUserIsSeeking = false
                        mPlayerAdapter!!.seekTo(userSelectedPosition)
                    }
                })
    }

    override fun onSongClicked(song: Song) {
        onSongSelected(song, deviceSongs!!)
    }

    internal inner class PlaybackListener : PlaybackInfoListener() {//实现播放信息类

        override fun onPositionChanged(position: Int) {//当位置改变
            if (!mUserIsSeeking) {
                seekBar!!.progress = position
            }
        }

        override fun onStateChanged(@State state: Int) {

            updatePlayingStatus()//更新播放图标
            if (mPlayerAdapter!!.getState() != State.PAUSED
                    && mPlayerAdapter!!.getState() != State.PAUSED) {
                updatePlayingInfo(false, true)
            }
        }

        override fun onPlaybackCompleted() {
            //After playback is complete 播放完成后
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SongProvider.count=0
    }
}
