package com.mylauncher.badges

import android.content.Context
import android.os.Process
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 各应用的通知计数(角标数据源)。由 [BadgeService] 维护。
 * 键 = AppEntry.badgeKey:主用户 = 包名,分身 = "包名#userId" —— 分身与原应用分开计数。
 */
object BadgeStore {
    val counts: MutableStateFlow<Map<String, Int>> = MutableStateFlow(emptyMap())

    /** 以全量替换计数(全量校准/服务连接时)。与增量更新互斥,避免读改写交错。 */
    fun replace(map: Map<String, Int>) {
        synchronized(this) { counts.value = map }
    }

    /** 增量更新:读取当前计数,经 [transform] 变换后写回。回调线程与周期线程并发安全。 */
    fun update(transform: (MutableMap<String, Int>) -> Unit) {
        synchronized(this) {
            val cur = counts.value.toMutableMap()
            transform(cur)
            counts.value = cur
        }
    }
}

/**
 * 通知角标:监听系统通知,统计每个包当前活跃通知数。
 * 需要用户在系统"通知使用权"中开启本应用(设置页有入口)。
 *
 * 计数策略 = 增量 + 全量混合(以全量为准):
 * - 回调路径做增量:posted +1 / removed -1,直接用回调参数 sbn 的包名计数,不依赖 activeNotifications 全量
 *   —— 覆盖部分 ROM 的 activeNotifications 返回不完整(通知栏有、全量查不到)的根因 B。
 * - 周期全量校准(主屏 ON_RESUME + 每 5s requestRefresh + 服务连接时)以 activeNotifications 重算为准
 *   —— 覆盖回调丢失/合并(连续多条只回调一次,根因 A)的场景,同时防止增量因重复回调漂移虚高。
 * - 异常场景(应用卸载/通知权限回收/监听服务被杀重启)由全量校准兜底归位。
 * - 诊断日志(tag=BadgeService):增量路径记录包名+新计数;全量校准与增量不一致时记录
 *   包名、增量值、全量值、修正方向 —— 上线后看日志即可判定根因 A(回调丢失:全量修正日志)
 *   还是根因 B(activeNotifications 漏:有增量日志但全量无此包)。
 */
class BadgeService : NotificationListenerService() {

    companion object {
        /** 供主屏在 ON_RESUME/周期时机主动请求重算计数(部分 ROM 的通知回调会丢,靠重算兜底)。 */
        var requestRefresh: (() -> Unit)? = null
        private const val TAG = "BadgeService"
    }

    override fun onListenerConnected() {
        requestRefresh = ::updateCounts
        updateCounts()
    }

    /** 增量 +1:基于回调参数 sbn 的包名直接计数(不依赖 activeNotifications 全量,覆盖全量漏通知的 ROM)。 */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) {
            // 拿不到包名,退回全量兜底
            updateCounts()
            return
        }
        val key = badgeKey(sbn)
        BadgeStore.update { it[key] = (it[key] ?: 0) + 1 }
        Log.i(TAG, "增量+1: $key -> ${BadgeStore.counts.value[key]}")
    }

    /** 增量 -1:用户清除/滑动移除/全部清除时系统逐条回调,计数对应减少;clamp ≥ 0 防负。 */
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) {
            // 拿不到包名,退回全量兜底
            updateCounts()
            return
        }
        val key = badgeKey(sbn)
        BadgeStore.update {
            val v = (it[key] ?: 0) - 1
            if (v > 0) it[key] = v else it.remove(key) // ≤0 直接移除键,保持 map 干净
        }
        Log.i(TAG, "增量-1: $key -> ${BadgeStore.counts.value[key] ?: 0}")
    }

    override fun onListenerDisconnected() {
        requestRefresh = null
    }

    /** 通知所属用户的角标键:分身通知(如 ColorOS 转发)可能与原应用同包名,按用户区分。 */
    private fun badgeKey(sbn: StatusBarNotification): String {
        val user = sbn.user?.let {
            if (it == Process.myUserHandle()) 0 else it.hashCode()
        } ?: 0
        // 部分 ROM 转发分身通知时 user 字段标成 0,但 uid 保留原用户:用 uid 兜底还原
        val fromUid = sbn.uid / 100000
        val userId = if (user != 0) user else if (fromUid > 0) fromUid else 0
        return if (userId == 0) sbn.packageName else "${sbn.packageName}#$userId"
    }

    /**
     * 全量校准:以 activeNotifications 重算为准,写回 BadgeStore(以全量为准)。
     * 与当前增量值不一致时打日志(包名、增量值、全量值、修正方向),用于上线后判定根因:
     * - 全量 > 增量 = 回调丢失(根因 A),全量补上;
     * - 全量 < 增量 = 重复回调虚高,全量校准压低;
     * - 增量有、全量无 = activeNotifications 漏通知(根因 B 特征)。
     * 一致时静默,避免 5s 周期刷屏。
     */
    private fun updateCounts() {
        val map = try {
            activeNotifications
                .groupBy { badgeKey(it) }
                .mapValues { it.value.size }
        } catch (e: Exception) {
            emptyMap()
        }
        val prev = BadgeStore.counts.value
        // 全量有、增量没有或不同的包:回调丢失或增量漂移
        for ((key, full) in map) {
            val inc = prev[key]
            if (inc == null || inc != full) {
                val direction = if ((inc ?: 0) < full) "up(回调丢失,全量补上)" else "down(增量虚高,全量校准)"
                Log.i(TAG, "全量校准: $key 增量=$inc 全量=$full 方向=$direction")
            }
        }
        // 增量有、全量没有的包:activeNotifications 漏通知(根因 B 特征)
        for (key in prev.keys) {
            if (!map.containsKey(key)) {
                Log.i(TAG, "全量校准: $key 增量=${prev[key]} 全量=0 方向=down(activeNotifications 无此包,增量被清零)")
            }
        }
        BadgeStore.replace(map)
    }
}

/** 通知使用权是否已开启(系统设置里用户手动授权)。 */
fun isBadgeListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return flat.split(':').any { it.startsWith(context.packageName) }
}
