package com.globalvision.tvlite.core.local

import android.content.Context
import org.json.JSONArray

class TvRecentSearchStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<String> {
        val raw = preferences.getString(KEY_ITEMS, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.optString(index).trim()
                    if (item.isNotBlank()) add(item)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun record(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isBlank()) return

        val merged = buildList {
            add(normalized)
            addAll(getAll().filterNot { it.equals(normalized, ignoreCase = true) })
        }.take(MAX_SIZE)

        val serialized = JSONArray().apply {
            merged.forEach { put(it) }
        }
        preferences.edit().putString(KEY_ITEMS, serialized.toString()).commit()
    }

    private companion object {
        const val PREFS_NAME = "tv_recent_searches"
        const val KEY_ITEMS = "items"
        const val MAX_SIZE = 20
    }
}
