package com.mylauncher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 手速排行榜后端基地址(20 卡已上线:mrd server 挂载 /api/v1/knock)。
 * 换环境只改这一处(与计划文档契约一致:Base = https://<域名>/api/v1/knock)。
 */
const val LEADERBOARD_BASE_URL = "https://mrd.hermes.cc.cd/api/v1/knock"

/** 榜单条目(服务端只存昵称与成绩,无设备标识)。 */
data class LeaderboardEntry(
    val rank: Int,
    val nickname: String,
    /** 次/秒,服务端原值;显示侧按 1 位小数格式化。 */
    val rate: Double,
    val createdAt: Long,
)

/** GET /leaderboard 响应。 */
data class LeaderboardData(
    val entries: List<LeaderboardEntry>,
    val totalPlayers: Int,
    /** 我的最好成绩名次(带 nickname 查询时返回;未上榜/未查询为 null)。 */
    val myRank: Int?,
)

/** POST /submit 响应(直接回 top10,省一次请求)。 */
data class SubmitResponse(
    val rank: Int,
    val totalPlayers: Int,
    val entries: List<LeaderboardEntry>,
)

/**
 * 排行榜网络层:HttpURLConnection + Dispatchers.IO,零新增依赖。
 * 超时 10s;任何失败返回 null,由调用方静默降级(榜单"加载失败,点击重试" / 弹窗回退基础文案)。
 */
object LeaderboardApi {

    private const val TIMEOUT_MS = 10_000

    /** GET /leaderboard?limit=10[&nickname=],失败返回 null。 */
    suspend fun fetchLeaderboard(nickname: String? = null): LeaderboardData? = withContext(Dispatchers.IO) {
        runCatching {
            val sb = StringBuilder("$LEADERBOARD_BASE_URL/leaderboard?limit=10")
            if (!nickname.isNullOrBlank()) {
                sb.append("&nickname=").append(URLEncoder.encode(nickname.trim(), "UTF-8"))
            }
            val json = request(sb.toString(), "GET", null)
            LeaderboardData(
                entries = parseEntries(json.optJSONArray("leaderboard")),
                totalPlayers = json.optInt("totalPlayers", 0),
                myRank = if (json.isNull("myRank")) null else json.optInt("myRank", -1).takeIf { it >= 0 },
            )
        }.getOrNull()
    }

    /**
     * GET /percentile?rate=<1位小数> — 该成绩超过的全球用户比例(0-100),只读不写。
     * 失败返回 null(弹窗回退基础文案)。
     */
    suspend fun fetchPercentile(rate: Float): Float? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$LEADERBOARD_BASE_URL/percentile?rate=${String.format(java.util.Locale.US, "%.1f", rate)}"
            val json = request(url, "GET", null)
            json.optDouble("percentile", 0.0).toFloat()
        }.getOrNull()
    }

    /**
     * POST /submit {nickname, rate, gapMs, samples},失败返回 null。
     * 注意:rate 必须提交精确值(1000/gapMs),不能 1 位小数 —— 服务端轻校验
     * abs(1000/rate - gapMs) <= 0.6,小 rate 场景 round 1 位会超容差被拒(实测 gapMs=199/rate=5.0 → 400)。
     */
    suspend fun submit(nickname: String, rate: Float, gapMs: Int, samples: Int): SubmitResponse? =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject()
                    .put("nickname", nickname.trim())
                    .put("rate", rate.toDouble())
                    .put("gapMs", gapMs)
                    .put("samples", samples)
                val json = request("$LEADERBOARD_BASE_URL/submit", "POST", payload.toString())
                SubmitResponse(
                    rank = json.optInt("rank", 0),
                    totalPlayers = json.optInt("totalPlayers", 0),
                    entries = parseEntries(json.optJSONArray("leaderboard")),
                )
            }.getOrNull()
        }

    private fun request(url: String, method: String, body: String?): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = (stream ?: return JSONObject()).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseEntries(arr: JSONArray?): List<LeaderboardEntry> = buildList {
        if (arr == null) return@buildList
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(
                LeaderboardEntry(
                    rank = o.optInt("rank", 0),
                    nickname = o.optString("nickname", ""),
                    rate = o.optDouble("rate", 0.0),
                    createdAt = o.optLong("createdAt", 0L),
                )
            )
        }
    }
}
