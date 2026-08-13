package com.mylauncher.data

/**
 * 首启默认应用:按用户期望顺序(电话、短信、相机、浏览器、图库、设置、微信、Kimi、DeepSeek)。
 * 先按包名候选精确匹配,未命中再用 label 关键词兜底;仍不足时用其余非系统应用补足。
 */
object DefaultApps {

    const val DEFAULT_COUNT = 9

    private data class Slot(val packages: List<String>, val keywords: List<String>)

    private val SLOTS = listOf(
        Slot(
            packages = listOf("com.android.dialer", "com.google.android.dialer", "com.vivo.dialer", "com.oneplus.dialer", "com.android.contacts"),
            keywords = listOf("电话", "拨号"),
        ),
        Slot(
            packages = listOf("com.android.mms", "com.android.messaging", "com.google.android.apps.messaging", "com.vivo.mms"),
            keywords = listOf("短信", "信息", "消息"),
        ),
        Slot(
            packages = listOf("com.android.camera", "com.android.camera2", "com.vivo.camera", "com.google.android.GoogleCamera", "com.coloros.camera"),
            keywords = listOf("相机", "拍照", "Camera"),
        ),
        Slot(
            packages = listOf("com.android.chrome", "com.android.browser", "com.vivo.browser", "com.heytap.browser", "com.microsoft.emmx", "org.mozilla.firefox"),
            keywords = listOf("浏览器", "Chrome", "Edge", "Firefox"),
        ),
        Slot(
            packages = listOf("com.android.gallery3d", "com.vivo.gallery", "com.google.android.apps.photos", "com.coloros.gallery"),
            keywords = listOf("图库", "相册", "照片", "Photos", "Gallery"),
        ),
        Slot(
            packages = listOf("com.android.settings"),
            keywords = listOf("设置", "设定", "Settings"),
        ),
        Slot(
            packages = listOf("com.tencent.mm"),
            keywords = listOf("微信", "WeChat"),
        ),
        Slot(
            packages = listOf("com.moonshot.kimi"),
            keywords = listOf("Kimi"),
        ),
        Slot(
            packages = listOf("com.deepseek.chat"),
            keywords = listOf("DeepSeek"),
        ),
    )

    /** 从已排序的应用列表中选出默认常用应用(保持 SLOTS 顺序,逐槽位先包名后关键词)。 */
    fun pick(apps: List<AppEntry>): List<AppEntry> {
        val picked = mutableListOf<AppEntry>()
        fun contains(e: AppEntry) = picked.any { it.component == e.component }
        fun takeFirst(pred: (AppEntry) -> Boolean) {
            apps.firstOrNull { pred(it) && !contains(it) }?.let { picked.add(it) }
        }

        for (slot in SLOTS) {
            if (picked.size >= DEFAULT_COUNT) break
            val before = picked.size
            takeFirst { it.packageName in slot.packages }
            if (picked.size == before) {
                // 包名未命中,用 label 关键词兜底
                takeFirst { a -> slot.keywords.any { a.label.contains(it, ignoreCase = true) } }
            }
        }
        // 不足时用剩余的非系统应用补足(避免默认塞入系统应用)
        for (a in apps) {
            if (picked.size >= DEFAULT_COUNT) break
            if (!a.isSystem) takeFirst { it.component == a.component }
        }
        return picked
    }
}
