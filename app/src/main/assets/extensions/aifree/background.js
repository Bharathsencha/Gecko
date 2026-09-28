/**
 * AI Free Background — native bridge only.
 * Ad blocking is handled by real uBlock Origin.
 * Dark mode is handled by real Dark Reader.
 */

// Native port bridge for block count updates from uBlock → app UI
let nativePort = null;

function connectToNativeBridge() {
    try {
        if (browser.runtime && browser.runtime.connectNative) {
            nativePort = browser.runtime.connectNative("browser-app");
            nativePort.onMessage.addListener((msg) => {
                handleConfigUpdate(msg);
            });
            nativePort.onDisconnect.addListener(() => {
                nativePort = null;
                setTimeout(connectToNativeBridge, 3000);
            });
        }
    } catch (e) {}
}

function notifyNative(msg) {
    try {
        if (nativePort) {
            nativePort.postMessage(msg);
        } else if (browser.runtime && browser.runtime.sendNativeMessage) {
            browser.runtime.sendNativeMessage("browser-app", msg).catch(() => {});
        }
    } catch (e) {}
}

function handleConfigUpdate(msg) {
    // Dark Reader toggle — forward to Dark Reader extension
    if (msg && msg.action === "set_dark_mode") {
        browser.runtime.sendMessage(
            "addon@darkreader.org",
            {
                type: "change-settings",
                settings: { enabled: !!msg.enabled }
            }
        ).catch(() => {});
    }

    // uBlock toggle — enable/disable uBlock Origin
    if (msg && msg.action === "set_ad_blocking") {
        const enabled = !!msg.enabled;
        // uBlock uses its internal "suspendTabFiltering" or global toggle
        // We send a message to uBlock's background to toggle
        browser.runtime.sendMessage(
            "uBlock0@raymondhill.net",
            { what: enabled ? "restoreAll" : "suspendAll" }
        ).catch(() => {
            // Fallback: toggle via storage flag that uBlock checks
            browser.storage.local.set({ "uBlockDisabled": !enabled });
        });
    }
}

connectToNativeBridge();
