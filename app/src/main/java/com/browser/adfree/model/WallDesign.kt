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

package com.browser.adfree.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ShapeKind { RECT, CIRCLE, TRIANGLE, HALF }

/**
 * One shape on a start-page design. Position and size are fractions of the
 * canvas, so a design drawn on the editor's preview scales to any screen.
 */
data class WallShape(
    val id: String = UUID.randomUUID().toString(),
    val kind: ShapeKind,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val color: Long,
    val rotation: Float = 0f,
    val outlined: Boolean = true,
    val shadow: Boolean = true
)

/** [background] null means "use the theme's own background and ink". */
data class WallDesign(
    val background: Long? = null,
    val shapes: List<WallShape> = emptyList()
) {
    fun toJson(): String = JSONObject().apply {
        if (background != null) put("bg", background)
        put("shapes", JSONArray().apply {
            shapes.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id); put("k", s.kind.name)
                    put("x", s.x.toDouble()); put("y", s.y.toDouble()); put("w", s.w.toDouble()); put("h", s.h.toDouble())
                    put("c", s.color); put("r", s.rotation.toDouble()); put("o", s.outlined); put("s", s.shadow)
                })
            }
        })
    }.toString()

    companion object {
        fun fromJson(json: String?): WallDesign? {
            if (json.isNullOrBlank()) return null
            return try {
                val o = JSONObject(json)
                val arr = o.optJSONArray("shapes") ?: JSONArray()
                WallDesign(
                    background = if (o.has("bg")) o.getLong("bg") else null,
                    shapes = (0 until arr.length()).map { i ->
                        val s = arr.getJSONObject(i)
                        WallShape(
                            id = s.optString("id", UUID.randomUUID().toString()),
                            kind = runCatching { ShapeKind.valueOf(s.getString("k")) }.getOrDefault(ShapeKind.RECT),
                            x = s.getDouble("x").toFloat(), y = s.getDouble("y").toFloat(),
                            w = s.getDouble("w").toFloat(), h = s.getDouble("h").toFloat(),
                            color = s.getLong("c"), rotation = s.optDouble("r", 0.0).toFloat(),
                            outlined = s.optBoolean("o", true), shadow = s.optBoolean("s", true)
                        )
                    }
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Shared palette for presets and the designer. */
object DesignPalette {
    const val YELLOW = 0xFFFFD84DL
    const val ORANGE = 0xFFFFB067L
    const val PURPLE = 0xFFB49CFFL
    const val TEAL = 0xFF7FD8C8L
    const val PINK = 0xFFFF9EC7L
    const val BLUE = 0xFF8EC5FFL
    const val RED = 0xFFFF6B4AL
    const val GREEN = 0xFF9BE07AL
    const val INK = 0xFF111111L
    const val PAPER = 0xFFF4F1EAL
    const val WHITE = 0xFFFFFFFFL
    const val CHARCOAL = 0xFF2A2A2AL

    val ALL = listOf(YELLOW, ORANGE, RED, PINK, PURPLE, BLUE, TEAL, GREEN, WHITE, PAPER, CHARCOAL, INK)
    val BACKGROUNDS = listOf(PAPER, WHITE, YELLOW, TEAL, PURPLE, PINK, BLUE, CHARCOAL, INK)
}

/** Built-in block compositions. Shapes sit low so favourites stay readable. */
object DesignPresets {
    private fun r(x: Float, y: Float, w: Float, h: Float, c: Long, rot: Float = 0f) = WallShape(kind = ShapeKind.RECT, x = x, y = y, w = w, h = h, color = c, rotation = rot)
    private fun c(x: Float, y: Float, d: Float, aspect: Float, c: Long) = WallShape(kind = ShapeKind.CIRCLE, x = x, y = y, w = d, h = d * aspect, color = c)
    private fun t(x: Float, y: Float, w: Float, h: Float, c: Long, rot: Float = 0f) = WallShape(kind = ShapeKind.TRIANGLE, x = x, y = y, w = w, h = h, color = c, rotation = rot)
    private fun half(x: Float, y: Float, w: Float, h: Float, c: Long, rot: Float = 0f) = WallShape(kind = ShapeKind.HALF, x = x, y = y, w = w, h = h, color = c, rotation = rot)

    val BLOCKS = WallDesign(shapes = listOf(
        r(-0.05f, 0.62f, 0.45f, 0.2f, DesignPalette.YELLOW),
        r(0.52f, 0.56f, 0.36f, 0.14f, DesignPalette.PURPLE),
        r(0.30f, 0.80f, 0.5f, 0.12f, DesignPalette.TEAL),
        r(0.74f, 0.74f, 0.3f, 0.22f, DesignPalette.ORANGE),
        r(0.08f, 0.86f, 0.16f, 0.08f, DesignPalette.PINK)
    ))

    val STACK = WallDesign(shapes = listOf(
        r(0.10f, 0.58f, 0.62f, 0.08f, DesignPalette.PINK, -4f),
        r(0.22f, 0.67f, 0.66f, 0.08f, DesignPalette.YELLOW, 3f),
        r(0.06f, 0.76f, 0.70f, 0.08f, DesignPalette.TEAL, -2f),
        r(0.26f, 0.85f, 0.64f, 0.08f, DesignPalette.PURPLE, 4f),
        r(0.12f, 0.94f, 0.72f, 0.08f, DesignPalette.ORANGE, -3f)
    ))

    val SHAPES = WallDesign(shapes = listOf(
        c(0.62f, 0.55f, 0.34f, 0.47f, DesignPalette.YELLOW),
        t(0.06f, 0.62f, 0.38f, 0.17f, DesignPalette.PINK, -8f),
        r(0.40f, 0.78f, 0.30f, 0.14f, DesignPalette.BLUE, 6f),
        half(0.02f, 0.85f, 0.36f, 0.09f, DesignPalette.TEAL),
        c(0.80f, 0.86f, 0.14f, 0.47f, DesignPalette.ORANGE)
    ))

    val MONDRIAN = WallDesign(shapes = listOf(
        r(0.0f, 0.55f, 0.42f, 0.22f, DesignPalette.RED),
        r(0.42f, 0.55f, 0.58f, 0.10f, DesignPalette.PAPER),
        r(0.42f, 0.65f, 0.30f, 0.12f, DesignPalette.YELLOW),
        r(0.72f, 0.65f, 0.28f, 0.35f, DesignPalette.BLUE),
        r(0.0f, 0.77f, 0.24f, 0.23f, DesignPalette.PAPER),
        r(0.24f, 0.77f, 0.48f, 0.23f, DesignPalette.INK)
    ).map { it.copy(shadow = false) })

    val STARTER = WallDesign(shapes = listOf(
        r(0.10f, 0.62f, 0.50f, 0.16f, DesignPalette.YELLOW),
        c(0.58f, 0.74f, 0.30f, 0.47f, DesignPalette.PURPLE)
    ))
}

/** A design the user made and named in the designer. */
data class SavedDesign(val id: String, val name: String, val design: WallDesign) {
    companion object {
        fun listToJson(list: List<SavedDesign>): String = JSONArray().apply {
            list.forEach { d -> put(JSONObject().apply { put("id", d.id); put("name", d.name); put("design", d.design.toJson()) }) }
        }.toString()

        fun listFromJson(json: String?): List<SavedDesign> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    val design = WallDesign.fromJson(o.optString("design")) ?: return@mapNotNull null
                    SavedDesign(o.optString("id", UUID.randomUUID().toString()), o.optString("name", "Design ${i + 1}"), design)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
