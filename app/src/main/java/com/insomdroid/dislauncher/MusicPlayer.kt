package com.insomdroid.dislauncher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

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
    var positionTick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(playing, controller, metadata) {
        while (playing && controller != null) {
            positionTick = SystemClock.elapsedRealtime()
            delay(1000L)
        }
        if (!playing) positionTick = SystemClock.elapsedRealtime()
    }

    val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
    val basePosition = playback?.position?.coerceAtLeast(0L) ?: 0L
    val lastPositionUpdate = playback?.lastPositionUpdateTime ?: positionTick
    val position = (
        basePosition + if (playing) {
            ((positionTick - lastPositionUpdate).coerceAtLeast(0L) *
                (playback?.playbackSpeed ?: 1f)).toLong()
        } else 0L
    ).coerceIn(0L, duration)
    val progress = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val artwork = remember(metadata) {
        metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }
    val fallbackBackground = MaterialTheme.colorScheme.surfaceVariant
    val foreground = MaterialTheme.colorScheme.onBackground
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)

    val title = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        ?: metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        ?: "Nothing playing"
    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        ?: if (metadata == null) "Tap to open your music app" else "Unknown artist"
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(fallbackBackground)
                    .clickable(onClick = onOpenMusicApp),
                contentAlignment = Alignment.Center
            ) {
                if (artwork != null) {
                    Image(
                        bitmap = artwork.asImageBitmap(),
                        contentDescription = "Album art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("♪", color = foreground, fontSize = 30.sp, fontWeight = FontWeight.Light)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    title,
                    color = foreground,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenMusicApp)
                )
                Text(
                    artist,
                    color = secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "|◀",
                        color = foreground,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            if (controller != null) runCatching { controller.transportControls.skipToPrevious() }
                            else onOpenMusicApp()
                        }.padding(vertical = 2.dp, horizontal = 2.dp)
                    )
                    Text(
                        if (playing) "Ⅱ" else "▶",
                        color = foreground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            if (controller != null && metadata != null) {
                                runCatching {
                                    if (playing) controller.transportControls.pause()
                                    else controller.transportControls.play()
                                }
                            } else onOpenMusicApp()
                        }.padding(vertical = 1.dp, horizontal = 2.dp)
                    )
                    Text(
                        "▶|",
                        color = foreground,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            if (controller != null) runCatching { controller.transportControls.skipToNext() }
                            else onOpenMusicApp()
                        }.padding(vertical = 2.dp, horizontal = 2.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatMediaTime(position), color = secondary, fontSize = 10.sp, lineHeight = 12.sp)
            Canvas(modifier = Modifier.weight(1f).height(3.dp)) {
                drawRoundRect(
                    color = track,
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(size.height / 2f)
                )
                if (progress > 0f) {
                    drawRoundRect(
                        color = foreground,
                        topLeft = Offset.Zero,
                        size = Size(size.width * progress, size.height),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
            }
            Text(formatMediaTime(duration), color = secondary, fontSize = 10.sp, lineHeight = 12.sp)
        }
    }
}

private fun formatMediaTime(positionMs: Long): String {
    if (positionMs <= 0L) return "--:--"
    val totalSeconds = positionMs / 1000L
    return (totalSeconds / 60L).toString() + ":" + (totalSeconds % 60L).toString().padStart(2, '0')
}

internal class DislauncherNotificationListener : android.service.notification.NotificationListenerService()
