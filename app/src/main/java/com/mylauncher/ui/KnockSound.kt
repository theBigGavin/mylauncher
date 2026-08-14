package com.mylauncher.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 木鱼敲击音效:AudioTrack 实时合成 —— 低频正弦快速衰减(空腔共鸣)+ 起始瞬态噪声(敲击),
 * 无需任何音频资源文件。
 */
object KnockSound {

    private const val SAMPLE_RATE = 44100

    /** 播放一次木鱼声(IO 线程执行,不阻塞 UI)。 */
    suspend fun play() = withContext(Dispatchers.IO) {
        runCatching {
            val duration = 0.30f // 300ms
            val n = (SAMPLE_RATE * duration).toInt()
            val samples = ShortArray(n)
            for (i in 0 until n) {
                val t = i / SAMPLE_RATE.toFloat()
                // 空腔共鸣:130Hz 主频 + 双频叠加(更饱满低沉,接近木鱼)
                val body = (sin(2.0 * PI * 130.0 * t) + 0.6 * sin(2.0 * PI * 260.0 * t)) * exp(-t * 14f)
                // 起始瞬态:高频小噪声让敲击更有"哒"感
                val click = sin(2.0 * PI * 700.0 * t) * exp(-t * 100f) * 0.6
                // 中段轻微回响
                val echo = sin(2.0 * PI * 130.0 * t) * exp(-(t - 0.10f).coerceAtLeast(0f) * 26f) * 0.4f
                samples[i] = (((body + click + echo) * 0.9) * Short.MAX_VALUE).toInt().toShort()
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(n * 2)
                .build()
            track.play()
            track.write(samples, 0, n)
            track.stop()
            track.release()
        }
    }
}
