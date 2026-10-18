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

package com.browser.adfree.storage

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Writes small state files off the main thread. Saves of the same file that
 * pile up collapse into one: only the newest content is written. Each write
 * goes to a temp file first and is renamed into place, so a crash never
 * leaves a half-written file.
 */
object DiskWriter {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "disk-writer").apply { isDaemon = true } }
    private val pending = ConcurrentHashMap<String, () -> String>()

    /** [content] runs on the writer thread; give it a snapshot, not live UI state. */
    fun save(file: File, content: () -> String) {
        if (pending.put(file.path, content) != null) return
        executor.execute {
            val build = pending.remove(file.path) ?: return@execute
            runCatching {
                val tmp = File(file.path + ".tmp")
                tmp.writeText(build())
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            }
        }
    }

    /** Deletes [file] after anything already queued, and drops a pending save of it. */
    fun delete(file: File) {
        pending.remove(file.path)
        executor.execute { file.delete() }
    }
}
