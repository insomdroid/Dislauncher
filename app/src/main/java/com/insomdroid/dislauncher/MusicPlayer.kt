package com.insomdroid.dislauncher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val component = remember(context) {
        ComponentName(context, DislauncherNotificationListener::class.java)
    }
    val manager = remember(context) {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val controllerState = remember { mutableStateOf<MediaController?>(null) }
    val metadataState = remember { mutableStateOf<MediaMetadata?>(null) }
    val playbackState = remember { mutableStateOf<PlaybackState?>(null) }
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
                controllerState.value = null
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
                if (!listening) {
                    manager.addOnActiveSessionsChangedListener(listener, component, handler)
                    listening = true
                }
                useSessions(sessions)
            } catch (_: SecurityException) {
                useSessions(emptyList())
            } catch (_: RuntimeException) {
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
    val playback = playbackState.value
    val playing = playback?.state == PlaybackState.STATE_PLAYING
    val foreground = MaterialTheme.colorScheme.onBackground
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val title = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        ?: metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        ?: "Nothing playing"
    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        ?: if (metadata == null) "Tap to open your music app" else "Unknown artist"

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f).clickable(onClick = onOpenMusicApp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title,
                color = foreground,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                artist,
                color = secondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier.size(32.dp).clickable {
                    if (controller != null) {
                        runCatching { controller.transportControls.skipToPrevious() }
                    } else {
                        onOpenMusicApp()
                    }
                },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = foreground, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Light)
            }
            Box(
                modifier = Modifier.size(32.dp).clickable {
                    if (controller != null) {
                        runCatching {
                            if (playing) controller.transportControls.pause()
                            else controller.transportControls.play()
                        }
                    } else {
                        onOpenMusicApp()
                    }
                },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (playing) "Ⅱ" else "▶",
                    color = foreground,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Box(
                modifier = Modifier.size(32.dp).clickable {
                    if (controller != null) {
                        runCatching { controller.transportControls.skipToNext() }
                    } else {
                        onOpenMusicApp()
                    }
                },
                contentAlignment = Alignment.Center
            ) {
                Text("›", color = foreground, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Light)
            }
        }
    }
}

internal class DislauncherNotificationListener : android.service.notification.NotificationListenerService()
