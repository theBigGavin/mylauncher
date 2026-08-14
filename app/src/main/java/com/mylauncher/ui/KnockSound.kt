package com.mylauncher.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.mylauncher.R

/**
 * 木鱼敲击音效:SoundPool 播放 res/raw/knock.mp3。
 * 初始化一次常驻,播放零延迟;加载完成前请求的敲击会在加载完成后补敲,不丢。
 */
object KnockSound {

    private var soundPool: SoundPool? = null
    private var soundId = 0
    private var loaded = false
    private var pendingPlay = false

    /** 应用启动时初始化一次(主线程调用,异步加载)。 */
    fun init(context: Context) {
        if (soundPool != null) return
        val pool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        soundPool = pool
        soundId = pool.load(context, R.raw.knock, 1)
        pool.setOnLoadCompleteListener { _, _, status ->
            loaded = status == 0
            if (loaded && pendingPlay) {
                pendingPlay = false
                pool.play(soundId, 1f, 1f, 1, 0, 1f)
            }
        }
    }

    /** 敲一次木鱼(非阻塞;未加载完时挂起,加载完成后补敲)。 */
    fun play() {
        val pool = soundPool ?: return
        if (loaded) {
            pool.play(soundId, 1f, 1f, 1, 0, 1f)
        } else {
            pendingPlay = true
        }
    }
}
