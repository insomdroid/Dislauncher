package com.insomdroid.dislauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun MusicPlayerWidget(
    modifier: Modifier = Modifier,
    onOpenMusicApp: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val component = remember(context) { ComponentName(context, DislauncherNotificationListener::class.java) }
    val manager = remember(context) {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val controllerState = remember { mutableStateOf<MediaController?>(null) }
    val metadataState = remember { mutableStateOf<MediaMetadata?>(null) }
    val playbackState = remember { mutableStateOf<PlaybackState?>(null) }
    val accessState = remember { mutableStateOf(false) }
    val callback = remember {
        object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                metadataState.value = metadata
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                playbackState.value = state
            }

            override fun onSessionDestroyed() {
                metadataState.value = null
                playbackState.value = null
            }
        }
    }

    DisposableEffect(lifecycleOwner, manager, component) {
        var listening = false
        fun useSessions(sessions: List<MediaController>?) {
            val candidates = sessions.orEmpty()
            val next = candidates.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING && it.metadata != null
            } ?: candidates.firstOrNull { it.metadata != null }
            val old = controllerState.value
            if (old?.sessionToken != next?.sessionToken) {
                old?.unregisterCallback(callback)
                controllerState.value = next
                next?.registerCallback(callback, handler)
            }
            metadataState.value = next?.metadata
            playbackState.value = next?.playbackState
        }

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
            useSessions(sessions)
        }

        fun refreshSessions() {
            try {
                val sessions = manager.getActiveSessions(component)
                accessState.value = true
                if (!listening) {
                    manager.addOnActiveSessionsChangedListener(listener, component, handler)
                    listening = true
                }
                useSessions(sessions)
            } catch (_: SecurityException) {
                accessState.value = false
                useSessions(emptyList())
            } catch (_: RuntimeException) {
                accessState.value = false
                useSessions(emptyList())
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshSessions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshSessions()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (listening) runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
            controllerState.value?.unregisterCallback(callback)
            controllerState.value = null
        }
    }

    val controller = controllerState.value
    val metadata = metadataState.value
    val playing = playbackState.value?.state == PlaybackState.STATE_PLAYING
    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        ?.takeIf { it.isNotBlank() }
        ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?.takeIf { it.isNotBlank() }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onOpenMusicApp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "♫",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    title ?: "Music player",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onOpenMusicApp)
                )
                Text(
                    artist ?: when {
                        !accessState.value -> "Allow media access to show playback"
                        controller == null -> "Nothing playing · tap the note to open music"
                        else -> "Ready to play"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (controller != null && metadata != null) {
                        TextButton(onClick = {
                            runCatching { controller.transportControls.skipToPrevious() }
                        }) { Text("|◀") }
                        TextButton(onClick = {
                            runCatching {
                                if (playing) controller.transportControls.pause()
                                else controller.transportControls.play()
                            }
                        }) { Text(if (playing) "Ⅱ" else "▶", fontWeight = FontWeight.SemiBold) }
                        TextButton(onClick = {
                            runCatching { controller.transportControls.skipToNext() }
                        }) { Text("▶|") }
                    } else if (!accessState.value) {
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }) { Text("Enable access") }
                    } else {
                        TextButton(onClick = onOpenMusicApp) { Text("Open player") }
                    }
                }
            }
        }
    }
}

internal class DislauncherNotificationListener : android.service.notification.NotificationListenerService()
