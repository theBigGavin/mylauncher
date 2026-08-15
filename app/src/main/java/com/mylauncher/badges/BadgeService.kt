package com.mylauncher.badges

import android.content.Context
import android.os.Process
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 各应用的通知计数(角标数据源)。由 [BadgeService] 维护。
 * 键 = AppEntry.badgeKey:主用户 = 包名,分身 = "包名#userId" —— 分身与原应用分开计数。
 */
object BadgeStore {
    val counts: MutableStateFlow<Map<String, Int>> = MutableStateFlow(emptyMap())
}

/**
 * 通知角标:监听系统通知,统计每个包当前活跃通知数。
 * 需要用户在系统"通知使用权"中开启本应用(设置页有入口)。
 */
class BadgeService : NotificationListenerService() {

    override fun onListenerConnected() = updateCounts()

    override fun onNotificationPosted(sbn: StatusBarNotification?) = updateCounts()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = updateCounts()

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

    private fun updateCounts() {
        val map = try {
            activeNotifications
                .groupBy { badgeKey(it) }
                .mapValues { it.value.size }
        } catch (e: Exception) {
            emptyMap()
        }
        BadgeStore.counts.value = map
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
