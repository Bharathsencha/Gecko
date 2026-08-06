package com.browser.adblocker.downloader.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

@Composable
fun BrowserGeckoView(
    session: GeckoSession,
    modifier: Modifier = Modifier,
    onViewCreated: (GeckoView) -> Unit = {}
) {
    AndroidView(
        factory = { context ->
            GeckoView(context).apply {
                setSession(session)
                onViewCreated(this)
            }
        },
        modifier = modifier,
        update = { view ->
            if (view.session != session) {
                view.setSession(session)
            }
        }
    )
}
