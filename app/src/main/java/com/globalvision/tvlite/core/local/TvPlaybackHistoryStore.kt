package com.globalvision.tvlite.core.local

import android.content.Context
import com.globalvision.tvlite.core.model.TvPlaybackHistoryEntry
import org.json.JSONArray
import org.json.JSONObject

class TvPlaybackHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<TvPlaybackHistoryEntry> {
        val raw = preferences.getString(KEY_HISTORY, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(item.toHistoryEntry())
                }
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun get(movieId: String): TvPlaybackHistoryEntry? {
        if (movieId.isBlank()) return null
        return getAll().firstOrNull { it.movieId == movieId }
    }

    fun upsert(entry: TvPlaybackHistoryEntry) {
        if (entry.movieId.isBlank()) return

        val merged = buildList {
            add(entry.copy(updatedAt = entry.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()))
            addAll(getAll().filterNot { it.movieId == entry.movieId })
        }
            .sortedByDescending { it.updatedAt }
            .take(MAX_HISTORY_SIZE)

        val serialized = JSONArray().apply {
            merged.forEach { put(it.toJson()) }
        }
        preferences.edit().putString(KEY_HISTORY, serialized.toString()).commit()
    }

    private fun JSONObject.toHistoryEntry() = TvPlaybackHistoryEntry(
        movieId = optString("movieId"),
        title = optString("title"),
        posterUrl = optString("posterUrl"),
        year = optString("year"),
        category = optString("category"),
        remark = optString("remark"),
        sourceIndex = optInt("sourceIndex"),
        sourceName = optString("sourceName"),
        episodeIndex = optInt("episodeIndex"),
        episodeName = optString("episodeName"),
        positionMs = optLong("positionMs"),
        updatedAt = optLong("updatedAt"),
    )

    private fun TvPlaybackHistoryEntry.toJson() = JSONObject().apply {
        put("movieId", movieId)
        put("title", title)
        put("posterUrl", posterUrl)
        put("year", year)
        put("category", category)
        put("remark", remark)
        put("sourceIndex", sourceIndex)
        put("sourceName", sourceName)
        put("episodeIndex", episodeIndex)
        put("episodeName", episodeName)
        put("positionMs", positionMs)
        put("updatedAt", updatedAt)
    }

    private companion object {
        const val PREFS_NAME = "tv_playback_history"
        const val KEY_HISTORY = "items"
        const val MAX_HISTORY_SIZE = 30
    }
}
