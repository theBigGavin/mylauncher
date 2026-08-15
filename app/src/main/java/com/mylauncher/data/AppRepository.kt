package com.mylauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
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
    /** 应用所属用户:应用分身(如 ColorOS 分身)运行在非主用户,user 用于区分与启动。 */
    val user: UserHandle = Process.myUserHandle(),
) {
    /** 主用户形如 "pkg/activity"(兼容既有持久化数据);非主用户加 "#userId" 后缀区分分身。 */
    val component: String get() =
        if (user == Process.myUserHandle()) "$packageName/$activityName"
        else "$packageName/$activityName#${user.hashCode()}"

    /** 是否主用户应用(非分身)。 */
    val isMainUser: Boolean get() = user == Process.myUserHandle()

    /** 通知角标键:主用户 = 包名,分身 = "包名#userId"(与 BadgeStore.counts 的键一致)。 */
    val badgeKey: String get() =
        if (isMainUser) packageName else "$packageName#${user.hashCode()}"
}

class AppRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager
    private val launcherApps: LauncherApps? =
        runCatching { appContext.getSystemService(LauncherApps::class.java) }.getOrNull()
    private val um: UserManager? =
        runCatching { appContext.getSystemService(UserManager::class.java) }.getOrNull()

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

    /** 查询中间结果(两条查询路径统一到该结构再转 AppEntry)。 */
    private class RawLaunch(
        val packageName: String,
        val activityName: String,
        val label: String,
        val isSystem: Boolean,
        val user: UserHandle,
        val hasIcon: Boolean,
    )

    /** 全量查询:经 LauncherApps 枚举所有可见用户(主用户 + 应用分身),应用分身也能上桌面。 */
    private fun queryLaunchableApps(): List<AppEntry> {
        val collator = Collator.getInstance(Locale.getDefault())
        val launcher = launcherApps
        // 查询失败或未设为默认桌面时,退回主用户 PackageManager 查询。
        // 注意:LauncherApps.profiles 只含受管 profile,分身用户(独立普通用户,如 ColorOS 999)
        // 不在其中 —— 用 UserManager.getUserHandles(API 34+,@hide,反射调用,需 QUERY_USERS)兜底;
        // 每个用户的 getActivityList 是否可见由系统对默认桌面的授权决定,失败的用户静默跳过。
        val profiles: List<UserHandle> = if (launcher != null) {
            val lp = runCatching { launcher.profiles }.getOrDefault(emptyList())
            val all = um?.let { u ->
                runCatching {
                    val m = UserManager::class.java.getMethod(
                        "getUserHandles", Boolean::class.javaPrimitiveType
                    )
                    @Suppress("UNCHECKED_CAST")
                    m.invoke(u, false) as List<UserHandle>
                }.getOrDefault(emptyList())
            } ?: emptyList()
            (lp + all).distinctBy { it.hashCode() }
        } else {
            listOf(Process.myUserHandle())
        }
        return profiles.asSequence()
            .flatMap { user ->
                if (launcher != null) {
                    val list = runCatching { launcher.getActivityList(null, user) }
                        .getOrElse { emptyList() }
                    list.asSequence()
                        .map { info ->
                            val ai = info.applicationInfo
                            RawLaunch(
                                packageName = ai.packageName,
                                activityName = info.componentName.className,
                                label = info.label?.toString() ?: ai.packageName,
                                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                                user = user,
                                hasIcon = ai.icon != 0,
                            )
                        }
                } else {
                    pm.queryIntentActivities(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
                    ).asSequence().map { ri ->
                        val ai = ri.activityInfo ?: return@map null
                        RawLaunch(
                            packageName = ai.packageName,
                            activityName = ai.name,
                            label = ri.loadLabel(pm).toString(),
                            isSystem = (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                            user = user,
                            hasIcon = ai.icon != 0 || ai.applicationInfo.icon != 0,
                        )
                    }.filterNotNull()
                }
            }
            .filter {
                it.packageName != appContext.packageName &&
                    // 无图标、无界面的系统壳应用对用户不可见,过滤掉
                    it.hasIcon
            }
            .map {
                AppEntry(
                    packageName = it.packageName,
                    activityName = it.activityName,
                    // 非主用户应用(应用分身)加"·分身"标注,与主应用区分
                    label = if (it.user != Process.myUserHandle()) "${it.label}·分身" else it.label,
                    isSystem = it.isSystem,
                    user = it.user,
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

    /** 启动应用:主用户直接 startActivity;分身经 LauncherApps.startMainActivity 定位到对应用户。 */
    fun launch(entry: AppEntry) {
        if (entry.isMainUser) {
            runCatching { appContext.startActivity(launchIntent(entry)) }
        } else {
            runCatching {
                launcherApps?.startMainActivity(
                    ComponentName(entry.packageName, entry.activityName),
                    entry.user,
                    null,
                    null,
                )
            }
        }
    }
}
