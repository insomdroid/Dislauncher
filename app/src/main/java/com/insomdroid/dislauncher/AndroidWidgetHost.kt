package com.insomdroid.dislauncher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt

internal const val DISLAUNCHER_APP_WIDGET_HOST_ID = 0xD15A

@Composable
internal fun rememberDislauncherAppWidgetHost(): AppWidgetHost {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val host = remember(context) {
        AppWidgetHost(context, DISLAUNCHER_APP_WIDGET_HOST_ID)
    }

    DisposableEffect(host, lifecycleOwner) {
        var listening = false
        fun startListeningSafely() {
            if (!listening) {
                runCatching { host.startListening() }
                listening = true
            }
        }
        fun stopListeningSafely() {
            if (listening) {
                runCatching { host.stopListening() }
                listening = false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startListeningSafely()
                Lifecycle.Event.ON_STOP -> stopListeningSafely()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            startListeningSafely()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopListeningSafely()
        }
    }

    return host
}

@Composable
internal fun AndroidAppWidgetContent(
    context: Context,
    host: AppWidgetHost,
    manager: AppWidgetManager,
    item: WidgetPageItem,
    cellWidthDp: Float,
    cellHeightDp: Float,
    modifier: Modifier = Modifier,
    onHostViewReady: (AppWidgetHostView) -> Unit = {}
) {
    val providerInfo = remember(item.appWidgetId) {
        runCatching { manager.getAppWidgetInfo(item.appWidgetId) }.getOrNull()
    }

    if (providerInfo == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Widget unavailable",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        return
    }

    val minWidth = (cellWidthDp * item.width).roundToInt().coerceAtLeast(1)
    val minHeight = (cellHeightDp * item.height).roundToInt().coerceAtLeast(1)
    LaunchedEffect(item.appWidgetId, item.width, item.height, cellWidthDp, cellHeightDp) {
        runCatching {
            manager.updateAppWidgetOptions(
                item.appWidgetId,
                Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minWidth)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minHeight)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight)
                }
            )
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            host.createView(context, item.appWidgetId, providerInfo).also { view ->
                view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                onHostViewReady(view)
            }
        },
        update = { view: AppWidgetHostView ->
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            onHostViewReady(view)
            // The host owns the view lifecycle; the system delivers subsequent widget updates.
            if (view.appWidgetId != item.appWidgetId) {
                view.setAppWidget(item.appWidgetId, providerInfo)
            }
        }
    )
}

internal fun AppWidgetProviderInfo.supportsHorizontalResize(): Boolean =
    resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0

internal fun AppWidgetProviderInfo.supportsVerticalResize(): Boolean =
    resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
