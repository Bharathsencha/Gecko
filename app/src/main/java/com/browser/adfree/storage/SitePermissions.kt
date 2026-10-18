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

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission

/** Things a site can ask for. */
enum class SitePermission(val label: String, val ask: String) {
    LOCATION("Location", "know your location"),
    CAMERA("Camera", "use your camera"),
    MICROPHONE("Microphone", "use your microphone"),
    NOTIFICATIONS("Notifications", "send you notifications"),
    DRM("Protected media", "play protected (DRM) media"),
    STORAGE("Persistent storage", "keep its data stored on this phone"),
    XR("VR and AR", "use virtual reality devices"),
    LOCAL_NETWORK("Local network", "reach devices on your local network"),
    COOKIES("Cross-site cookies", "use its cookies while embedded in other sites");

    companion object {
        /** Maps a Gecko content permission to ours; null for ones handled silently. */
        fun fromGecko(type: Int): SitePermission? = when (type) {
            PermissionDelegate.PERMISSION_GEOLOCATION -> LOCATION
            PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> NOTIFICATIONS
            PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> DRM
            PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> STORAGE
            PermissionDelegate.PERMISSION_XR -> XR
            PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS,
            PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS -> LOCAL_NETWORK
            PermissionDelegate.PERMISSION_STORAGE_ACCESS -> COOKIES
            else -> null
        }

        fun toGecko(p: SitePermission): List<Int> = when (p) {
            LOCATION -> listOf(PermissionDelegate.PERMISSION_GEOLOCATION)
            NOTIFICATIONS -> listOf(PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION)
            DRM -> listOf(PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS)
            STORAGE -> listOf(PermissionDelegate.PERMISSION_PERSISTENT_STORAGE)
            XR -> listOf(PermissionDelegate.PERMISSION_XR)
            LOCAL_NETWORK -> listOf(PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS, PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS)
            COOKIES -> listOf(PermissionDelegate.PERMISSION_STORAGE_ACCESS)
            CAMERA, MICROPHONE -> emptyList()
        }
    }
}

/** A question waiting for the user. Answering it is remembered for the site. */
class PermissionAsk(
    val host: String,
    val permissions: List<SitePermission>,
    val isPrivate: Boolean,
    val onAnswer: (allow: Boolean) -> Unit
)

/**
 * Per-site allow/block decisions. A site is asked once; the answer is kept
 * until the user changes it in the shield panel or in Settings. Answers given
 * in private tabs live in memory only and vanish with the private session.
 */
object SitePermissions {
    private const val PREFS = "site_permissions"

    /** host -> permission -> allowed */
    private val saved = mutableStateOf<Map<String, Map<SitePermission, Boolean>>>(emptyMap())
    private val privateAnswers = HashMap<String, MutableMap<SitePermission, Boolean>>()
    /** Bumped on private changes so screens reading [get] recompose. */
    private val privateVersion = androidx.compose.runtime.mutableIntStateOf(0)
    private var appContext: Context? = null

    /** Requests waiting to be shown, oldest first. */
    val pending = mutableStateListOf<PermissionAsk>()

    var all: Map<String, Map<SitePermission, Boolean>>
        get() = saved.value
        private set(value) { saved.value = value }

    fun init(context: Context) {
        appContext = context.applicationContext
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("map", null) ?: return
        runCatching {
            val json = JSONObject(raw)
            all = json.keys().asSequence().associateWith { host ->
                val inner = json.getJSONObject(host)
                inner.keys().asSequence().mapNotNull { k ->
                    runCatching { SitePermission.valueOf(k) }.getOrNull()?.let { it to inner.getBoolean(k) }
                }.toMap()
            }.filterValues { it.isNotEmpty() }
        }
    }

    private fun persist() {
        val json = JSONObject()
        all.forEach { (host, perms) ->
            json.put(host, JSONObject().apply { perms.forEach { (p, v) -> put(p.name, v) } })
        }
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString("map", json.toString())?.apply()
    }

    fun hostOf(url: String?): String =
        runCatching { Uri.parse(url ?: "").host?.lowercase()?.removePrefix("www.") }.getOrNull().orEmpty()

    fun get(host: String, p: SitePermission, isPrivate: Boolean = false): Boolean? {
        if (isPrivate) {
            privateVersion.intValue
            privateAnswers[host]?.get(p)?.let { return it }
        }
        return all[host]?.get(p)
    }

    fun forSite(host: String): Map<SitePermission, Boolean> = all[host].orEmpty()

    fun set(host: String, p: SitePermission, allowed: Boolean?, isPrivate: Boolean = false, runtime: GeckoRuntime? = null) {
        if (host.isBlank()) return
        if (isPrivate) {
            if (allowed == null) privateAnswers[host]?.remove(p) else privateAnswers.getOrPut(host) { HashMap() }[p] = allowed
            privateVersion.intValue++
        } else {
            val site = all[host].orEmpty().toMutableMap()
            if (allowed == null) site.remove(p) else site[p] = allowed
            all = if (site.isEmpty()) all - host else all + (host to site)
            persist()
        }
        runtime?.let { syncEngine(it, host, p, allowed) }
    }

    fun forget(host: String, runtime: GeckoRuntime? = null) {
        val site = all[host] ?: return
        all = all - host
        persist()
        runtime?.let { rt -> site.keys.forEach { syncEngine(rt, host, it, null) } }
    }

    fun forgetAll(runtime: GeckoRuntime? = null) {
        val hosts = all.keys.toList()
        hosts.forEach { forget(it, runtime) }
        privateAnswers.clear()
    }

    fun clearPrivate() {
        privateAnswers.clear()
        privateVersion.intValue++
    }

    /**
     * Gecko keeps its own copy of answers it was given. Bring it in line so a
     * change made here takes effect on the next request, not after a restart.
     */
    private fun syncEngine(runtime: GeckoRuntime, host: String, p: SitePermission, allowed: Boolean?) {
        val types = SitePermission.toGecko(p)
        if (types.isEmpty()) return
        runtime.storageController.allPermissions.accept { list ->
            list?.filter { it.permission in types && hostOf(it.uri) == host }?.forEach { perm ->
                val value = when (allowed) {
                    true -> ContentPermission.VALUE_ALLOW
                    false -> ContentPermission.VALUE_DENY
                    null -> ContentPermission.VALUE_PROMPT
                }
                runtime.storageController.setPermission(perm, value)
            }
        }
    }
}
