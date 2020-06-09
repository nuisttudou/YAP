package com.revosleap.yap.playback

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer // 类可用于控制音频/视频文件和流的播放
import android.os.PowerManager

import com.revosleap.yap.ui.activities.MainActivity
import com.revosleap.yap.models.Song
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 播放器(code 长)
 */
class MediaPlayerHolder(private val mMusicService: MusicService?) :
        PlayerAdapter, MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener {
    private val mContext: Context= mMusicService!!.applicationContext
    private val mAudioManager: AudioManager=mContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mMediaPlayer: MediaPlayer? = null
    private var mPlaybackInfoListener: PlaybackInfoListener? = null
    private var mExecutor: ScheduledExecutorService? = null
    private var mSeekBarPositionUpdateTask: Runnable? = null
    private var mSelectedSong: Song? = null
    private var mSongs: List<Song>? = null
    private var sReplaySong = false
    @PlaybackInfoListener.State
    private var mState: Int = 0
    private var mNotificationActionsReceiver: NotificationReceiver? = null
    private var mMusicNotificationManager: MusicNotificationManager? = null
    private var mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
    private var mPlayOnFocusGain: Boolean = false

    //结合companion object配置参数 处理丢失焦点问题
    private val mOnAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> mCurrentAudioFocusState = AUDIO_FOCUSED
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                // Audio focus was lost, but it's possible to duck (i.e.: play quietly)失去了音频焦点，但有可能会避开
                //用于指示音频焦点的暂时丢失，其中音频焦点的失败者如果想要继续播放，也可以降低其输出音量（也称为“引诱”），因为新的焦点所有者不需要其他人保持沉默
                mCurrentAudioFocusState = AUDIO_NO_FOCUS_CAN_DUCK
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {//用于指示音频焦点的暂时丢失  短暂的
                // Lost audio focus, but will gain it back (shortly), so note whether
                // playback should resume 失去音频焦点，但会很快恢复原状，因此请注意是否 应该恢复播放
                mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
                mPlayOnFocusGain = isMediaPlayer() && mState == PlaybackInfoListener.State.PLAYING || mState == PlaybackInfoListener.State.RESUMED
            }
            AudioManager.AUDIOFOCUS_LOSS ->//永久失去焦点
                // Lost audio focus, probably "permanently" 失去音频焦点，可能“永久”
                mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
        }

        if (mMediaPlayer != null) {
            // Update the player state based on the change
            // 根据状态更新音量
            configurePlayerState()
        }
    }

//    init {
//        mContext = mMusicService!!.applicationContext
//        mAudioManager = mContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
//    }

    private fun registerActionsReceiver() {
        mNotificationActionsReceiver = NotificationReceiver()
        val intentFilter = IntentFilter()

        intentFilter.addAction(MusicNotificationManager.PREV_ACTION)
        intentFilter.addAction(MusicNotificationManager.PLAY_PAUSE_ACTION)
        intentFilter.addAction(MusicNotificationManager.NEXT_ACTION)
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        intentFilter.addAction(Intent.ACTION_HEADSET_PLUG)
        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

        mMusicService!!.registerReceiver(mNotificationActionsReceiver, intentFilter)
    }

    private fun unregisterActionsReceiver() {
        if (mMusicService != null && mNotificationActionsReceiver != null) {
            try {
                mMusicService.unregisterReceiver(mNotificationActionsReceiver)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }

        }
    }

    override fun registerNotificationActionsReceiver(isReceiver: Boolean) {

        if (isReceiver) {
            registerActionsReceiver()
        } else {
            unregisterActionsReceiver()
        }
    }

    override fun getCurrentSong(): Song? {
        return mSelectedSong
    }


    override fun setCurrentSong(song: Song, songs: List<Song>) {
        mSelectedSong = song
        mSongs = songs
    }

    override fun onCompletion(mediaPlayer: MediaPlayer) {
        if (mPlaybackInfoListener != null) {
            mPlaybackInfoListener!!.onStateChanged(PlaybackInfoListener.State.COMPLETED)
            mPlaybackInfoListener!!.onPlaybackCompleted()
        }

        if (sReplaySong) {
            if (isMediaPlayer()) {
                resetSong()
            }
            sReplaySong = false
        } else {
            skip(true)
        }
    }

    override fun onResumeActivity() {
        startUpdatingCallbackWithPosition()
    }

    override fun onPauseActivity() {
        stopUpdatingCallbackWithPosition()
    }

    private fun tryToGetAudioFocus() {

        val result = mAudioManager.requestAudioFocus(
                mOnAudioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mCurrentAudioFocusState = AUDIO_FOCUSED
        } else {
            mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
        }
    }

    private fun giveUpAudioFocus() {
        if (mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
        }
    }

    override fun setPlaybackInfoListener(listener: PlaybackInfoListener) {
        mPlaybackInfoListener = listener
    }

    private fun setStatus(@PlaybackInfoListener.State state: Int) {

        mState = state
        if (mPlaybackInfoListener != null) {
            mPlaybackInfoListener!!.onStateChanged(state)
        }
    }

    private fun resumeMediaPlayer() {
        if (!isPlaying()) {
            mMediaPlayer!!.start()
            setStatus(PlaybackInfoListener.State.RESUMED)
            mMusicService!!.startForeground(MusicNotificationManager.NOTIFICATION_ID, mMusicNotificationManager!!.createNotification())
        }
    }

    private fun pauseMediaPlayer() {
        setStatus(PlaybackInfoListener.State.PAUSED)
        mMediaPlayer!!.pause()
        mMusicService!!.stopForeground(false)
        mMusicNotificationManager!!.notificationManager.notify(MusicNotificationManager.NOTIFICATION_ID, mMusicNotificationManager!!.createNotification())
    }

    private fun resetSong() {
        mMediaPlayer!!.seekTo(0)
        mMediaPlayer!!.start()
        setStatus(PlaybackInfoListener.State.PLAYING)
    }

    /**
     * Syncs the mMediaPlayer position with mPlaybackProgressCallback via recurring task.
     */
    private fun startUpdatingCallbackWithPosition() {
        if (mExecutor == null) {
            mExecutor = Executors.newSingleThreadScheduledExecutor()
        }
        if (mSeekBarPositionUpdateTask == null) {
            mSeekBarPositionUpdateTask = Runnable { updateProgressCallbackTask() }
        }

        mExecutor!!.scheduleAtFixedRate(
                mSeekBarPositionUpdateTask,
                0,
                1000,
                TimeUnit.MILLISECONDS
        )
    }

    // Reports media playback position to mPlaybackProgressCallback.
    private fun stopUpdatingCallbackWithPosition() {
        if (mExecutor != null) {
            mExecutor!!.shutdownNow()
            mExecutor = null
            mSeekBarPositionUpdateTask = null
        }
    }

    private fun updateProgressCallbackTask() {
        if (isMediaPlayer() && mMediaPlayer!!.isPlaying) {
            val currentPosition = mMediaPlayer!!.currentPosition
            if (mPlaybackInfoListener != null) {
                mPlaybackInfoListener!!.onPositionChanged(currentPosition)
            }
        }
    }

    override fun instantReset() {
        if (isMediaPlayer()) {
            if (mMediaPlayer!!.currentPosition < 5000) {
                skip(false)
            } else {
                resetSong()
            }
        }
    }

    /**
     * Once the [MediaPlayer] is released, it can't be used again, and another one has to be
     * created. In the onStop() method of the [MainActivity] the [MediaPlayer] is
     * released. Then in the onStart() of the [MainActivity] a new [MediaPlayer]
     * object has to be created. That's why this method is private, and called by load(int) and
     * not the constructor.
     * [MediaPlayer]发布后，将无法再次使用，并且必须将另一个
     *已创建。 在[MainActivity]的onStop（）方法中，[MediaPlayer]是
     *发布。 然后在[MainActivity]的onStart（）中新建一个[MediaPlayer]
     *必须创建对象。 这就是为什么此方法是私有的，并由load（int）和
     *不是构造函数。
     */
    override fun initMediaPlayer() {

        try {
            if (mMediaPlayer != null) {
                mMediaPlayer!!.reset()
            } else {
                mMediaPlayer = MediaPlayer()

                mMediaPlayer!!.setOnPreparedListener(this)
                mMediaPlayer!!.setOnCompletionListener(this)
                mMediaPlayer!!.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK)
                mMediaPlayer!!.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                mMusicNotificationManager = mMusicService!!.musicNotificationManager
            }
            tryToGetAudioFocus()
            mMediaPlayer!!.setDataSource(mSelectedSong!!.path)
            mMediaPlayer!!.prepare()
        } catch (e: Exception) {
            e.printStackTrace()
            skip(true)
        }

    }


    override fun getMediaPlayer(): MediaPlayer? {
        return mMediaPlayer
    }

    override fun onPrepared(mediaPlayer: MediaPlayer) {

        startUpdatingCallbackWithPosition()
        setStatus(PlaybackInfoListener.State.PLAYING)
    }


    override fun release() {
        if (isMediaPlayer()) {
            mMediaPlayer!!.release()
            mMediaPlayer = null
            giveUpAudioFocus()
            unregisterActionsReceiver()
        }
    }

    override fun isPlaying(): Boolean {
        return isMediaPlayer() && mMediaPlayer!!.isPlaying
    }

    override fun resumeOrPause() {

        if (isPlaying()) {
            pauseMediaPlayer()
        } else {
            resumeMediaPlayer()
        }
    }

    @PlaybackInfoListener.State
    override fun getState(): Int {
        return mState
    }

    override fun isMediaPlayer(): Boolean {
        return mMediaPlayer != null
    }

    override fun reset() {
        sReplaySong = !sReplaySong
    }

    override fun isReset(): Boolean {
        return sReplaySong
    }

    override fun skip(isNext: Boolean) {
        getSkipSong(isNext)
    }

    private fun getSkipSong(isNext: Boolean) {
        val currentIndex = mSongs!!.indexOf(mSelectedSong)

        val index: Int

        try {
            index = if (isNext) currentIndex + 1 else currentIndex - 1
            mSelectedSong = mSongs!![index]
        } catch (e: IndexOutOfBoundsException) {
            mSelectedSong = if (currentIndex != 0) mSongs!![0] else mSongs!![mSongs!!.size - 1]
            e.printStackTrace()
        }

        initMediaPlayer()
    }

    override fun seekTo(position: Int) {
        if (isMediaPlayer()) {
            mMediaPlayer!!.seekTo(position)
        }
    }

    override fun getPlayerPosition(): Int {
        return mMediaPlayer!!.currentPosition
    }

    /**
     * Reconfigures the player according to audio focus settings and starts/restarts it. This method
     * starts/restarts the MediaPlayer instance respecting the current audio focus state. So if we
     * have focus, it will play normally; if we don't have focus, it will either leave the player
     * paused or set it to a low volume, depending on what is permitted by the current focus
     * settings.
     *根据音频焦点设置重新配置播放器并启动/重新启动它。 这个方法
     *根据当前的音频焦点状态启动/重新启动MediaPlayer实例。 所以如果我们
     *有焦点，它将正常播放； 如果我们没有焦点，它将离开玩家
     *暂停或将其设置为低音量，具体取决于当前焦点所允许的范围设置。
     */
    private fun configurePlayerState() {

        if (mCurrentAudioFocusState == AUDIO_NO_FOCUS_NO_DUCK) {
            // We don't have audio focus and can't duck, so we have to pause
            pauseMediaPlayer()
        } else {

            if (mCurrentAudioFocusState == AUDIO_NO_FOCUS_CAN_DUCK) {
                // We're permitted to play, but only if we 'duck', ie: play softly
                mMediaPlayer!!.setVolume(VOLUME_DUCK, VOLUME_DUCK)//失去焦点音量
            } else {
                mMediaPlayer!!.setVolume(VOLUME_NORMAL, VOLUME_NORMAL)//获得焦点
            }

            // If we were playing when we lost focus, we need to resume playing.
            if (mPlayOnFocusGain) {
                resumeMediaPlayer()
                mPlayOnFocusGain = false
            }
        }
    }

    private inner class NotificationReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            // TODO Auto-generated method stub存根
            val action = intent.action

            if (action != null) {

                when (action) {
                    MusicNotificationManager.PREV_ACTION -> instantReset()
                    MusicNotificationManager.PLAY_PAUSE_ACTION -> resumeOrPause()
                    MusicNotificationManager.NEXT_ACTION -> skip(true)

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (mSelectedSong != null) {
                        pauseMediaPlayer()
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> if (mSelectedSong != null && !isPlaying()) {
                        resumeMediaPlayer()
                    }
                    Intent.ACTION_HEADSET_PLUG -> if (mSelectedSong != null) {
                        when (intent.getIntExtra("state", -1)) {
                            //0 means disconnected
                            0 -> pauseMediaPlayer()
                            //1 means connected
                            1 -> if (!isPlaying()) {
                                resumeMediaPlayer()
                            }
                        }
                    }
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> if (isPlaying()) {
                        pauseMediaPlayer()
                    }
                }
            }
        }
    }

    companion object {//配置参数

        // The volume we set the media player to when we lose audio focus, but are
        // allowed to reduce the volume instead of stopping playback.
        //当我们失去音频焦点时将媒体播放器设置为的音量
        //允许减小音量，而不是停止播放。
        private val VOLUME_DUCK = 0.2f
        // The volume we set the media player when we have audio focus.        
        // 当我们获得音频焦点时，我们设置媒体播放器的音量。
        private val VOLUME_NORMAL = 1.0f
        // we don't have audio focus, and can't duck (play at a low volume)
        // 我们没有音频焦点，也不能躲避（低音量播放）
        private val AUDIO_NO_FOCUS_NO_DUCK = 0
        // we don't have focus, but can duck (play at a low volume)
        // 我们没有重点，但可以躲避（低音量播放）
        private val AUDIO_NO_FOCUS_CAN_DUCK = 1
        // we have full audio focus
        private val AUDIO_FOCUSED = 2
    }
}
