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

package com.browser.adfree.vpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.torproject.jni.TorService

/**
 * Embedded Tor. tor can run only once per process, so the daemon is started
 * on the first connect and then kept alive for the app's lifetime: disconnect
 * sets DisableNetwork (no traffic, no circuits) and reconnect re-enables it
 * and asks for fresh circuits. Restarting the service crashed the app.
 */
class TorController(context: Context) {

    interface Listener {
        fun onProgress(percent: Int, summary: String)
        fun onReady(socksPort: Int)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main)
    private var service: TorService? = null
    private var connection: ServiceConnection? = null
    private var job: Job? = null
    private var listener: Listener? = null

    /** [exitCountry] is an ISO code, or blank to let Tor pick the fastest exit. */
    fun start(exitCountry: String, listener: Listener) {
        job?.cancel()
        this.listener = listener
        val running = service
        if (running != null) {
            job = scope.launch { reconfigureAndWait(running, exitCountry) }
            return
        }
        writeTorrc(exitCountry)
        if (connection == null) {
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val svc = (binder as? TorService.LocalBinder)?.service ?: return
                    service = svc
                    job = scope.launch { waitForCircuit(svc) }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    service = null
                    this@TorController.listener?.onError("Tor stopped unexpectedly. Restart the app to use Tor again.")
                }
            }
            connection = conn
            appContext.bindService(Intent(appContext, TorService::class.java), conn, Context.BIND_AUTO_CREATE)
        }
    }

    /** Stops Tor traffic but keeps the daemon, so the next connect is quick and safe. */
    fun stop() {
        job?.cancel()
        job = null
        listener = null
        val svc = service ?: return
        scope.launch(Dispatchers.IO) {
            try {
                svc.torControlConnection?.setConf("DisableNetwork", "1")
            } catch (e: Exception) {
                Log.w(TAG, "Could not pause Tor: ${e.message}")
            }
        }
    }

    private suspend fun reconfigureAndWait(svc: TorService, exitCountry: String) {
        listener?.onProgress(5, "Resuming Tor")
        val ok = withContext(Dispatchers.IO) {
            try {
                val control = svc.torControlConnection ?: return@withContext false
                if (exitCountry.isBlank()) {
                    control.resetConf(listOf("ExitNodes", "StrictNodes"))
                } else {
                    control.setConf(mapOf("ExitNodes" to "{${exitCountry.lowercase()}}", "StrictNodes" to "1"))
                }
                control.setConf("DisableNetwork", "0")
                control.signal("NEWNYM")
                true
            } catch (e: Exception) {
                Log.w(TAG, "Tor reconfigure failed: ${e.message}")
                false
            }
        }
        if (!ok) {
            listener?.onError("Tor did not respond. Restart the app and try again.")
            return
        }
        waitForCircuit(svc)
    }

    /** Polls tor's own bootstrap state; ready once a circuit is built. */
    private suspend fun waitForCircuit(svc: TorService) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (scope.isActive && System.currentTimeMillis() < deadline) {
            val (phase, circuit) = withContext(Dispatchers.IO) {
                try {
                    svc.getInfo("status/bootstrap-phase") to svc.getInfo("status/circuit-established")
                } catch (e: Exception) {
                    null to null
                }
            }
            if (phase != null) {
                val percent = Regex("PROGRESS=(\\d+)").find(phase)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val summary = Regex("SUMMARY=\"([^\"]*)\"").find(phase)?.groupValues?.get(1) ?: "Starting Tor"
                listener?.onProgress(percent, summary)
                val port = svc.socksPort
                if (percent >= 100 && circuit?.trim() == "1" && port > 0) {
                    listener?.onReady(port)
                    return
                }
            }
            delay(500)
        }
        listener?.onError("Tor could not build a circuit in time. Pick \"Any country\" or check your connection.")
    }

    private fun writeTorrc(exitCountry: String) {
        val lines = buildString {
            // Lighter on mobile data and battery, same anonymity.
            append("ReducedConnectionPadding 1\n")
            append("ReducedCircuitPadding 1\n")
            if (exitCountry.isNotBlank()) {
                append("ExitNodes {").append(exitCountry.lowercase()).append("}\n")
                append("StrictNodes 1\n")
            }
        }
        try {
            TorService.getTorrc(appContext).writeText(lines)
        } catch (e: Exception) {
            Log.w(TAG, "Could not write torrc: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "TorController"
        private const val TIMEOUT_MS = 90_000L
    }
}
