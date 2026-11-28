package com.insomdroid.dislauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class PinWidgetRequestActivity : ComponentActivity() {
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    private var pinRequest: LauncherApps.PinItemRequest? = null
    private var providerInfo: android.appwidget.AppWidgetProviderInfo? = null
    private var providerLabel: String = "Widget"
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingItem: WidgetPageItem? = null

    private var isProcessing by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val returnedId = result.data?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            pendingWidgetId
        ) ?: pendingWidgetId

        if (result.resultCode == Activity.RESULT_OK &&
            returnedId != AppWidgetManager.INVALID_APPWIDGET_ID
        ) {
            pendingWidgetId = returnedId
            acceptPinRequest()
        } else {
            deletePendingWidget()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetHost = AppWidgetHost(this, DISLAUNCHER_APP_WIDGET_HOST_ID)
        appWidgetManager = AppWidgetManager.getInstance(this)
        pendingWidgetId = savedInstanceState?.getInt(
            STATE_PENDING_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val launcherApps = getSystemService(LauncherApps::class.java)
        val request = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull()
        if (intent.action != LauncherApps.ACTION_CONFIRM_PIN_APPWIDGET ||
            request == null ||
            request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET
        ) {
            finish()
            return
        }

        val info = runCatching { request.getAppWidgetProviderInfo(this) }.getOrNull()
        if (info == null) {
            finish()
            return
        }

        pinRequest = request
        providerInfo = info
        providerLabel = runCatching { info.loadLabel(packageManager).toString() }
            .getOrDefault(info.provider.packageName)

        setContent {
            MaterialTheme {
                AlertDialog(
                    onDismissRequest = { cancelRequest() },
                    title = { Text("Add widget?") },
                    text = {
                        Column {
                            Text(providerLabel)
                            Text(
                                "Add this widget to the SamiUI widget page?",
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            errorMessage?.let { message ->
                                Text(
                                    message,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { confirmRequest() },
                            enabled = !isProcessing
                        ) {
                            Text(if (isProcessing) "Adding…" else "Add widget")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { cancelRequest() },
                            enabled = !isProcessing
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }

    private fun confirmRequest() {
        val request = pinRequest ?: return
        val info = providerInfo ?: return
        if (isProcessing) return

        isProcessing = true
        errorMessage = null

        if (pendingWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            pendingWidgetId = appWidgetHost.allocateAppWidgetId()
        }

        val item = createPinnedWidgetPageItem(this, pendingWidgetId, info)
        if (item == null) {
            deletePendingWidget()
            isProcessing = false
            errorMessage = "There is not enough room for this widget on the widget page."
            return
        }
        pendingItem = item

        val options = Bundle().apply {
            putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
        }
        val bound = runCatching {
            appWidgetManager.bindAppWidgetIdIfAllowed(
                pendingWidgetId,
                info.provider,
                options
            )
        }.getOrDefault(false)

        if (bound) {
            acceptPinRequest()
        } else {
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
            runCatching {
                bindWidgetLauncher.launch(bindIntent)
            }.onFailure {
                deletePendingWidget()
                isProcessing = false
                errorMessage = "Android could not open the widget permission screen."
            }
        }
    }

    private fun acceptPinRequest() {
        val request = pinRequest
        val item = pendingItem
        val widgetId = pendingWidgetId
        if (request == null || item == null ||
            widgetId == AppWidgetManager.INVALID_APPWIDGET_ID
        ) {
            deletePendingWidget()
            finish()
            return
        }

        if (!savePinnedWidgetPageItem(this, item)) {
            deletePendingWidget()
            Toast.makeText(
                this,
                "The widget could not be placed because the widget page changed.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        val options = Bundle().apply {
            putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        val accepted = runCatching { request.accept(options) }.getOrDefault(false)
        if (!accepted) {
            saveWidgetPageItems(
                this,
                loadWidgetPageItems(this).filterNot { it.id == item.id }
            )
            deletePendingWidget()
            finish()
            return
        }

        getSharedPreferences(DISLAUNCHER_PREFERENCES_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean("show_widget_page", true)
            .apply()

        Toast.makeText(this, "Widget added to SamiUI.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun cancelRequest() {
        deletePendingWidget()
        finish()
    }

    private fun deletePendingWidget() {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            runCatching { appWidgetHost.deleteAppWidgetId(pendingWidgetId) }
        }
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingItem = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_PENDING_WIDGET_ID, pendingWidgetId)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val STATE_PENDING_WIDGET_ID = "pin_request_pending_widget_id"
    }
}
