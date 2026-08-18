package com.mylauncher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.homeDataStore by preferencesDataStore(name = "mylauncher_home")

/** 主屏条目:组件键 + 可选自定义名。 */
data class StoredEntry(val component: String, val customName: String?)

data class HomeData(
    val initialized: Boolean,
    val entries: List<StoredEntry>,
    /** 桌面应用槽位数(用户可配置,4..20)。 */
    val maxApps: Int,
    /** 收藏的应用组件键集合(抽屉"所有应用"置顶显示)。 */
    val favorites: Set<String>,
    /** 抽屉/选择器是否显示系统应用(持久化用户习惯)。 */
    val showSystem: Boolean,
    val iconSizeDp: Int,
    val fontSizeSp: Int,
    val showIcons: Boolean,
    val rowSpacingDp: Int,
    val showBadges: Boolean,
    /** 图标是否显示原彩(否则单色化白剪影)。 */
    val showOriginalColor: Boolean,
    /** 功德彩蛋:当日累计计数(取自按日历史)。 */
    val meritCount: Int,
    val meritDate: String,
    /** 功德按日历史(日期 → 当日计数,保留最近 365 天)。 */
    val meritHistory: Map<String, Int>,
    /** 历史最高单日功德(每日统计峰值)。 */
    val meritPeak: Int,
    /** 最快连击手速纪录(两次敲击的最小间隔 ms,0 = 暂无)。 */
    val fastestKnockGapMs: Int,
    /** 排行榜昵称(本地持久化,20b 输入框写入;空白 = 未设置)。 */
    val leaderboardNickname: String,
    val easterEggEnabled: Boolean,
    /** 彩蛋是否已解锁(设置页连点版本号 5 次激活,激活后才显示彩蛋设置分组)。 */
    val easterEggUnlocked: Boolean,
    val meritSoundEnabled: Boolean,
    /** 功德文字(冒泡显示"文字+N",默认"功德",用户可自定义)。 */
    val meritLabel: String,
    /** 自动积累功德开关。 */
    val autoMeritEnabled: Boolean,
    /** 自动积累连续时长上限(秒,10..600):开启后每秒 +1,最多连续积累这么多秒。 */
    val autoMeritMaxS: Int,
    val wallpaperMode: String,
    /** 桌面列表视口高度(占屏高百分比,横竖屏分开配置)。 */
    val listHeightPercent: Int,
    val listHeightPercentLandscape: Int,
    /** 自定义壁纸裁切(按形态独立保存):scale = 铺满基准上的用户缩放倍数;offsetX/Y = 屏宽/屏高比例的平移。 */
    val customWallpaperScale: Float,
    val customWallpaperOffsetX: Float,
    val customWallpaperOffsetY: Float,
    val customWallpaperScaleLandscape: Float,
    val customWallpaperOffsetXLandscape: Float,
    val customWallpaperOffsetYLandscape: Float,
    val customWallpaperScaleInner: Float,
    val customWallpaperOffsetXInner: Float,
    val customWallpaperOffsetYInner: Float,
)

class HomeStore(private val context: Context) {

    companion object {
        const val MAX_APPS = 100
        /** 桌面槽位数的可配置范围:最少 4 个,最多 100 个;默认 20。 */
        const val MIN_MAX_APPS = 4
        const val DEFAULT_MAX_APPS = 20
        const val DEFAULT_ICON_DP = 38
        const val DEFAULT_FONT_SP = 26
        const val DEFAULT_ROW_SPACING_DP = 0

        const val WALLPAPER_BUILTIN = "builtin"
        const val WALLPAPER_SYSTEM = "system"
        const val WALLPAPER_CUSTOM = "custom"
        const val WALLPAPER_FORM_PORTRAIT = "portrait"
        const val WALLPAPER_FORM_LANDSCAPE = "landscape"
        const val WALLPAPER_FORM_INNER = "inner"
        const val DEFAULT_LIST_HEIGHT_PERCENT = 50

        private val KEY_ENTRIES = stringPreferencesKey("home_entries")
        private val KEY_MAX_APPS = intPreferencesKey("max_apps")
        private val KEY_FAVORITES = stringPreferencesKey("favorites")
        private val KEY_SHOW_SYSTEM = booleanPreferencesKey("show_system")
        private val KEY_INIT = booleanPreferencesKey("initialized")
        private val KEY_ICON = intPreferencesKey("icon_size_dp")
        private val KEY_FONT = intPreferencesKey("font_size_sp")
        private val KEY_SHOW_ICONS = booleanPreferencesKey("show_icons")
        private val KEY_ROW_SPACING = intPreferencesKey("row_spacing_dp")
        private val KEY_SHOW_BADGES = booleanPreferencesKey("show_badges")
        private val KEY_SHOW_COLOR = booleanPreferencesKey("show_original_color")
        private val KEY_MERIT_COUNT = intPreferencesKey("merit_count")
        private val KEY_MERIT_DATE = stringPreferencesKey("merit_date")
        /** 功德按日历史(每行 "日期 计数",保留最近 365 天)——总功德/每日统计的数据基础。 */
        private val KEY_MERIT_HISTORY = stringPreferencesKey("merit_history")
        private val KEY_FASTEST_KNOCK = intPreferencesKey("fastest_knock_gap_ms")
        /** 排行榜昵称(20b 昵称输入框读写)。 */
        private val KEY_LEADERBOARD_NICKNAME = stringPreferencesKey("leaderboard_nickname")
        private const val MERIT_HISTORY_DAYS = 365
        private val KEY_EASTER_EGG = booleanPreferencesKey("easter_egg_enabled")
        private val KEY_EASTER_UNLOCKED = booleanPreferencesKey("easter_egg_unlocked")
        /** 数据 schema 版本:存量数据一次性迁移的标记。 */
        private val KEY_SCHEMA = intPreferencesKey("schema_version")
        private val KEY_MERIT_SOUND = booleanPreferencesKey("merit_sound_enabled")
        private val KEY_MERIT_LABEL = stringPreferencesKey("merit_label")
        private val KEY_AUTO_MERIT = booleanPreferencesKey("auto_merit_enabled")
        private val KEY_AUTO_MERIT_INTERVAL = intPreferencesKey("auto_merit_interval_s")

        const val DEFAULT_MERIT_LABEL = "功德"
        const val DEFAULT_AUTO_MERIT_INTERVAL_S = 10
        const val MIN_AUTO_MERIT_INTERVAL_S = 10
        const val MAX_AUTO_MERIT_INTERVAL_S = 600
        private val KEY_WALLPAPER = stringPreferencesKey("wallpaper_mode")
        private val KEY_LIST_HEIGHT = intPreferencesKey("list_height_percent")
        private val KEY_LIST_HEIGHT_LS = intPreferencesKey("list_height_percent_ls")
        private val KEY_WP_SCALE = floatPreferencesKey("custom_wallpaper_scale")
        private val KEY_WP_OFFSET_X = floatPreferencesKey("custom_wallpaper_offset_x")
        private val KEY_WP_OFFSET_Y = floatPreferencesKey("custom_wallpaper_offset_y")
        private val KEY_WP_SCALE_LS = floatPreferencesKey("custom_wallpaper_scale_ls")
        private val KEY_WP_OFFSET_X_LS = floatPreferencesKey("custom_wallpaper_offset_x_ls")
        private val KEY_WP_OFFSET_Y_LS = floatPreferencesKey("custom_wallpaper_offset_y_ls")
        private val KEY_WP_SCALE_IN = floatPreferencesKey("custom_wallpaper_scale_in")
        private val KEY_WP_OFFSET_X_IN = floatPreferencesKey("custom_wallpaper_offset_x_in")
        private val KEY_WP_OFFSET_Y_IN = floatPreferencesKey("custom_wallpaper_offset_y_in")
    }

    val data: Flow<HomeData> = context.homeDataStore.data.map { p ->
        val merit = meritFrom(p)
        HomeData(
            initialized = p[KEY_INIT] ?: false,
            entries = deserialize(p[KEY_ENTRIES].orEmpty()),
            maxApps = p[KEY_MAX_APPS] ?: DEFAULT_MAX_APPS,
            favorites = deserializeFavorites(p[KEY_FAVORITES].orEmpty()),
            showSystem = p[KEY_SHOW_SYSTEM] ?: false,
            iconSizeDp = p[KEY_ICON] ?: DEFAULT_ICON_DP,
            fontSizeSp = p[KEY_FONT] ?: DEFAULT_FONT_SP,
            showIcons = p[KEY_SHOW_ICONS] ?: true,
            rowSpacingDp = p[KEY_ROW_SPACING] ?: DEFAULT_ROW_SPACING_DP,
            showBadges = p[KEY_SHOW_BADGES] ?: true,
            showOriginalColor = p[KEY_SHOW_COLOR] ?: false,
            meritCount = merit.count,
            meritDate = merit.date,
            meritHistory = merit.history,
            meritPeak = merit.history.values.maxOrNull() ?: 0,
            fastestKnockGapMs = p[KEY_FASTEST_KNOCK] ?: 0,
            leaderboardNickname = p[KEY_LEADERBOARD_NICKNAME] ?: "",
            easterEggEnabled = p[KEY_EASTER_EGG] ?: false,
            easterEggUnlocked = p[KEY_EASTER_UNLOCKED] ?: false,
            meritSoundEnabled = p[KEY_MERIT_SOUND] ?: true,
            meritLabel = p[KEY_MERIT_LABEL]?.takeIf { it.isNotBlank() } ?: DEFAULT_MERIT_LABEL,
            autoMeritEnabled = p[KEY_AUTO_MERIT] ?: false,
            autoMeritMaxS = p[KEY_AUTO_MERIT_INTERVAL] ?: DEFAULT_AUTO_MERIT_INTERVAL_S,
            wallpaperMode = p[KEY_WALLPAPER] ?: WALLPAPER_BUILTIN,
            listHeightPercent = p[KEY_LIST_HEIGHT] ?: DEFAULT_LIST_HEIGHT_PERCENT,
            listHeightPercentLandscape = p[KEY_LIST_HEIGHT_LS] ?: 100,
            customWallpaperScale = p[KEY_WP_SCALE] ?: 1f,
            customWallpaperOffsetX = p[KEY_WP_OFFSET_X] ?: 0f,
            customWallpaperOffsetY = p[KEY_WP_OFFSET_Y] ?: 0f,
            customWallpaperScaleLandscape = p[KEY_WP_SCALE_LS] ?: 1f,
            customWallpaperOffsetXLandscape = p[KEY_WP_OFFSET_X_LS] ?: 0f,
            customWallpaperOffsetYLandscape = p[KEY_WP_OFFSET_Y_LS] ?: 0f,
            customWallpaperScaleInner = p[KEY_WP_SCALE_IN] ?: 1f,
            customWallpaperOffsetXInner = p[KEY_WP_OFFSET_X_IN] ?: 0f,
            customWallpaperOffsetYInner = p[KEY_WP_OFFSET_Y_IN] ?: 0f,
        )
    }

    suspend fun setEntries(entries: List<StoredEntry>) {
        context.homeDataStore.edit {
            val max = it[KEY_MAX_APPS] ?: DEFAULT_MAX_APPS
            it[KEY_ENTRIES] = serialize(entries.take(max))
            it[KEY_INIT] = true
        }
    }

    suspend fun setMaxApps(value: Int) {
        context.homeDataStore.edit { it[KEY_MAX_APPS] = value.coerceIn(MIN_MAX_APPS, MAX_APPS) }
    }

    /** 收藏/取消收藏:收藏的应用在抽屉"所有应用"中置顶。 */
    suspend fun setShowSystem(value: Boolean) {
        context.homeDataStore.edit { it[KEY_SHOW_SYSTEM] = value }
    }

    suspend fun toggleFavorite(component: String, favorite: Boolean) {
        context.homeDataStore.edit {
            val cur = deserializeFavorites(it[KEY_FAVORITES].orEmpty()).toMutableSet()
            if (favorite) cur.add(component) else cur.remove(component)
            it[KEY_FAVORITES] = cur.sorted().joinToString("\n")
        }
    }

    suspend fun setIconSize(value: Int) {
        context.homeDataStore.edit { it[KEY_ICON] = value.coerceIn(24, 56) }
    }

    suspend fun setFontSize(value: Int) {
        context.homeDataStore.edit { it[KEY_FONT] = value.coerceIn(18, 40) }
    }

    suspend fun setShowIcons(value: Boolean) {
        context.homeDataStore.edit { it[KEY_SHOW_ICONS] = value }
    }

    suspend fun setRowSpacing(value: Int) {
        context.homeDataStore.edit { it[KEY_ROW_SPACING] = value.coerceIn(0, 10) }
    }

    suspend fun setShowBadges(value: Boolean) {
        context.homeDataStore.edit { it[KEY_SHOW_BADGES] = value }
    }

    suspend fun setShowOriginalColor(value: Boolean) {
        context.homeDataStore.edit { it[KEY_SHOW_COLOR] = value }
    }

    /** 功德 +1:写入按日历史(保留最近 365 天),总功德/每日统计直接汇总此表。 */
    suspend fun addMerit() {
        val today = java.time.LocalDate.now().toString()
        context.homeDataStore.edit {
            val hist = deserializeMeritHistory(it[KEY_MERIT_HISTORY].orEmpty()).toMutableMap()
            if (hist.isEmpty()) {
                // 旧格式迁移:首次写入时把 merit_date/merit_count 并入历史
                val oldDate = it[KEY_MERIT_DATE]
                val oldCount = it[KEY_MERIT_COUNT] ?: 0
                if (oldDate != null && oldCount > 0) hist[oldDate] = oldCount
            }
            hist[today] = (hist[today] ?: 0) + 1
            it[KEY_MERIT_HISTORY] = hist.toSortedMap()
                .entries
                .toList()
                .takeLast(MERIT_HISTORY_DAYS)
                .joinToString("\n") { (d, c) -> "$d $c" }
            it.remove(KEY_MERIT_DATE)
            it.remove(KEY_MERIT_COUNT)
        }
    }

    suspend fun setEasterEggEnabled(value: Boolean) {
        context.homeDataStore.edit { it[KEY_EASTER_EGG] = value }
    }

    /** 解锁彩蛋(设置页连点版本号 5 次):解锁后显示彩蛋设置分组。 */
    suspend fun setEasterEggUnlocked() {
        context.homeDataStore.edit { it[KEY_EASTER_UNLOCKED] = true }
    }

    /**
     * 存量数据一次性迁移(每次启动调用,幂等):
     * v1.3.1 及更早的版本没有"解锁"概念,彩蛋菜单常显 —— 那时的用户升级上来,
     * 菜单应保持可见,否则像"激活状态丢了"。新装/恢复默认不受影响(无初始化数据/解锁键已存在)。
     */
    suspend fun applyLegacyMigrations() {
        context.homeDataStore.edit {
            if (it[KEY_SCHEMA] == null) {
                if (it[KEY_INIT] == true && it[KEY_EASTER_UNLOCKED] == null) {
                    it[KEY_EASTER_UNLOCKED] = true
                }
                it[KEY_SCHEMA] = 1
            }
        }
    }

    suspend fun setMeritSoundEnabled(value: Boolean) {
        context.homeDataStore.edit { it[KEY_MERIT_SOUND] = value }
    }

    /** 自定义功德文字:存原文(允许编辑中短暂空白),读取侧空白回退默认"功德"。 */
    suspend fun setMeritLabel(value: String) {
        context.homeDataStore.edit { it[KEY_MERIT_LABEL] = value.trim() }
    }

    suspend fun setAutoMeritEnabled(value: Boolean) {
        context.homeDataStore.edit { it[KEY_AUTO_MERIT] = value }
    }

    /** 连续积累时长上限(秒,10..600)。 */
    suspend fun setAutoMeritMaxS(value: Int) {
        context.homeDataStore.edit {
            it[KEY_AUTO_MERIT_INTERVAL] = value.coerceIn(MIN_AUTO_MERIT_INTERVAL_S, MAX_AUTO_MERIT_INTERVAL_S)
        }
    }

    /** 最快连击手速纪录(两次敲击最小间隔 ms;仅纪录刷新时写入)。 */
    suspend fun setFastestKnockGapMs(value: Int) {
        context.homeDataStore.edit {
            val cur = it[KEY_FASTEST_KNOCK] ?: 0
            if (cur == 0 || value < cur) it[KEY_FASTEST_KNOCK] = value
        }
    }

    /** 排行榜昵称:去空白 + 限 16 字符(与榜单契约一致)。 */
    suspend fun setLeaderboardNickname(value: String) {
        context.homeDataStore.edit {
            it[KEY_LEADERBOARD_NICKNAME] = value.trim().take(16)
        }
    }

    suspend fun setListHeightPercent(form: String, value: Int) {
        val v = value.coerceIn(25, 100)
        context.homeDataStore.edit {
            if (form == WALLPAPER_FORM_LANDSCAPE) it[KEY_LIST_HEIGHT_LS] = v
            else it[KEY_LIST_HEIGHT] = v
        }
    }

    suspend fun setWallpaperMode(value: String) {
        context.homeDataStore.edit {
            it[KEY_WALLPAPER] = when (value) {
                WALLPAPER_SYSTEM, WALLPAPER_CUSTOM -> value
                else -> WALLPAPER_BUILTIN
            }
        }
    }

    /** 按形态保存自定义壁纸的裁切变换(缩放倍数 + 屏宽/屏高比例的平移)。 */
    suspend fun setCustomWallpaperTransform(
        form: String,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ) {
        val sc = scale.coerceIn(1f, 5f)
        context.homeDataStore.edit {
            when (form) {
                WALLPAPER_FORM_LANDSCAPE -> {
                    it[KEY_WP_SCALE_LS] = sc
                    it[KEY_WP_OFFSET_X_LS] = offsetX
                    it[KEY_WP_OFFSET_Y_LS] = offsetY
                }
                WALLPAPER_FORM_INNER -> {
                    it[KEY_WP_SCALE_IN] = sc
                    it[KEY_WP_OFFSET_X_IN] = offsetX
                    it[KEY_WP_OFFSET_Y_IN] = offsetY
                }
                else -> {
                    it[KEY_WP_SCALE] = sc
                    it[KEY_WP_OFFSET_X] = offsetX
                    it[KEY_WP_OFFSET_Y] = offsetY
                }
            }
        }
    }

    suspend fun resetAll(defaultEntries: List<StoredEntry>) {
        context.homeDataStore.edit {
            it[KEY_ENTRIES] = serialize(defaultEntries.take(MAX_APPS))
            it[KEY_MAX_APPS] = DEFAULT_MAX_APPS
            it[KEY_INIT] = true
            it[KEY_SHOW_SYSTEM] = false
            it[KEY_ICON] = DEFAULT_ICON_DP
            it[KEY_FONT] = DEFAULT_FONT_SP
            it[KEY_SHOW_ICONS] = true
            it[KEY_ROW_SPACING] = DEFAULT_ROW_SPACING_DP
            it[KEY_SHOW_BADGES] = true
            it[KEY_SHOW_COLOR] = false
            it[KEY_EASTER_EGG] = false
            it[KEY_EASTER_UNLOCKED] = false
            it[KEY_MERIT_SOUND] = true
            it[KEY_MERIT_LABEL] = DEFAULT_MERIT_LABEL
            it[KEY_AUTO_MERIT] = false
            it[KEY_AUTO_MERIT_INTERVAL] = DEFAULT_AUTO_MERIT_INTERVAL_S
            it.remove(KEY_FASTEST_KNOCK)
            it.remove(KEY_LEADERBOARD_NICKNAME)
            it[KEY_MERIT_HISTORY] = ""
            it.remove(KEY_MERIT_DATE)
            it.remove(KEY_MERIT_COUNT)
            it[KEY_WALLPAPER] = WALLPAPER_BUILTIN
        }
    }

    private fun serialize(entries: List<StoredEntry>): String =
        entries.joinToString("\n") { e ->
            val name = e.customName?.replace("\t", " ")?.replace("\n", " ")?.trim()
            if (name.isNullOrEmpty()) e.component else "${e.component}\t$name"
        }

    private fun deserialize(raw: String): List<StoredEntry> =
        raw.split("\n")
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split("\t", limit = 2)
                StoredEntry(
                    component = parts[0],
                    customName = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
                )
            }

    private fun deserializeFavorites(raw: String): Set<String> =
        raw.split("\n").filter { it.isNotBlank() }.toSet()

    private fun deserializeMeritHistory(raw: String): Map<String, Int> =
        raw.split("\n").mapNotNull { line ->
            val parts = line.trim().split(" ", limit = 2)
            parts.getOrNull(0)?.takeIf { it.isNotBlank() }?.let { date ->
                val count = parts.getOrNull(1)?.toIntOrNull() ?: 0
                if (count > 0) date to count else null
            }
        }.toMap()

    /** 功德读数:按日历史 + 旧格式(merit_date/merit_count)迁移兼容。 */
    private fun meritFrom(p: Preferences): MeritState {
        val history = deserializeMeritHistory(p[KEY_MERIT_HISTORY].orEmpty())
        val today = java.time.LocalDate.now().toString()
        val merged = if (history.isEmpty()) {
            val oldDate = p[KEY_MERIT_DATE]
            val oldCount = p[KEY_MERIT_COUNT] ?: 0
            if (oldDate != null && oldCount > 0) mapOf(oldDate to oldCount) else emptyMap()
        } else history
        return MeritState(count = merged[today] ?: 0, date = today, history = merged)
    }

    private data class MeritState(val count: Int, val date: String, val history: Map<String, Int>)
}
