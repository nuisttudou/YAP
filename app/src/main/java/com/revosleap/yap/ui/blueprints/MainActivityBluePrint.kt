package com.revosleap.yap.ui.blueprints

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.revosleap.yap.R
import com.revosleap.yap.models.Song
import com.revosleap.yap.utils.RecyclerAdapter
import com.revosleap.yap.utils.SongProvider
import com.revosleap.yap.utils.Utils
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import java.io.File

/**
 * 这部分处理滚动列表
 * MainActivityBluePrint
 */
abstract class MainActivityBluePrint : AppCompatActivity(), ActionMode.Callback, RecyclerAdapter.OnLongClick,
        RecyclerAdapter.SongsSelected, RecyclerAdapter.SongClicked {
    private var actionMode: ActionMode? = null
    private var songAdapter: RecyclerAdapter? = null//
    private var deviceMusic = mutableListOf<Song>()//音乐列表

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        songAdapter = RecyclerAdapter()//
        setViews()
    }

    override fun onSongLongClicked(position: Int) {
        if (actionMode == null) {
            actionMode = startActionMode(this)
        }
    }

    override fun onSelectSongs(selectedSongs: MutableList<Song>) {
        if (selectedSongs.isEmpty()) {
            actionMode?.finish()
            songAdapter?.removeSelection()
        } else {
            val title = "Delete ${selectedSongs.size} Songs"
            actionMode?.title = title
        }
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        val inflater = mode?.menuInflater
        inflater?.inflate(R.menu.action_mode_menu, menu!!)
        toolbar.visibility= View.GONE
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return false
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.action_delete -> {
                val songs = songAdapter?.getSelectedSongs()
                songs?.forEach {
                    val file = File(it.path)
                    Utils.delete(this@MainActivityBluePrint, file)
                    songAdapter?.updateRemoved(it)
                }
                Toast.makeText(this, "Deleted ${songs?.size} Songs", Toast.LENGTH_SHORT).show()
                mode?.finish()
                songAdapter?.removeSelection()
                return true
            }

        }
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        songAdapter?.removeSelection()
        toolbar.visibility= View.VISIBLE
        actionMode = null
    }

    private fun setViews() {//设置选中音乐时的事件

        songAdapter?.setOnLongClick(this)//非空即监听
        songAdapter?.setSongsSelected(this)//非空即监听
        songAdapter?.setOnSongClicked(this)//非空即监听
        recyclerView?.apply {
            adapter = songAdapter
            layoutManager = LinearLayoutManager(this@MainActivityBluePrint)
            hasFixedSize()
        }

    }

    fun getMusic(){
        deviceMusic.addAll(SongProvider.getAllDeviceSongs(this))
        songAdapter?.addSongs(deviceMusic)
    }
}