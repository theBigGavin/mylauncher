package com.mylauncher.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.mylauncher.R

/**
 * 木鱼敲击音效:SoundPool 播放 res/raw/knock.mp3。
 * 初始化一次常驻,播放零延迟;加载完成前请求的敲击会在加载完成后补敲,不丢。
 * 每次敲击先停掉上一击再从头播放:快速连击不叠加拖尾,起音跟手(拟真木鱼连敲)。
 *
 * 防重入:两次 play() 间隔 < [KNOCK_REENTRY_MS] (80ms) 视为同一次敲击的重复触发
 * (双调/双路径),直接丢弃 —— 物理上人类连敲间隔不可能小于 80ms,
 * 真实快速连击(>80ms)不受影响,每次手势照常响。
 */
object KnockSound {

    /** 实机定位开关:改 true 重新安装后,logcat -s MyLauncher 可见双响路径的时间戳证据。 */
    const val DEBUG_KNOCK = false

    /** 物理连敲下限:同一次敲击的重复触发(双调/双路径/双上报)间隔不可能低于此值。 */
    private const val KNOCK_REENTRY_MS = 80L

    private var soundPool: SoundPool? = null
    private var soundId = 0
    private var loaded = false
    private var pendingPlay = false
    private var lastStream = 0
    private var lastPlayAtMs = 0L

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
                lastStream = pool.play(soundId, 1f, 1f, 1, 0, 1f)
            }
        }
    }

    /** 敲一次木鱼(非阻塞;未加载完时挂起,加载完成后补敲)。
     *  同一手势两条触发路径(边缘按下 + 返回完成)的去重由调用方按手势处理;
     *  这里兜底物理防重入:80ms 内的重复触发直接丢弃,保证不会"嗒嗒"两响。 */
    fun play() {
        val pool = soundPool ?: return
        val now = System.currentTimeMillis()
        if (now - lastPlayAtMs < KNOCK_REENTRY_MS) {
            if (DEBUG_KNOCK) Log.d("MyLauncher", "KnockSound[reentry-dropped]: gap=${now - lastPlayAtMs}ms")
            return
        }
        lastPlayAtMs = now
        if (loaded) {
            // 立即停止上一击并从头重放:连击时每次敲击的起音都清晰,不糊成一片
            if (lastStream != 0) pool.stop(lastStream)
            lastStream = pool.play(soundId, 1f, 1f, 1, 0, 1f)
        } else {
            pendingPlay = true
        }
    }
}
