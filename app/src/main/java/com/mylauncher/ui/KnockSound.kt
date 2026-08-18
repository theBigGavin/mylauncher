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
     * 物理连敲下限(实测定值,持续修正):用户真实敲击已实测到 23.3 次/秒(≈43ms),
     * 同一手势双调/双上报的重复触发为毫秒级(未实测到 >20ms 的样本);取 20ms ——
     * 真实连击(到 50 次/秒)不误吞,重复触发稳定吸收。
     * 注:音源 59ms,超过 ~17 次/秒后放音本就互相重叠,计数与冒泡不受影响。
     */
    private const val KNOCK_REENTRY_MS = 20L

    private var soundPool: SoundPool? = null
    private var soundId = 0
    private var loaded = false
    private var pendingPlay = false
    private var lastPlayAtMs = 0L

    // 会话内最快连击间隔(不含首击/闲置间隔);刷新纪录时回调(主屏持久化为历史纪录)
    private var fastestGapMs = 0
    var onFastestKnock: ((Int) -> Unit)? = null

    // 会话内有效敲击次数(通过防重入判定的每次敲击 +1)
    // 排行榜上传门槛:连击 10 次以上才能上传(samples >= 10,随纪录一并上报)
    private var sessionSamples = 0
    private var lastKnockAtMs = 0L

    /** 会话内有效敲击样本数(进程生命周期内累计;冷启动重置)。 */
    val samples: Int
        get() = sessionSamples

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

    /**
     * 记录一次有效敲击:样本 +1、刷新会话最快纪录(与是否放音无关)。
     * 音效开关关闭时也必须调用——旧实现把样本/纪录埋在 play() 里,
     * 关音效后敲击不计数、纪录不更新,上传按钮永久置灰(修过的坑)。
     */
    fun recordKnock() {
        val now = System.currentTimeMillis()
        val gap = (now - lastKnockAtMs).toInt()
        if (gap < KNOCK_REENTRY_MS) return
        lastKnockAtMs = now
        sessionSamples++
        // 连击手速纪录:真实连击(<2s)间隔刷新会话最快值
        if (gap in (KNOCK_REENTRY_MS + 1)..2000 && (fastestGapMs == 0 || gap < fastestGapMs)) {
            fastestGapMs = gap
            onFastestKnock?.invoke(gap)
        }
    }

    /** 敲一次木鱼(非阻塞;未加载完时挂起,加载完成后补敲)。放音侧自带防重入地板。 */
    fun play() {
        val pool = soundPool ?: return
        val now = System.currentTimeMillis()
        val gap = (now - lastPlayAtMs).toInt()
        if (gap < KNOCK_REENTRY_MS) {
            if (DEBUG_KNOCK) Log.d("MyLauncher", "KnockSound[reentry-dropped]: gap=${gap}ms")
            return
        }
        if (DEBUG_KNOCK) Log.d("MyLauncher", "KnockSound[play]: gap=${gap}ms")
        lastPlayAtMs = now
        if (loaded) {
            pool.play(soundId, 1f, 1f, 1, 0, 1f)
        } else {
            pendingPlay = true
        }
    }
}
