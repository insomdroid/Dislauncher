package com.insomdroid.dislauncher

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Debug-only preview entry points used by the standalone Android CLI preview workflow.
 * Keep these previews independent from device state, permissions, and network access.
 */
@Preview(name = "Progress line - light", showBackground = true, widthDp = 360)
@Composable
internal fun ProgressLineLightPreview() {
    MaterialTheme {
        ProgressLine(
            label = "Day in progress",
            percent = 68,
            primaryText = MaterialTheme.colorScheme.onBackground,
            secondaryText = MaterialTheme.colorScheme.onSurfaceVariant,
            fill = MaterialTheme.colorScheme.primary,
            track = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

@Preview(name = "Progress line - dark", showBackground = true, widthDp = 360)
@Composable
internal fun ProgressLineDarkPreview() {
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        ProgressLine(
            label = "Year in progress",
            percent = 83,
            primaryText = MaterialTheme.colorScheme.onBackground,
            secondaryText = MaterialTheme.colorScheme.onSurfaceVariant,
            fill = MaterialTheme.colorScheme.primary,
            track = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}
