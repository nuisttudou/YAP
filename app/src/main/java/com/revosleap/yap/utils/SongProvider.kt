package com.revosleap.yap.utils

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.widget.Toast
import com.revosleap.yap.models.Song
import com.revosleap.yap.ui.activities.MainActivity
import java.util.*
/**明白
 * 查找音乐数据
 * 提供音乐数据
 */
object SongProvider {
    //单例
    private const val TITLE = 0//标题
    private const val TRACK = 1//
    private const val YEAR = 2//年份
    private const val DURATION = 3//时长
    private const val PATH = 4//路径
    private const val ALBUM = 5//专辑
    private const val ARTIST_ID = 6//作者ID
    private const val ARTIST = 7//作者
    //数组
    private val BASE_PROJECTION = arrayOf(MediaStore.Audio.AudioColumns.TITLE, // 0
            MediaStore.Audio.AudioColumns.TRACK, // 1
            MediaStore.Audio.AudioColumns.YEAR, // 2
            MediaStore.Audio.AudioColumns.DURATION, // 3
            MediaStore.Audio.AudioColumns.DATA, // 4
            MediaStore.Audio.AudioColumns.ALBUM, // 5
            MediaStore.Audio.AudioColumns.ARTIST_ID, // 6
            MediaStore.Audio.AudioColumns.ARTIST)// 7

    private val mAllDeviceSongs = ArrayList<Song>()

    var count=0
    fun getAllDeviceSongs(context: Context): MutableList<Song> {//其他类获取设备音频
//        println("-----------------")
        val cursor = makeSongCursor(context)
//        if( cursor==null) cursor = makeSongCursor(context)
        if(count++>1)return ArrayList()
        return getSongs(cursor)
    }

    private fun getSongs(cursor: Cursor?): MutableList<Song> {//遍历Cursor,将其加入songs
        val songs = ArrayList<Song>()
        if (cursor != null && cursor.moveToFirst()) {
            do {
                val song = getSongFromCursorImpl(cursor)
                if (song.duration >= 30000) {
                    songs.add(song)
                    mAllDeviceSongs.add(song)
                }
            } while (cursor.moveToNext())
        }

        cursor?.close()

        return songs
    }


    private fun getSongFromCursorImpl(cursor: Cursor): Song {//从Cursor获取音乐的接口实现
        val title = cursor.getString(TITLE)
        val trackNumber = cursor.getInt(TRACK)
        val year = cursor.getInt(YEAR)
        val duration = cursor.getInt(DURATION)
        val uri = cursor.getString(PATH)
        val albumName = cursor.getString(ALBUM)
        val artistId = cursor.getInt(ARTIST_ID)
        val artistName = cursor.getString(ARTIST)

        return Song(title, trackNumber, year, duration, uri, albumName, artistId, artistName)
    }

    private fun makeSongCursor(context: Context): Cursor? {//制作音乐的Cursorn函数
        return try {
            context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    BASE_PROJECTION, null, null, null)
        } catch (e: SecurityException) {
            null
        }
    }
}
    /*
    internal fun makeSongCursor(context: Context): Cursor? {//制作音乐的Cursorn函数
        try {
            return context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    BASE_PROJECTION, null, null, null)
        } catch (e: SecurityException) {
            return null
        }
    }*/
//}
