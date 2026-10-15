/*
 * Copyright (C) 2024-2026 Gibbly Gobbly <gibblygobbly@proton.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.browser.adfree.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

@Composable
fun GeckoViewContainer(
    session: GeckoSession,
    isLoading: Boolean = false,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Plain holder, not Compose state: scroll events arrive every frame on the
    // main thread (which also drives Gecko's touch scrolling), and a state
    // write per event schedules UI work that makes scrolling stutter.
    val scrollY = remember(session) { java.util.concurrent.atomic.AtomicInteger(0) }
    val spinnerInk = com.browser.adfree.ui.theme.BorderSubtle.toArgb()
    val spinnerFill = com.browser.adfree.ui.theme.AccentCyan.toArgb()

    DisposableEffect(session) {
        val scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, y: Int) {
                scrollY.set(y)
            }
        }
        session.scrollDelegate = scrollDelegate
        onDispose {
            if (session.scrollDelegate === scrollDelegate) {
                session.scrollDelegate = null
            }
        }
    }

    AndroidView(
        factory = { context ->
            val geckoView = com.browser.adfree.ui.IncognitoGeckoView(context).apply {
                setSession(session)
            }

            SwipeRefreshLayout(context).apply {
                setColorSchemeColors(spinnerInk)
                setProgressBackgroundColorSchemeColor(spinnerFill)
                
                addView(
                    geckoView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                setOnRefreshListener {
                    onRefresh()
                }

                // Only allow pull-to-refresh when strictly at the very top of the webpage (scrollY <= 0).
                // If scrollY > 0, return true so GeckoView can scroll up smoothly without triggering reload.
                setOnChildScrollUpCallback { _, _ ->
                    scrollY.get() > 0
                }
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { swipeLayout ->
            swipeLayout.isRefreshing = isLoading
            for (i in 0 until swipeLayout.childCount) {
                val child = swipeLayout.getChildAt(i)
                if (child is GeckoView) {
                    if (child.session != session) {
                        child.setSession(session)
                    }
                    break
                }
            }
        }
    )
}
