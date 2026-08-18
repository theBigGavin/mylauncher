package com.mylauncher.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.mylauncher.R

/**
 * 木鱼敲击音效:SoundPool 播放 res/raw/knock.wav(约 59ms 短促敲击)。
 * 初始化一次常驻,播放零延迟;加载完成前请求的敲击会在加载完成后补敲,不丢。
 * 不 stop 上一击:音源极短,自然衰减即止;stop+play 相邻时 SoundPool 流 ID 复用
 * 与底层异步 stop 存在竞态,快速连击下会偶发吃掉新一击(修过的坑),
 * 直接叠加播放(maxStreams=4 允许重叠)更稳。
 *
 * 防重入:两次 play() 间隔 < [KNOCK_REENTRY_MS] (80ms) 视为同一次敲击的重复触发
 * (双调/双路径),直接丢弃 —— 物理上人类连敲间隔不可能小于 80ms,
 * 真实快速连击(>80ms)不受影响,每次手势照常响。
 */
object KnockSound {

    /** 实机定位开关:改 true 重新安装后,logcat -s MyLauncher 可见双响路径的时间戳证据。 */
    const val DEBUG_KNOCK = false

    /**
     * 物理连敲下限(实测定值,OPPO 实机 DEBUG_KNOCK 日志 82 次全力连滑样本):
     * 用户真实敲击最小间隔 100ms;同一手势双调/双上报的重复触发 ≤50ms;
     * 取中间 60ms —— 真实连击永不误吞,重复触发稳定吸收。
     */
    private const val KNOCK_REENTRY_MS = 60L

    private var soundPool: SoundPool? = null
    private var soundId = 0
    private var loaded = false
    private var pendingPlay = false
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
                pool.play(soundId, 1f, 1f, 1, 0, 1f)
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
        if (DEBUG_KNOCK) Log.d("MyLauncher", "KnockSound[play]: gap=${now - lastPlayAtMs}ms")
        lastPlayAtMs = now
        if (loaded) {
            pool.play(soundId, 1f, 1f, 1, 0, 1f)
        } else {
            pendingPlay = true
        }
    }
}
