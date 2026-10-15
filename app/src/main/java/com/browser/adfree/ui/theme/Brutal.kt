/*
 * Copyright (C) 2024-2026 Gibbly Gobbly <gibblygobbly@proton.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.browser.adfree.ui.theme

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Neo-brutalist building blocks: flat fills, 2dp ink outlines, hard offset
// shadows (no blur) and small corners. Pressing a raised element pushes it
// into its shadow.

val BrutalCorner = 6.dp
val BrutalShape = RoundedCornerShape(BrutalCorner)
val BrutalBorderWidth = 2.dp
val BrutalShadowOffset = 4.dp

/** Flat box with ink outline and an optional hard shadow offset down-right. */
fun Modifier.brutalBox(
    fill: Color,
    border: Color,
    shadow: Color? = null,
    shadowOffset: Dp = BrutalShadowOffset,
    shape: Shape = BrutalShape,
    borderWidth: Dp = BrutalBorderWidth
): Modifier = this
    .then(
        if (shadow != null && shadowOffset > 0.dp) {
            Modifier.drawBehind {
                val outline = shape.createOutline(size, layoutDirection, this)
                val px = shadowOffset.toPx()
                translate(px, px) { drawOutline(outline, shadow) }
            }
        } else Modifier
    )
    .background(fill, shape)
    .border(borderWidth, border, shape)

/** Raised, clickable box that sinks into its shadow while pressed. */
fun Modifier.brutalPressable(
    fill: Color,
    border: Color,
    shadow: Color,
    enabled: Boolean = true,
    shadowOffset: Dp = BrutalShadowOffset,
    shape: Shape = BrutalShape,
    role: Role = Role.Button,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    val travel by animateDpAsState(
        targetValue = if (pressed && enabled) shadowOffset else 0.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessHigh),
        label = "brutalPress"
    )
    this
        .offset(x = travel, y = travel)
        .brutalBox(
            fill = fill,
            border = border,
            shadow = if (enabled) shadow else null,
            shadowOffset = shadowOffset - travel,
            shape = shape
        )
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        )
        .alpha(if (enabled) 1f else 0.45f)
}

/**
 * Press feedback for flat (unboxed) targets: a square ink-tinted tile fades in
 * under the content and the content dips slightly, with a light haptic tick.
 */
fun Modifier.brutalTap(
    enabled: Boolean = true,
    role: Role = Role.Button,
    shape: Shape = BrutalShape,
    haptic: Boolean = true,
    pressScale: Float = 0.94f,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    val tint by animateColorAsState(
        targetValue = if (pressed && enabled) BorderSubtle.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(90),
        label = "tapTint"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "tapScale"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .background(tint, shape)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = {
                if (haptic) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
        )
}

@Composable
fun BrutalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    fill: Color = AccentCyan,
    contentColor: Color = OnAccent,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .brutalPressable(
                fill = fill,
                border = BorderSubtle,
                shadow = HardShadow,
                enabled = enabled,
                shadowOffset = 3.dp,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Secondary button: surface fill instead of accent. */
@Composable
fun BrutalOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) = BrutalButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    icon = icon,
    fill = SurfaceDark,
    contentColor = TextPrimary,
    enabled = enabled
)

/**
 * 48dp square touch target. [boxed] draws a raised square tile behind the
 * icon (used for page-bar back buttons and active states).
 */
@Composable
fun BrutalIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boxed: Boolean = false,
    fill: Color = SurfaceDark,
    tint: Color = TextPrimary,
    enabled: Boolean = true,
    iconSize: Dp = 22.dp
) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        if (boxed) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .brutalPressable(
                        fill = fill,
                        border = BorderSubtle,
                        shadow = HardShadow,
                        enabled = enabled,
                        shadowOffset = 3.dp,
                        onClick = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize - 2.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .brutalTap(enabled = enabled, onClick = onClick)
                    .alpha(if (enabled) 1f else 0.35f),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
            }
        }
    }
}

/** Rectangular switch: ink-outlined track, square thumb, accent fill when on. */
@Composable
fun BrutalSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val trackShape = RoundedCornerShape(4.dp)
    val view = LocalView.current
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
        label = "switchThumb"
    )
    val trackFill by animateColorAsState(
        targetValue = if (checked) AccentCyan else SurfaceCard,
        animationSpec = tween(140),
        label = "switchTrack"
    )
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 48.dp)
            .then(
                if (onCheckedChange != null) Modifier.toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onCheckedChange(it)
                    }
                ) else Modifier
            )
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 24.dp)
                .brutalBox(
                    fill = trackFill,
                    border = BorderSubtle,
                    shape = trackShape
                )
                .padding(4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .size(16.dp)
                    .background(BorderSubtle, RoundedCornerShape(2.dp))
            )
        }
    }
}

/** Full-page top bar: back tile, title, optional trailing actions, ink rule below. */
@Composable
fun BrutalPageBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrutalIconButton(
                icon = Icons.Default.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                boxed = true,
                fill = AccentCyan,
                tint = OnAccent
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .then(if (onTitleClick != null) Modifier.brutalTap(pressScale = 1f, onClick = onTitleClick).padding(vertical = 8.dp) else Modifier)
            )
            actions()
        }
        BrutalRule()
    }
}

/** Bottom-sheet drag handle: a short ink bar. */
@Composable
fun BrutalSheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 5.dp)
                .background(BorderSubtle, RoundedCornerShape(1.dp))
        )
    }
}

/** Structural 2dp ink rule. */
@Composable
fun BrutalRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(BrutalBorderWidth)
            .background(BorderSubtle)
    )
}

/** Hairline between list rows. */
@Composable
fun BrutalHairline(modifier: Modifier = Modifier, startIndent: Dp = 0.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(1.dp)
            .background(BorderSubtle.copy(alpha = 0.18f))
    )
}

@Composable
fun BrutalSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

/** Square icon tile used at the start of list rows. */
@Composable
fun BrutalIconTile(icon: ImageVector, fill: Color = SurfaceCard, tint: Color = TextPrimary, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .brutalBox(fill = fill, border = BorderSubtle, borderWidth = 1.5.dp, shape = RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * Edge-to-edge settings row. Pass [checked] to render a switch (the whole row
 * toggles), or [onClick] for navigation/actions with an optional [trailing].
 */
@Composable
fun BrutalListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconFill: Color? = null,
    titleColor: Color = TextPrimary,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressTint by animateColorAsState(
        targetValue = if (pressed) BorderSubtle.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(90),
        label = "rowPress"
    )
    val rowModifier = when {
        checked != null && onCheckedChange != null -> Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = interaction,
            indication = null,
            onValueChange = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onCheckedChange(it)
            }
        )
        onClick != null -> Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
        )
        else -> Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .background(pressTint)
            .then(rowModifier)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            BrutalIconTile(icon = icon, fill = iconFill ?: SurfaceCard)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = TextSecondary, fontSize = 12.5.sp, lineHeight = 16.sp)
            }
        }
        if (checked != null) {
            Spacer(Modifier.width(8.dp))
            BrutalSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
        } else if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Indeterminate or determinate ink-on-accent progress line. */
@Composable
fun BrutalProgressLine(progress: Float?, modifier: Modifier = Modifier) {
    if (progress == null) {
        LinearProgressIndicator(
            modifier = modifier.fillMaxWidth().height(4.dp),
            color = BorderSubtle,
            trackColor = AccentCyan
        )
    } else {
        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = modifier.fillMaxWidth().height(4.dp),
            color = BorderSubtle,
            trackColor = AccentCyan
        )
    }
}

/** Single-line search input: flat field, ink outline, hard shadow while focused. */
@Composable
fun BrutalSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    var focused by remember { androidx.compose.runtime.mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .brutalBox(
                fill = SurfaceElevated,
                border = BorderSubtle,
                shadow = if (focused) HardShadow else null,
                shadowOffset = 3.dp
            )
            .padding(start = 12.dp, end = 4.dp)
    ) {
        Icon(androidx.compose.material.icons.Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = TextSecondary, fontSize = 14.sp, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = SpaceGrotesk, color = TextPrimary, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(TextPrimary),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    autoCorrect = false,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
            )
        }
        if (value.isNotEmpty()) {
            BrutalIconButton(androidx.compose.material.icons.Icons.Default.Close, "Clear search", { onValueChange("") }, iconSize = 18.dp)
        }
    }
}

/** Site favicon in a small square tile, with a letter fallback. */
@Composable
fun SiteFavicon(url: String, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val host = remember(url) {
        try { java.net.URI(url).host?.removePrefix("www.") ?: url } catch (e: Exception) { url }
    }
    Box(
        modifier = modifier
            .size(size)
            .brutalBox(fill = SurfaceCard, border = BorderSubtle, borderWidth = 1.5.dp, shape = RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(host.take(1).uppercase(), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        coil.compose.AsyncImage(
            // Straight from the site: a third-party favicon service would learn every site in your history.
            model = "https://$host/favicon.ico",
            contentDescription = null,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

/** Empty or error state: short title, one line of explanation, optional action. */
@Composable
fun BrutalEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(message, color = TextSecondary, fontSize = 14.sp, lineHeight = 19.sp)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            BrutalButton(actionLabel, onAction)
        }
    }
}

/**
 * Centered dialog panel: ink outline, hard shadow, title, free-form body and
 * up to two actions. The panel pops in with a short spring.
 */
@Composable
fun BrutalDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    onDismissAction: (() -> Unit)? = null,
    confirmFill: Color = AccentCyan,
    dismissOnOutside: Boolean = true,
    /** Stack the buttons full width; use when a label would not fit at half width. */
    stackButtons: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    body: @Composable () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnClickOutside = dismissOnOutside,
            usePlatformDefaultWidth = false
        )
    ) {
        val appear = remember { androidx.compose.animation.core.Animatable(0.92f) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
        }
        Column(
            modifier = Modifier
                .graphicsLayer { scaleX = appear.value; scaleY = appear.value }
                .padding(horizontal = 20.dp)
                .widthIn(max = 460.dp)
                .padding(end = 5.dp, bottom = 5.dp)
                .fillMaxWidth()
                .brutalBox(fill = SurfaceDark, border = BorderSubtle, shadow = HardShadow, shadowOffset = 5.dp)
                .padding(20.dp)
        ) {
            if (header != null) {
                header()
                Spacer(Modifier.height(14.dp))
            }
            Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
            Spacer(Modifier.height(8.dp))
            body()
            Spacer(Modifier.height(20.dp))
            val longLabels = stackButtons || confirmLabel.length > 10 || (dismissLabel?.length ?: 0) > 10
            if (longLabels) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BrutalButton(confirmLabel, onConfirm, Modifier.fillMaxWidth().padding(end = 3.dp), fill = confirmFill, contentColor = InkBlack)
                    if (dismissLabel != null) {
                        BrutalOutlineButton(dismissLabel, onDismissAction ?: onDismiss, Modifier.fillMaxWidth().padding(end = 3.dp))
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    if (dismissLabel != null) {
                        BrutalOutlineButton(dismissLabel, onDismissAction ?: onDismiss, Modifier.weight(1f))
                    }
                    BrutalButton(confirmLabel, onConfirm, Modifier.weight(1f), fill = confirmFill, contentColor = InkBlack)
                }
            }
        }
    }
}

/** Labelled single-line input in the flat style. */
@Composable
fun BrutalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Next,
    isPassword: Boolean = false,
    onImeAction: () -> Unit = {}
) {
    var focused by remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .padding(end = 3.dp, bottom = 3.dp)
                .fillMaxWidth()
                .height(48.dp)
                .brutalBox(
                    fill = SurfaceElevated,
                    border = BorderSubtle,
                    shadow = if (focused) HardShadow else null,
                    shadowOffset = 3.dp
                )
                .padding(horizontal = 12.dp)
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, color = TextSecondary, fontSize = 14.sp, maxLines = 1)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = SpaceGrotesk, color = TextPrimary, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(TextPrimary),
                visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (isPassword && keyboardType == androidx.compose.ui.text.input.KeyboardType.Text) androidx.compose.ui.text.input.KeyboardType.Password else keyboardType,
                    imeAction = imeAction,
                    autoCorrect = false
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onAny = { onImeAction() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
            )
        }
    }
}

/**
 * Makes a full-screen layer opaque to touch. Without it, taps on empty parts
 * of a page fall through to whatever is below, such as the hidden address bar.
 */
fun Modifier.blockTouchesBelow(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
    }
}
