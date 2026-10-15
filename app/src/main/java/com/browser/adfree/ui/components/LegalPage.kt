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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.ui.theme.*

/**
 * Shows a bundled Markdown document (assets/legal) as a native page.
 * Handles what the policy and terms use: headings, paragraphs, bullets,
 * tables, **bold**, `code` and [links](url) (shown as their text).
 */
@Composable
fun LegalPage(assetName: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val markdown = remember(assetName) {
        runCatching { context.assets.open("legal/$assetName").bufferedReader().readText() }.getOrDefault("")
    }
    val blocks = remember(markdown) { parseBlocks(markdown) }

    Column(Modifier.fillMaxSize().background(DarkBg)) {
        BrutalPageBar(title = title, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (blocks.isEmpty()) {
                BrutalEmptyState(title = "Not available", message = "This document could not be loaded.")
            }
            blocks.forEach { block -> RenderBlock(block) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Bullets(val items: List<String>) : Block
    data class Table(val header: List<String>, val rows: List<List<String>>) : Block
}

private fun parseBlocks(md: String): List<Block> {
    val out = mutableListOf<Block>()
    val lines = md.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trimEnd()
        when {
            line.isBlank() -> i++
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                out += Block.Heading(level, line.drop(level).trim())
                i++
            }
            line.trimStart().startsWith("|") -> {
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    val cells = lines[i].trim().trim('|').split("|").map { it.trim() }
                    if (cells.none { c -> c.isNotEmpty() && c.any { it != ':' && it != '-' } }) { i++; continue }
                    rows += cells
                    i++
                }
                if (rows.isNotEmpty()) out += Block.Table(rows.first(), rows.drop(1))
            }
            line.trimStart().startsWith("- ") -> {
                val items = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith("- ")) {
                    items += lines[i].trimStart().removePrefix("- ").trim()
                    i++
                }
                out += Block.Bullets(items)
            }
            else -> {
                val sb = StringBuilder(line.trim())
                i++
                while (i < lines.size && lines[i].isNotBlank() && !lines[i].startsWith("#") &&
                    !lines[i].trimStart().startsWith("- ") && !lines[i].trimStart().startsWith("|")
                ) {
                    sb.append(' ').append(lines[i].trim())
                    i++
                }
                out += Block.Paragraph(sb.toString())
            }
        }
    }
    return out
}

/** Inline Markdown: **bold**, `code`, [text](url) -> text. */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    val linked = Regex("\\[([^\\]]+)]\\(([^)]+)\\)").replace(text) { it.groupValues[1] }
    var rest = linked
    val pattern = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
    while (true) {
        val m = pattern.find(rest) ?: break
        append(rest.substring(0, m.range.first))
        if (m.groupValues[1].isNotEmpty()) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
        } else {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(m.groupValues[2]) }
        }
        rest = rest.substring(m.range.last + 1)
    }
    append(rest)
}

@Composable
private fun RenderBlock(block: Block) {
    when (block) {
        is Block.Heading -> Text(
            inline(block.text),
            color = TextPrimary,
            fontSize = when (block.level) { 1 -> 24.sp; 2 -> 19.sp; else -> 16.sp },
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = if (block.level == 1) 8.dp else 20.dp, bottom = 8.dp)
        )
        is Block.Paragraph -> Text(
            inline(block.text),
            color = TextPrimary,
            fontSize = 14.5.sp,
            lineHeight = 21.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        is Block.Bullets -> Column(Modifier.padding(bottom = 10.dp)) {
            block.items.forEach { item ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .padding(top = 8.dp)
                            .size(7.dp)
                            .background(AccentCyan)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(inline(item), color = TextPrimary, fontSize = 14.5.sp, lineHeight = 21.sp)
                }
            }
        }
        is Block.Table -> Column(
            Modifier
                .padding(top = 4.dp, bottom = 14.dp, end = 4.dp)
                .fillMaxWidth()
                .brutalBox(fill = SurfaceDark, border = BorderSubtle, shadow = HardShadow, shadowOffset = 4.dp)
        ) {
            // Each row reads top to bottom: the first column as a title, the rest labelled by their header.
            block.rows.forEachIndexed { index, row ->
                if (index > 0) BrutalHairline()
                Column(Modifier.padding(12.dp)) {
                    Text(inline(row.getOrElse(0) { "" }), color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    row.drop(1).forEachIndexed { c, cell ->
                        if (cell.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(block.header.getOrElse(c + 1) { "" }.uppercase(), color = TextSecondary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            Text(inline(cell), color = TextPrimary, fontSize = 13.5.sp, lineHeight = 19.sp)
                        }
                    }
                }
            }
        }
    }
}
