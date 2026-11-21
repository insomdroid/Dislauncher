package com.insomdroid.dislauncher

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class ChangelogSection(
    val title: String,
    val items: List<String>
)

private data class ChangelogEntry(
    val version: String,
    val date: String,
    val sections: List<ChangelogSection>
)

private fun readChangelog(context: Context): List<ChangelogEntry> {
    val source = context.assets.open("changelog.md").bufferedReader().use { it.readText() }
    return source.split(Regex("(?m)^##\\s+")).drop(1).mapNotNull { block ->
        val lines = block.lines()
        val header = lines.firstOrNull().orEmpty().split("|").map(String::trim)
        if (header.size < 2) return@mapNotNull null
        val version = header[0].removePrefix("v")
        val date = header[1]
        var sectionTitle = ""
        val sections = mutableListOf<Pair<String, MutableList<String>>>()
        lines.drop(1).forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#### ") -> {
                    sectionTitle = line.removePrefix("#### ").trim()
                    sections += sectionTitle to mutableListOf()
                }
                line.startsWith("- ") -> {
                    if (sections.isEmpty()) sections += (sectionTitle.ifBlank { "Changes" }) to mutableListOf()
                    sections.last().second += line.removePrefix("- ")
                        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                }
            }
        }
        ChangelogEntry(
            version = version,
            date = date,
            sections = sections.map { ChangelogSection(it.first, it.second.toList()) }
                .filter { it.items.isNotEmpty() }
        )
    }
}

@Composable
internal fun ChangelogScreen() {
    val context = LocalContext.current
    val entries = remember(context) { runCatching { readChangelog(context).take(2) }.getOrDefault(emptyList()) }

    if (entries.isEmpty()) {
        Text(
            "Changelog is unavailable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, entry ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "v${entry.version}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        entry.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                entry.sections.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        section.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("•", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    item,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
            if (index < entries.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
