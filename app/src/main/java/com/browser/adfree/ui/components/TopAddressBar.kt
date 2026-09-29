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

package com.browser.adfree.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.browser.adfree.model.HistoryItem
import com.browser.adfree.ui.theme.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TopAddressBar(
    urlInput: String,
    onUrlChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    adBlockCount: Int,
    isReaderMode: Boolean,
    isDarkWeb: Boolean,
    tabCount: Int = 1,
    onOpenClearData: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenTabs: () -> Unit = {},
    onReload: () -> Unit = {},
    onOpenShields: () -> Unit = {},
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    historySuggestions: List<HistoryItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val isStartPage = urlInput == "about:home" || urlInput.isBlank()

    val currentDisplayText = if (urlInput == "about:home") "" else urlInput
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = currentDisplayText,
                selection = TextRange(currentDisplayText.length)
            )
        )
    }
    var isFocused by remember { mutableStateOf(false) }

    // Track the position & width of the URL bar row so the Popup can be anchored correctly
    var barHeightPx by remember { mutableStateOf(0) }
    var barWidthPx  by remember { mutableStateOf(0) }

    // Keep textFieldValue in sync when urlInput updates externally (tab switch / page load)
    LaunchedEffect(urlInput) {
        val target = if (urlInput == "about:home") "" else urlInput
        if (textFieldValue.text != target) {
            textFieldValue = TextFieldValue(text = target, selection = TextRange(target.length))
        }
    }

    // Auto-select all on first focus gain only
    var previousFocused by remember { mutableStateOf(false) }
    LaunchedEffect(isFocused) {
        if (isFocused && !previousFocused && textFieldValue.text.isNotEmpty()) {
            textFieldValue = textFieldValue.copy(
                selection = TextRange(0, textFieldValue.text.length)
            )
        }
        previousFocused = isFocused
    }

    val showSuggestions = isFocused && textFieldValue.text.isNotEmpty() && historySuggestions.isNotEmpty()

    // ── Main bar row (layout unchanged — no Column wrapper that would push WebView) ──
    Box(modifier = modifier
        .fillMaxWidth()
        .onGloballyPositioned { coords ->
            barHeightPx = coords.size.height
            barWidthPx  = coords.size.width
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // Burn Logo Button on left side beside URL bar
            IconButton(
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onOpenClearData()
                },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
                    .border(
                        1.dp,
                        Brush.horizontalGradient(listOf(FlameOrange.copy(alpha = 0.5f), BorderSubtle)),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = "Clear Data",
                    tint = FlameOrange,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Main Omnibox Capsule
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(SurfaceElevated)
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            colors = listOf(
                                if (isReaderMode) AccentCyan else BorderSubtle,
                                AccentCyan.copy(alpha = 0.35f),
                                BorderSubtle
                            )
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {

                    // Shield / Lock / Search icon (Clicking opens Shields Quick Sheet)
                    if (adBlockCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ShieldGreen.copy(alpha = 0.15f))
                                .clickable { onOpenShields() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Shield",
                                tint = ShieldGreen,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = adBlockCount.toString(),
                                color = ShieldGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    } else if (!isStartPage && urlInput.startsWith("https://")) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenShields() }
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Secure",
                                tint = AccentCyan.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    } else if (isStartPage) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // 3. Omnibox — incognito keyboard (no autocorrect / suggestions)
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            onUrlChange(newValue.text)
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Search,
                            autoCorrect = false
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onSearch(textFieldValue.text)
                            },
                            onGo = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onSearch(textFieldValue.text)
                            },
                            onDone = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onSearch(textFieldValue.text)
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { isFocused = it.isFocused },
                        decorationBox = { innerTextField ->
                            if (textFieldValue.text.isEmpty() || urlInput == "about:home") {
                                Text(
                                    text = "Search or type URL",
                                    color = TextSecondary.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    // 4. Clear + Reload
                    if (textFieldValue.text.isNotEmpty() && urlInput != "about:home") {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    textFieldValue = TextFieldValue("", selection = TextRange.Zero)
                                    onUrlChange("")
                                }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(17.dp)
                                .clickable { onReload() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Action Capsule: Bookmark + History + Settings
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(SurfaceElevated)
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(BorderSubtle, AccentCyan.copy(alpha = 0.2f), BorderSubtle)
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 2.dp)
            ) {
                IconButton(
                    onClick = {
                        onToggleBookmark()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Bookmarked" else "Bookmark this page",
                        tint = if (isBookmarked) AccentCyan else TextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(BorderSubtle)
                )
                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onOpenHistory()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = TextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(BorderSubtle)
                )
                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onOpenTabs()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Tabs",
                            tint = TextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                        if (tabCount > 1) {
                            Box(
                                modifier = Modifier
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .size(14.dp)
                                    .background(AccentCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabCount.toString(),
                                    color = DarkBg,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Autocomplete Popup — floats OVER the WebView, zero layout impact ──
        if (showSuggestions) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(
                    x = with(density) { 12.dp.roundToPx() },
                    y = barHeightPx  // position just below the bar
                ),
                properties = PopupProperties(focusable = false, dismissOnClickOutside = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(with(density) { (barWidthPx - 24.dp.roundToPx()).toDp() })
                        .shadow(12.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                ) {
                    historySuggestions.forEachIndexed { index, item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    onSearch(item.url)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                if (item.title.isNotBlank() && item.title != item.url) {
                                    Text(
                                        text = item.title,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                }
                                Text(
                                    text = item.url,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // Divider between items
                        if (index < historySuggestions.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .padding(horizontal = 14.dp)
                                    .background(BorderSubtle)
                            )
                        }
                    }
                }
            }
        }
    }
}
