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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.AnnotatedString
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
    isPrivate: Boolean = false,
    onOpenClearData: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenTabs: () -> Unit = {},
    onReload: () -> Unit = {},
    onOpenShields: () -> Unit = {},
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    historySuggestions: List<HistoryItem> = emptyList(),
    /** Phrases the search engine suggests for what is typed; empty unless the setting is on. */
    searchSuggestions: List<String> = emptyList(),
    clearStyle: com.browser.adfree.storage.ClearStyle = com.browser.adfree.storage.ClearStyle.FIRE,
    container: com.browser.adfree.model.TabContainer? = null,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    val fieldInteraction = remember { MutableInteractionSource() }
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

    // A tap on an already-focused field (focus can go stale after the page view held the
    // keyboard) must still bring the keyboard back.
    LaunchedEffect(fieldInteraction) {
        fieldInteraction.interactions.collect { if (it is PressInteraction.Release) keyboardController?.show() }
    }

    fun focusField() {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val showSuggestions = isFocused && textFieldValue.text.isNotEmpty() && (historySuggestions.isNotEmpty() || searchSuggestions.isNotEmpty())

    val privateFill = PrivatePurple
    val chipGreen = Color(0xFF7FDCA4)
    // Private mode: the night field from the private start page, the same in both themes.
    val fieldInk = if (isPrivate) PrivateNightInk else TextPrimary
    val fieldInkDim = if (isPrivate) PrivateNightMuted else TextSecondary

    Box(modifier = modifier
        .fillMaxWidth()
        .background(SurfaceDark)
        .onGloballyPositioned { coords ->
            barHeightPx = coords.size.height
            barWidthPx  = coords.size.width
        }
    ) {
        Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 8.dp)
        ) {
            BrutalIconButton(
                icon = clearStyleIcon(clearStyle),
                contentDescription = "Clear browsing data",
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onOpenClearData()
                },
                boxed = true,
                fill = clearStyleFill(clearStyle),
                tint = InkBlack
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Omnibox
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .brutalBox(
                        fill = if (isPrivate) PrivateNight else SurfaceElevated,
                        border = BorderSubtle,
                        shadow = if (isFocused) (if (isPrivate) privateFill else HardShadow) else null,
                        shadowOffset = 3.dp
                    )
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focusField() }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!isPrivate && container != null) {
                        Text(
                            container.displayName,
                            color = InkBlack,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .brutalBox(fill = Color(container.color), border = BorderSubtle, borderWidth = 1.5.dp, shape = RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (isPrivate) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(width = 28.dp, height = 22.dp)
                                .brutalBox(fill = privateFill, border = privateFill, borderWidth = 1.dp, shape = RoundedCornerShape(3.dp))
                                .clickable { onOpenShields() }
                                .semantics { contentDescription = "Private tab. Site settings" }
                        ) {
                            PrivateMask(maskColor = InkBlack, eyeColor = privateFill, modifier = Modifier.size(width = 18.dp, height = 11.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (adBlockCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .brutalBox(fill = chipGreen, border = BorderSubtle, borderWidth = 1.5.dp, shape = RoundedCornerShape(3.dp))
                                .clickable { onOpenShields() }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = "Blocked on this page", tint = InkBlack, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(adBlockCount.toString(), color = InkBlack, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (!isStartPage && urlInput.startsWith("https://")) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secure connection, site protection",
                            tint = fieldInk,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onOpenShields() }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (isStartPage) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = fieldInkDim, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Incognito keyboard: no autocorrect or suggestions
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            onUrlChange(newValue.text)
                        },
                        singleLine = true,
                        interactionSource = fieldInteraction,
                        textStyle = TextStyle(fontFamily = SpaceGrotesk, color = fieldInk, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(fieldInk),
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
                            .focusRequester(focusRequester)
                            .onFocusChanged { isFocused = it.isFocused },
                        decorationBox = { innerTextField ->
                            if (textFieldValue.text.isEmpty()) {
                                Text(
                                    text = if (isPrivate) "Search privately or type a URL" else "Search or type a URL",
                                    color = fieldInkDim,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (!isFocused && !isStartPage) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { onToggleBookmark() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark this page",
                                tint = fieldInk,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    if (isFocused && textFieldValue.text.isEmpty() && clipboard.hasText()) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable {
                                    val pasted = clipboard.getText()?.text?.trim().orEmpty()
                                    if (pasted.isNotEmpty()) {
                                        textFieldValue = TextFieldValue(pasted, selection = TextRange(pasted.length))
                                        onUrlChange(pasted)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste",
                                tint = fieldInk,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (textFieldValue.text.isNotEmpty() && urlInput != "about:home") {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable {
                                    if (isFocused) {
                                        textFieldValue = TextFieldValue("", selection = TextRange.Zero)
                                        onUrlChange("")
                                    } else {
                                        onReload()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFocused) Icons.Default.Close else Icons.Default.Refresh,
                                contentDescription = if (isFocused) "Clear" else "Reload",
                                tint = fieldInk,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            BrutalIconButton(
                icon = Icons.Default.History,
                contentDescription = "History",
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onOpenHistory()
                }
            )
            // Tab count tile
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onOpenTabs()
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .brutalBox(
                            fill = if (isPrivate) privateFill else SurfaceDark,
                            border = BorderSubtle,
                            shape = RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (tabCount > 99) "99+" else tabCount.toString(),
                        color = if (isPrivate) InkBlack else TextPrimary,
                        fontSize = if (tabCount > 9) 10.sp else 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        BrutalRule()
        }

        // Autocomplete Popup — floats OVER the WebView, zero layout impact
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
                        .brutalBox(fill = SurfaceElevated, border = BorderSubtle, shadow = HardShadow)
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
                        if (index < historySuggestions.lastIndex || searchSuggestions.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(BorderSubtle.copy(alpha = 0.2f))
                            )
                        }
                    }
                    searchSuggestions.forEachIndexed { index, phrase ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    onSearch(phrase)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = phrase,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (index < searchSuggestions.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(BorderSubtle.copy(alpha = 0.2f))
                            )
                        }
                    }
                }
            }
        }
    }
}
