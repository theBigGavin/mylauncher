package com.mylauncher.data

import android.content.Context
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
    val iconSizeDp: Int,
    val fontSizeSp: Int,
    val showIcons: Boolean,
    val rowSpacingDp: Int,
    val showBadges: Boolean,
    /** 图标是否显示原彩(否则单色化白剪影)。 */
    val showOriginalColor: Boolean,
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
        const val MAX_APPS = 20
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
        private val KEY_INIT = booleanPreferencesKey("initialized")
        private val KEY_ICON = intPreferencesKey("icon_size_dp")
        private val KEY_FONT = intPreferencesKey("font_size_sp")
        private val KEY_SHOW_ICONS = booleanPreferencesKey("show_icons")
        private val KEY_ROW_SPACING = intPreferencesKey("row_spacing_dp")
        private val KEY_SHOW_BADGES = booleanPreferencesKey("show_badges")
        private val KEY_SHOW_COLOR = booleanPreferencesKey("show_original_color")
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
        HomeData(
            initialized = p[KEY_INIT] ?: false,
            entries = deserialize(p[KEY_ENTRIES].orEmpty()),
            iconSizeDp = p[KEY_ICON] ?: DEFAULT_ICON_DP,
            fontSizeSp = p[KEY_FONT] ?: DEFAULT_FONT_SP,
            showIcons = p[KEY_SHOW_ICONS] ?: true,
            rowSpacingDp = p[KEY_ROW_SPACING] ?: DEFAULT_ROW_SPACING_DP,
            showBadges = p[KEY_SHOW_BADGES] ?: true,
            showOriginalColor = p[KEY_SHOW_COLOR] ?: false,
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
            it[KEY_ENTRIES] = serialize(entries.take(MAX_APPS))
            it[KEY_INIT] = true
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
        context.homeDataStore.edit { it[KEY_ROW_SPACING] = value.coerceIn(0, 48) }
    }

    suspend fun setShowBadges(value: Boolean) {
        context.homeDataStore.edit { it[KEY_SHOW_BADGES] = value }
    }

    suspend fun setShowOriginalColor(value: Boolean) {
        context.homeDataStore.edit { it[KEY_SHOW_COLOR] = value }
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
            it[KEY_INIT] = true
            it[KEY_ICON] = DEFAULT_ICON_DP
            it[KEY_FONT] = DEFAULT_FONT_SP
            it[KEY_SHOW_ICONS] = true
            it[KEY_ROW_SPACING] = DEFAULT_ROW_SPACING_DP
            it[KEY_SHOW_BADGES] = true
            it[KEY_SHOW_COLOR] = false
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
}
