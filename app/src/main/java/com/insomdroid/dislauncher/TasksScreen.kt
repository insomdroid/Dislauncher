package com.insomdroid.dislauncher

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class LauncherTask(val id: String, val name: String, val checked: Boolean = false)

internal fun readLauncherTasks(prefs: SharedPreferences): List<LauncherTask> {
    val encoded = prefs.getString("launcher_tasks_json", null)
    if (!encoded.isNullOrBlank()) {
        return runCatching {
            val array = JSONArray(encoded)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name").trim()
                if (name.isBlank()) return@mapNotNull null
                LauncherTask(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    checked = item.optBoolean("checked", false)
                )
            }
        }.getOrElse { emptyList() }
    }
    val legacy = prefs.getStringSet("home_tasks", emptySet()).orEmpty()
        .map(String::trim).filter(String::isNotBlank).sorted()
    val migrated = legacy.map { LauncherTask(UUID.randomUUID().toString(), it) }
    if (migrated.isNotEmpty()) saveLauncherTasks(prefs, migrated)
    return migrated
}

internal fun saveLauncherTasks(prefs: SharedPreferences, tasks: List<LauncherTask>) {
    val array = JSONArray()
    tasks.forEach { task ->
        array.put(JSONObject().put("id", task.id).put("name", task.name).put("checked", task.checked))
    }
    prefs.edit()
        .putString("launcher_tasks_json", array.toString())
        .putStringSet("home_tasks", tasks.map { it.name }.toSet())
        .apply()
}

internal fun readFavoriteOrder(prefs: SharedPreferences, favorites: Set<String>): List<String> {
    val saved = prefs.getString("favorite_order_json", null)
    val previous = if (saved.isNullOrBlank()) emptyList() else runCatching {
        val array = JSONArray(saved)
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.getOrElse { emptyList() }
    return (previous.filter { it in favorites } + favorites.filter { it !in previous }.sorted()).distinct()
}

internal fun saveFavoriteOrder(prefs: SharedPreferences, order: List<String>) {
    val array = JSONArray()
    order.forEach { array.put(it) }
    prefs.edit().putString("favorite_order_json", array.toString()).apply()
}

internal const val HOME_ITEM_TASKS = "builtin:tasks"
internal const val HOME_ITEM_CALLS = "builtin:calls"
internal const val HOME_ITEM_MUSIC = "builtin:music"

internal fun readHomeItemOrder(prefs: SharedPreferences, favorites: Set<String>): List<String> {
    val available = listOf(HOME_ITEM_TASKS, HOME_ITEM_CALLS, HOME_ITEM_MUSIC) + favorites.sorted()
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

@Composable
internal fun TasksScreen(tasks: List<LauncherTask>, onTasksChange: (List<LauncherTask>) -> Unit, onBack: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.onBackground
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = primary, fontSize = 28.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 18.dp))
            Text("Tasks", color = primary, fontSize = 18.sp)
        }
        Text("${tasks.size} TASKS", color = secondary, fontSize = 10.sp, letterSpacing = 1.sp)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f),
                singleLine = true, placeholder = { Text("Add a task", fontSize = 13.sp) },
                shape = RoundedCornerShape(3.dp)
            )
            Text("ADD", color = primary, fontSize = 12.sp, modifier = Modifier.clickable {
                val name = draft.trim()
                if (name.isNotEmpty()) {
                    onTasksChange(tasks + LauncherTask(UUID.randomUUID().toString(), name))
                    draft = ""
                }
            }.padding(7.dp))
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp)) {
            if (tasks.isEmpty()) item(key = "tasks-empty") {
                Text("No tasks yet.", color = secondary, fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 14.dp))
            }
            items(tasks, key = { it.id }) { task ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = task.checked,
                        onCheckedChange = { checked ->
                            onTasksChange(tasks.map { if (it.id == task.id) it.copy(checked = checked) else it })
                        }
                    )
                    Text(
                        task.name,
                        color = if (task.checked) secondary else primary,
                        fontSize = 15.sp,
                        textDecoration = if (task.checked) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).clickable {
                            onTasksChange(tasks.map { if (it.id == task.id) it.copy(checked = !it.checked) else it })
                        }.padding(vertical = 5.dp)
                    )
                    Text("×", color = secondary, fontSize = 20.sp,
                        modifier = Modifier.clickable { onTasksChange(tasks.filterNot { it.id == task.id }) }
                            .padding(start = 12.dp, end = 3.dp, top = 4.dp, bottom = 4.dp))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("${tasks.count { it.checked }} COMPLETED", color = secondary, fontSize = 9.sp)
            Text("CLEAR ALL", color = secondary, fontSize = 10.sp,
                modifier = Modifier.clickable { if (tasks.isNotEmpty()) confirmClear = true }.padding(6.dp))
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear all tasks?") },
        text = { Text("This removes every task from the list.") },
        confirmButton = {
            Text("CLEAR", color = primary,
                modifier = Modifier.clickable { onTasksChange(emptyList()); confirmClear = false }.padding(12.dp))
        },
        dismissButton = {
            Text("CANCEL", color = secondary,
                modifier = Modifier.clickable { confirmClear = false }.padding(12.dp))
        }
    )
}
