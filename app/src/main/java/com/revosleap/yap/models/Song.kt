package com.revosleap.yap.models
//定义音乐数据组成类
data class Song(
        val title: String,
        val trackNumber: Int,
        val year: Int,
        val duration: Int,
        val path: String?,
        val albumName: String,
        val artistId: Int,
        val artistName: String)

