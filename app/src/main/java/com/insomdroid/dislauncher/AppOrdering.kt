package com.insomdroid.dislauncher

import android.content.SharedPreferences
import org.json.JSONArray

internal fun readFavoriteOrder(prefs: SharedPreferences, favorites: Set<String>): List<String> {
    val saved = prefs.getString("favorite_order_json", null)
    val previous = if (saved.isNullOrBlank()) emptyList() else runCatching {
        val array = JSONArray(saved)
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.getOrElse { emptyList() }
    return (previous.filter { it in favorites } + favorites.filter { it !in previous }.sorted()).distinct()
}


internal fun readHomeItemOrder(prefs: SharedPreferences, favorites: Set<String>): List<String> {
    val available = readFavoriteOrder(prefs, favorites)
    val saved = prefs.getString("home_item_order_json", null)?.let { encoded ->
        runCatching {
            val array = JSONArray(encoded)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
    }.orEmpty()
    return (saved.filter { it in available } + available.filter { it !in saved }).distinct()
}

internal fun saveHomeItemOrder(prefs: SharedPreferences, order: List<String>) {
    val array = JSONArray()
    order.forEach(array::put)
    prefs.edit().putString("home_item_order_json", array.toString()).apply()
}
