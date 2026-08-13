package com.mylauncher.badges

import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow

/** 各包的通知计数(角标数据源)。由 [BadgeService] 维护。 */
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

    private fun updateCounts() {
        val map = try {
            activeNotifications
                .groupBy { it.packageName }
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
