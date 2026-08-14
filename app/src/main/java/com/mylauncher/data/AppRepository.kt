package com.mylauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/** 一个可启动的 App(入口 Activity 粒度)。 */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystem: Boolean,
) {
    /** 形如 "pkg/activity" 的唯一键,用于持久化。 */
    val component: String get() = "$packageName/$activityName"
}

class AppRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    /** null 表示尚未加载完成。 */
    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    val apps: StateFlow<List<AppEntry>?> = _apps

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 同步全量查询(PackageManager IPC,勿在主线程调用)。 */
    fun refresh() {
        _apps.value = queryLaunchableApps()
    }

    /** 异步刷新:供广播接收器等主线程入口使用。 */
    fun refreshAsync() {
        bgScope.launch { refresh() }
    }

    @Suppress("DEPRECATION")
    private fun queryLaunchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.getDefault())
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .filter {
                val ai = it.activityInfo
                ai != null &&
                    ai.packageName != appContext.packageName &&
                    // 无图标、无界面的系统壳应用对用户不可见,过滤掉
                    (ai.icon != 0 || ai.applicationInfo.icon != 0)
            }
            .map {
                AppEntry(
                    packageName = it.activityInfo.packageName,
                    activityName = it.activityInfo.name,
                    label = it.loadLabel(pm).toString(),
                    isSystem = (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .distinctBy { it.component }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
            .toList()
    }

    fun launchIntent(entry: AppEntry): Intent =
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(entry.packageName, entry.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
