package com.mylauncher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
)

class HomeStore(private val context: Context) {

    companion object {
        const val MAX_APPS = 20
        const val DEFAULT_ICON_DP = 38
        const val DEFAULT_FONT_SP = 26

        private val KEY_ENTRIES = stringPreferencesKey("home_entries")
        private val KEY_INIT = booleanPreferencesKey("initialized")
        private val KEY_ICON = intPreferencesKey("icon_size_dp")
        private val KEY_FONT = intPreferencesKey("font_size_sp")
        private val KEY_SHOW_ICONS = booleanPreferencesKey("show_icons")
    }

    val data: Flow<HomeData> = context.homeDataStore.data.map { p ->
        HomeData(
            initialized = p[KEY_INIT] ?: false,
            entries = deserialize(p[KEY_ENTRIES].orEmpty()),
            iconSizeDp = p[KEY_ICON] ?: DEFAULT_ICON_DP,
            fontSizeSp = p[KEY_FONT] ?: DEFAULT_FONT_SP,
            showIcons = p[KEY_SHOW_ICONS] ?: true,
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

    suspend fun resetAll(defaultEntries: List<StoredEntry>) {
        context.homeDataStore.edit {
            it[KEY_ENTRIES] = serialize(defaultEntries.take(MAX_APPS))
            it[KEY_INIT] = true
            it[KEY_ICON] = DEFAULT_ICON_DP
            it[KEY_FONT] = DEFAULT_FONT_SP
            it[KEY_SHOW_ICONS] = true
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
