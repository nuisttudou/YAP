package com.revosleap.yap.utils

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import com.revosleap.yap.R
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.*
import java.util.concurrent.TimeUnit

/**明白
 * 播放歌曲相关的设置图片和删除歌曲
 */
object Utils {//单例

    fun songArt(path: String, context: Context): Bitmap {//设置播放时歌曲图片
        val retriever = MediaMetadataRetriever()
        val inputStream: InputStream
        retriever.setDataSource(path)
        if (retriever.embeddedPicture != null) {//设置歌曲播放时图标
            inputStream = ByteArrayInputStream(retriever.embeddedPicture)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            retriever.release()
            return bitmap
        } else {
            return getLargeIcon(context)//设置音频图片为耳机图标
        }
    }

    private fun getLargeIcon(context: Context): Bitmap {//设置音频图片为耳机图标
        return BitmapFactory.decodeResource(context.resources, R.drawable.headphones)
    }

//    fun formatDuration(duration: Int): String {
//        return String.format(Locale.getDefault(), "%02d:%02d",
//                TimeUnit.MILLISECONDS.toMinutes(duration.toLong()),
//                TimeUnit.MILLISECONDS.toSeconds(duration.toLong()) -
//                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration.toLong())))
//    }
//
//    fun formatTrack(trackNumber: Int): Int {
//        var formatted = trackNumber
//        if (trackNumber >= 1000) {
//            formatted = trackNumber % 1000
//        }
//        return formatted
//    }

    fun delete(activity: AppCompatActivity, imageFile: File){//删除
        val handler = Handler()
        handler.postDelayed({
            // Set up the projection (we only need the ID)
            val projection = arrayOf(MediaStore.Audio.Media._ID)

            // Match on the file path
            val selection = MediaStore.Audio.Media.DATA + " = ?"
            val selectionArgs = arrayOf<String>(imageFile.absolutePath)

            // Query for the ID of the media matching the file path
            val queryUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val contentResolver = activity.contentResolver
            val c = contentResolver.query(queryUri, projection, selection, selectionArgs, null)
            if (c!!.moveToFirst()) {
                // We found the ID. Deleting the item via the content provider will also remove the file
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val deleteUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                contentResolver.delete(deleteUri, null, null)

            } else {
                Log.w("Media ", "Media not found!!")

            }
            c.close()
        }, 70)
    }
}
