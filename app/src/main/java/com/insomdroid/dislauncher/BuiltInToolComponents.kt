package com.insomdroid.dislauncher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared launcher shape scale. Use pill only for intentionally capsule-shaped
 * controls such as the app-drawer search field and compact filter chips.
 */
internal object LauncherShapeTokens {
    val extraSmall = 4.dp
    val small = 6.dp
    val medium = 8.dp
    val large = 12.dp
    val extraLarge = 16.dp
    val pill = 24.dp
    val dialog = 28.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BuiltInToolHeader(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background
    )
) {
    TopAppBar(
        modifier = modifier,
        title = title,
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
        actions = actions,
        colors = colors
    )
}

/**
 * Shared search-field foundation. Outlined mode is used by built-in tools;
 * pill mode preserves the app drawer's distinct capsule treatment.
 */
@Composable
internal fun BuiltInToolSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    pill: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    placeholderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    pillBorderColor: Color? = null,
    fieldModifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
    fontSize: TextUnit = 16.sp
) {
    if (!pill) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            singleLine = true,
            shape = RoundedCornerShape(LauncherShapeTokens.small),
            placeholder = {
                Text(placeholder, color = placeholderColor)
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            textStyle = TextStyle(color = textColor, fontFamily = fontFamily, fontSize = fontSize)
        )
    } else {
        Row(
            modifier = modifier
                .height(46.dp)
                .clip(RoundedCornerShape(LauncherShapeTokens.pill))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.01f))
                .border(
                    BorderStroke(
                        1.dp,
                        pillBorderColor ?: if (value.isNotEmpty()) textColor.copy(alpha = 0.62f)
                        else placeholderColor.copy(alpha = 0.38f)
                    ),
                    RoundedCornerShape(LauncherShapeTokens.pill)
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) leadingIcon()
            Spacer(Modifier.size(if (leadingIcon != null) 10.dp else 0.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = placeholderColor,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = fontSize
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = fieldModifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = textColor,
                        fontFamily = fontFamily,
                        fontSize = fontSize
                    ),
                    cursorBrush = SolidColor(textColor)
                )
            }
            if (trailingIcon != null) trailingIcon()
        }
    }
}

/** Shared empty state: a centred title with an optional message, used by every built-in tool. */
@Composable
internal fun BuiltInEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    titleColor: Color = Color.Unspecified,
    icon: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BuiltInToolDesign.emptyStateHorizontalInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.section)
    ) {
        icon?.invoke()
        Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor, textAlign = TextAlign.Center)
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Shared shell for every built-in tool: the header sits in the top bar, and the content is capped at
 * [BuiltInToolDesign.contentMaxWidth], centred, with one horizontal margin rule. Content runs in a
 * [BoxScope], so overlays such as FABs align to the bottom end of the same column.
 */
@Composable
internal fun BuiltInToolScaffold(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable BoxScope.() -> Unit
) {
    Scaffold(
        modifier = modifier,
        containerColor = containerColor,
        topBar = header
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            Box(
                Modifier
                    .widthIn(max = BuiltInToolDesign.contentMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .padding(
                        horizontal = builtInToolContentMargin(maxWidth),
                        vertical = BuiltInToolDesign.contentVerticalInset
                    ),
                content = content
            )
        }
    }
}
