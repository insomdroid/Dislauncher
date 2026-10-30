package com.insomdroid.dislauncher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun WelcomeOnboardingScreen(
    preferences: LauncherPreferences,
    onPreferencesChange: (LauncherPreferences) -> Unit,
    apps: List<LauncherApp>,
    favorites: Set<String>,
    onFinish: (selectedPackages: Set<String>) -> Unit
) {
    val context = LocalContext.current
    val isIndonesian = context.resources.configuration.locales[0]?.language == "id"
    fun tx(english: String, indonesian: String): String =
        if (isIndonesian) indonesian else english
    var page by remember { mutableStateOf(0) }
    var selectedFavorites by remember { mutableStateOf(favorites.toSet()) }
    val pageCount = 3

    fun skipOnboarding() {
        onFinish(emptySet())
    }

    fun openSystemSettings(action: String) {
        runCatching { context.startActivity(Intent(action)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.dislauncher_icon_foreground),
                    contentDescription = "Dislauncher logo",
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "DISLAUNCHER",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )
                Text(
                    tx("A calmer Android Home", "Layar utama Android yang lebih tenang"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = ::skipOnboarding) { Text(tx("Skip setup", "Lewati pengaturan")) }
        }

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = page,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    (slideInHorizontally(initialOffsetX = { it / 6 }) + fadeIn())
                        .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 6 }) + fadeOut())
                },
                label = "dislauncher-onboarding-pages"
            ) { currentPage ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (currentPage) {
                        2 -> {
                            Text(
                                tx("Choose your six favourites", "Pilih enam aplikasi favorit"),
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                tx("Choose up to six apps to appear on Home. Only your selected favourites will appear.", "Pilih hingga enam aplikasi untuk ditampilkan di Home. Hanya aplikasi pilihan Anda yang akan muncul."),
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    tx("Selected", "Dipilih"),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    "${selectedFavorites.size.coerceAtMost(6)}/6",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 310.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(apps.sortedBy { it.label.lowercase() }, key = { it.packageName }) { app ->
                                    val checked = app.packageName in selectedFavorites
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                selectedFavorites = when {
                                                    checked -> selectedFavorites - app.packageName
                                                    selectedFavorites.size < 6 -> selectedFavorites + app.packageName
                                                    else -> selectedFavorites
                                                }
                                            }
                                            .padding(horizontal = 4.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppIcon(app, "system", size = 32.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            app.label,
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { enabled ->
                                                selectedFavorites = when {
                                                    !enabled -> selectedFavorites - app.packageName
                                                    selectedFavorites.size < 6 -> selectedFavorites + app.packageName
                                                    else -> selectedFavorites
                                                }
                                            }
                                        )
                                    }
                                }
                                if (apps.isEmpty()) {
                                    item {
                                        Text(
                                            tx("Loading apps…", "Memuat aplikasi…"),
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            val illustration = when (currentPage) {
                                0 -> "D."
                                1 -> "09:41"
                                else -> "A  B  C"
                            }
                            Box(
                                modifier = Modifier
                                    .width(208.dp)
                                    .height(112.dp)
                                    .clip(MaterialTheme.shapes.extraLarge)
                                    .background(if (currentPage == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentPage == 0) {
                                    Image(
                                        painter = painterResource(R.drawable.dislauncher_icon_foreground),
                                        contentDescription = "Dislauncher app icon",
                                        modifier = Modifier.size(96.dp)
                                    )
                                } else {
                                    Text(
                                        illustration,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = when (currentPage) {
                                            1 -> 37.sp
                                            3 -> 24.sp
                                            else -> 62.sp
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.height(22.dp))
                            Text(
                                when (currentPage) {
                                    0 -> tx("Less noise. More intention.", "Lebih sedikit gangguan. Lebih fokus.")
                                    2 -> "A useful Home screen."
                                    3 -> "Find apps without the clutter."
                                    else -> tx("Make it feel like yours.", "Sesuaikan dengan gaya Anda.")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 40.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                when (currentPage) {
                                    0 -> tx("A text-first launcher for the things you check every day—not another feed to scroll.", "Launcher berbasis teks untuk hal yang Anda perlukan setiap hari, tanpa feed yang terus digulir.")
                                    1 -> tx("Use Android's wallpaper colors, or choose system, light, dark, or black.", "Gunakan warna wallpaper Android atau pilih tema sistem, terang, gelap, maupun AMOLED.")
                                    else -> "A text-first launcher for the things you check every day—not another feed to scroll."
                                },
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp
                            )
                            if (currentPage == 1) {
                                Spacer(Modifier.height(18.dp))
                                Text(
                                    tx("THEME", "TEMA"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("system" to tx("System", "Sistem"), "light" to tx("Light", "Terang"), "dark" to tx("Dark", "Gelap"), "black" to "AMOLED").forEach { (value, label) ->
                                        FilterChip(
                                            selected = preferences.themeMode == value,
                                            onClick = { onPreferencesChange(preferences.copy(themeMode = value)) },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { openSystemSettings(Settings.ACTION_HOME_SETTINGS) }) {
                                    Text(tx("Set as default Home", "Jadikan Home default"))
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(if (index == page) 22.dp else 7.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                if (index == page) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page > 0) {
                    TextButton(onClick = { page-- }) { Text(tx("Back", "Kembali")) }
                } else {
                    Text(
                        tx("STEP ${page + 1} OF $pageCount", "LANGKAH ${page + 1} DARI $pageCount"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (page < pageCount - 1) {
                            page++
                        } else {
                            onFinish(selectedFavorites)
                        }
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text(if (page == pageCount - 1) tx("Finish setup", "Selesai") else tx("Continue", "Lanjutkan"))
                }
            }
        }
    }

}
