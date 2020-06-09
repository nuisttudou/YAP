package com.revosleap.yap.playback

import androidx.annotation.IntDef

/**
 * 播放信息,明白
 */
abstract class PlaybackInfoListener {
    open fun onPositionChanged(position: Int) {}//位置改变

    open fun onStateChanged(@State state: Int) {}//状态改变

    open fun onPlaybackCompleted() {}//播放完成

    @IntDef(State.INVALID, State.PLAYING, State.PAUSED, State.COMPLETED, State.RESUMED)
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)//注解
    annotation class State {
        companion object {

            const val INVALID = -1 //无效
            const val PLAYING = 0 //正在播放
            const val PAUSED = 1 //暂停
            const val COMPLETED = 2 //播放结束
            const val RESUMED = 3 //重新播放
        }
    }
}
