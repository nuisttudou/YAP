# 项目结构及主要内容




## Manifest
读EXTERNAL_STORAGE
写EXTERNAL_STORAGE
保持清醒
前台服务　android.permission.FOREGROUND_SERVICE
## model
### song 
歌曲信息

---
## MainActivity
ServiceConnection

---
## MusicService:Service
### MediaPlayerHolder:MediaPlayer
##### 封装音频播放状态

PlayerAdapter, 
MediaPlayer.OnCompletionListener, 
MediaPlayer.OnPreparedListener 

### NotificationManager
createNotification
createNotificationChannel
updateMetaData

PendingIntent

.addAction NotificationCompat.Action.Builder

NotifactionManager可以实现可视化的信息显示，通过它们可以显示广播信息的内容以及图标和震动等信息

---
## utils

### RecyclerAdapteer
onCreateViewHolder
RecyclerView 定义接口 onBindViewHolder
inner class ViewHolder 在MainActivity中调用

### songProvider
contentResolver
cursor 行的集合
标题、年份、时长、路径、专辑、作者

### delete
contentResolver 删除

### 设置图标
MediaMetadataRetriever

### EqualizerUtils 单例
Intent

---
### 框架:
MediaSessionCompat


### ui
SeekBar

---
