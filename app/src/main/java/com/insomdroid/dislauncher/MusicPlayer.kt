package com.insomdroid.dislauncher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.StatusBarNotification
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

        fun stopListening() {
            if (listening) {
                runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
                listening = false
            }
            controllerState.value?.unregisterCallback(callback)
            controllerState.value = null
            metadataState.value = null
            playbackState.value = null
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
            when (event) {
                Lifecycle.Event.ON_RESUME -> refreshSessions()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> stopListening()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            refreshSessions()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopListening()
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
            modifier = Modifier.weight(1f).clickable {
                val openedActivePlayer = controller?.packageName?.let { packageName ->
                    val launchIntent = runCatching {
                        context.packageManager.getLaunchIntentForPackage(packageName)
                    }.getOrNull()
                    launchIntent?.let { intent ->
                        runCatching {
                            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.isSuccess
                    } ?: false
                } ?: false
                if (!openedActivePlayer) onOpenMusicApp()
            },
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
                Icon(
                    imageVector = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = foreground,
                    modifier = Modifier.size(20.dp)
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

internal object NotificationDotState {
    var notificationCounts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    val activePackages: Set<String>
        get() = notificationCounts.keys

    @Volatile
    private var listener: DislauncherNotificationListener? = null

    fun attach(service: DislauncherNotificationListener) {
        listener = service
        service.refreshDotPackages(reloadActiveNotifications = true)
    }

    fun detach(service: DislauncherNotificationListener) {
        if (listener === service) {
            listener = null
            notificationCounts = emptyMap()
        }
    }

    fun refresh() {
        listener?.refreshDotPackages()
    }

    fun countFor(packageName: String): Int = notificationCounts[packageName] ?: 0

    internal fun update(counts: Map<String, Int>) {
        notificationCounts = counts
    }
}

private data class TrackedDotNotification(
    val packageName: String,
    val ongoing: Boolean
)

private fun StatusBarNotification.toTrackedDotNotification(): TrackedDotNotification =
    TrackedDotNotification(
        packageName = packageName,
        ongoing = isOngoing || (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
    )

internal class DislauncherNotificationListener : android.service.notification.NotificationListenerService() {
    private val trackedNotifications = linkedMapOf<String, TrackedDotNotification>()
    private val dotRefreshHandler = Handler(Looper.getMainLooper())
    private val dotRefreshLock = Any()
    private var dotRefreshPending = false

    private val dotRefreshRunnable = Runnable {
        synchronized(dotRefreshLock) {
            dotRefreshPending = false
        }
        refreshDotPackages()
    }

    /**
     * Coalesces notification bursts into at most one dot-count recalculation per
     * 300 ms window, while keeping the latest tracked notification state.
     */
    private fun scheduleDotRefresh() {
        synchronized(dotRefreshLock) {
            if (dotRefreshPending) return
            dotRefreshPending = true
            dotRefreshHandler.postDelayed(dotRefreshRunnable, DOT_REFRESH_COALESCE_MS)
        }
    }

    private fun cancelScheduledDotRefresh() {
        synchronized(dotRefreshLock) {
            dotRefreshHandler.removeCallbacks(dotRefreshRunnable)
            dotRefreshPending = false
        }
    }

    private companion object {
        const val DOT_REFRESH_COALESCE_MS = 300L
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationDotState.attach(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        synchronized(trackedNotifications) {
            trackedNotifications[sbn.key] = sbn.toTrackedDotNotification()
        }
        scheduleDotRefresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        synchronized(trackedNotifications) {
            trackedNotifications.remove(sbn.key)
        }
        scheduleDotRefresh()
    }

    override fun onListenerDisconnected() {
        cancelScheduledDotRefresh()
        synchronized(trackedNotifications) { trackedNotifications.clear() }
        NotificationDotState.detach(this)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        cancelScheduledDotRefresh()
        synchronized(trackedNotifications) { trackedNotifications.clear() }
        NotificationDotState.detach(this)
        super.onDestroy()
    }

    internal fun refreshDotPackages(reloadActiveNotifications: Boolean = false) {
        if (reloadActiveNotifications) {
            val current = runCatching { activeNotifications.orEmpty().toList() }.getOrDefault(emptyList())
            synchronized(trackedNotifications) {
                trackedNotifications.clear()
                current.forEach { sbn ->
                    trackedNotifications[sbn.key] = sbn.toTrackedDotNotification()
                }
            }
        }

        val prefs = getSharedPreferences("dislauncher_preferences", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notification_dots_enabled", false)) {
            NotificationDotState.update(emptyMap())
            return
        }

        val selectedPackages = prefs.getStringSet("notification_dot_packages", emptySet()).orEmpty()
        if (selectedPackages.isEmpty()) {
            NotificationDotState.update(emptyMap())
            return
        }

        val activeCounts = synchronized(trackedNotifications) {
            trackedNotifications.values.asSequence()
                .filterNot { it.ongoing }
                .map { it.packageName }
                .filter { it in selectedPackages }
                .groupingBy { it }
                .eachCount()
        }
        NotificationDotState.update(activeCounts)
    }
}
