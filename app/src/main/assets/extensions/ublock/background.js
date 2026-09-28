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

/**
 * Native Ad & Tracker Content Shield Engine for GeckoView
 * Handles network-level request blocking, domain matching, proxy routing, and native synchronization.
 */

let blockedRules = new Set();
let blockedStats = {
    totalBlocked: 0,
    sessionBlocked: 0,
    tabBlocked: {}
};

let adBlockingActive = true;
let blockAiOverviewsActive = true;

// In-Browser VPN / Secure Proxy Router
let vpnConfig = {
    enabled: false,
    type: "http",
    host: "",
    port: 0
};

// Comprehensive built-in ad, tracker, popup, and telemetry domain database
const coreBlocklist = [
    // Major Ad Networks & Exchanges
    "doubleclick.net", "googleadservices.com", "adservice.google.com", "googlesyndication.com",
    "adnxs.com", "adcolony.com", "pubmatic.com", "rubiconproject.com", "adform.net",
    "openx.net", "scorecardresearch.com", "taboola.com", "outbrain.com", "criteo.com",
    "amazon-adsystem.com", "adroll.com", "quantserve.com", "flurry.com", "moatads.com",
    "bidswitch.net", "casalemedia.com", "contextweb.com", "exponential.com", "inmobi.com",
    "smartadserver.com", "spotxchange.com", "triplelift.com", "yieldmo.com", "zemanta.com",
    "chartbeat.net", "optimizely.com", "hotjar.com", "crazyegg.com", "mouseflow.com",
    
    // Aggressive Popups, Redirects & Push Ads
    "popads.net", "popcash.net", "propellerads.com", "hilltopads.net", "yllix.com",
    "adcash.com", "adsterra.com", "exoclick.com", "juicyads.com", "trafficjunky.com",
    "clickadu.com", "revenuehits.com", "bidvertiser.com", "chitika.com", "infolinks.com",
    "mgid.com", "revcontent.com", "zergnet.com", "adclick.g.doubleclick.net",
    "pagead2.googlesyndication.com", "tpc.googlesyndication.com", "partner.googleadservices.com",
    "ads.youtube.com", "securepubads.g.doubleclick.net", "ad.doubleclick.net",
    "static.doubleclick.net", "stats.g.doubleclick.net", "ad-delivery.net", "ad-maven.com",
    "adpushup.com", "adskeeper.co.uk", "adthrive.com", "mediavine.com", "monetag.com",
    "adtrue.com", "adblade.com", "richaudience.com", "sovrn.com", "undertone.com",
    
    // Tracking & Telemetry
    "google-analytics.com", "analytics.google.com", "statcounter.com", "yandex.ru/metrika",
    "mc.yandex.ru", "clarity.ms", "segment.io", "mixpanel.com", "amplitude.com",
    "newrelic.com", "nr-data.net", "branch.io", "appsflyer.com", "adjust.com",
    "facebook.net/en_US/fbevents.js", "connect.facebook.net/signals", "ads-twitter.com",
    "bat.bing.com", "ads.linkedin.com", "tr.snapchat.com", "analytics.tiktok.com",
    
    // Cryptomining & Malware
    "coinhive.com", "crypto-loot.com", "coin-have.com", "minr.pw", "webminepool.com",
    "authedmine.com", "monerominer.rocks", "browser-mining.com"
];

// Initialize Set with core domains
coreBlocklist.forEach(domain => blockedRules.add(domain.toLowerCase()));

// Load cached rules from storage
browser.storage.local.get(["cachedRules", "totalBlocked"], (res) => {
    if (res.cachedRules && Array.isArray(res.cachedRules)) {
        res.cachedRules.forEach(d => blockedRules.add(d.toLowerCase()));
    }
    if (res.totalBlocked) {
        blockedStats.totalBlocked = res.totalBlocked;
    }
    syncLiveRules();
});

// Sync updated blocklists (AdGuard DNS + Firebog consolidated hosts)
async function syncLiveRules() {
    try {
        const response = await fetch("https://v.firebog.net/hosts/AdguardDNS.txt");
        if (response.ok) {
            const raw = await response.text();
            const freshDomains = raw.split("\n")
                .map(l => l.trim().toLowerCase())
                .filter(l => l && !l.startsWith("#") && !l.includes(" ") && l.includes("."));
            
            if (freshDomains.length > 500) {
                freshDomains.forEach(d => blockedRules.add(d));
                browser.storage.local.set({ cachedRules: Array.from(blockedRules) });
            }
        }
    } catch (err) {}
}

// Fast Subdomain & Exact Matcher
function isBlocked(host) {
    if (!host) return false;
    let curr = host.toLowerCase();
    
    while (curr.includes(".")) {
        if (blockedRules.has(curr)) return true;
        let nextDot = curr.indexOf(".");
        if (nextDot === -1 || nextDot === curr.length - 1) break;
        curr = curr.substring(nextDot + 1);
    }
    return false;
}

// 1. Request Interception & Cancellation + Google AI Overview &udm=14 auto-redirect
browser.webRequest.onBeforeRequest.addListener(
    function(details) {
        try {
            const urlObj = new URL(details.url);
            const host = urlObj.hostname.toLowerCase();
            const fullUrl = details.url.toLowerCase();

            // Intercept Google main searches to automatically inject &udm=14 (Web Clean Mode)
            if (blockAiOverviewsActive && details.type === "main_frame" && host.includes("google.") && urlObj.pathname.startsWith("/search")) {
                if (!urlObj.searchParams.has("udm") && urlObj.searchParams.has("q")) {
                    urlObj.searchParams.set("udm", "14");
                    return { redirectUrl: urlObj.toString() };
                }
            }

            if (!adBlockingActive) {
                return { cancel: false };
            }

            // Direct Host match check
            let matched = isBlocked(host);

            // Pattern heuristics for inline ad scripts/pixels
            if (!matched) {
                if (fullUrl.includes("/ads.js") ||
                    fullUrl.includes("/adservice.") ||
                    fullUrl.includes("/pagead/js/") ||
                    fullUrl.includes("/google-analytics.com/analytics.js") ||
                    fullUrl.includes("/gtag/js?id=") ||
                    fullUrl.includes("ad_type=") ||
                    fullUrl.includes("/ad_status.") ||
                    fullUrl.includes("/popunder.") ||
                    fullUrl.includes("popup.js")) {
                    matched = true;
                }
            }

            if (matched) {
                blockedStats.totalBlocked++;
                blockedStats.sessionBlocked++;

                const tabId = details.tabId;
                if (tabId !== undefined && tabId >= 0) {
                    blockedStats.tabBlocked[tabId] = (blockedStats.tabBlocked[tabId] || 0) + 1;
                }

                // Notify native Kotlin app with blocked stats
                notifyNative({
                    action: "ad_blocked",
                    url: details.url,
                    domain: host,
                    tabBlockedCount: tabId >= 0 ? blockedStats.tabBlocked[tabId] : 1,
                    totalBlockedCount: blockedStats.totalBlocked
                });

                return { cancel: true };
            }
        } catch (e) {}
        return { cancel: false };
    },
    { urls: ["<all_urls>"] },
    ["blocking"]
);

// Reset tab counter on new navigation
browser.webRequest.onBeforeSendHeaders.addListener(
    function(details) {
        if (details.type === "main_frame") {
            const tabId = details.tabId;
            if (tabId !== undefined && tabId >= 0) {
                blockedStats.tabBlocked[tabId] = 0;
            }
        }
    },
    { urls: ["<all_urls>"] }
);

// 2. In-Browser Proxy / VPN Routing
if (browser.proxy && browser.proxy.onRequest) {
    browser.proxy.onRequest.addListener(
        (details) => {
            if (vpnConfig.enabled && vpnConfig.host && vpnConfig.port > 0) {
                return {
                    type: vpnConfig.type || "http",
                    host: vpnConfig.host,
                    port: vpnConfig.port,
                    proxyDNS: true
                };
            }
            return { type: "direct" };
        },
        { urls: ["<all_urls>"] }
    );
}

// 3. Persistent Native Port Bridge
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

connectToNativeBridge();

function notifyNative(msg) {
    try {
        if (nativePort) {
            nativePort.postMessage(msg);
        } else if (browser.runtime && browser.runtime.sendNativeMessage) {
            browser.runtime.sendNativeMessage("browser-app", msg).catch(() => {});
        }
    } catch (e) {}
}

function handleConfigUpdate(request) {
    if (!request) return;
    if (request.action === "set_ad_blocking") {
        adBlockingActive = !!request.enabled;
    } else if (request.action === "set_block_ai_overviews") {
        blockAiOverviewsActive = !!request.enabled;
    } else if (request.action === "set_vpn_config") {
        vpnConfig.enabled = !!request.enabled;
        vpnConfig.host = request.host || "";
        vpnConfig.port = request.port || 0;
        vpnConfig.type = request.type || "http";
    }
}

// Listen to messages from content.js or internal calls
browser.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.action === "get_stats") {
        sendResponse({
            total: blockedStats.totalBlocked,
            tab: (sender.tab && sender.tab.id) ? (blockedStats.tabBlocked[sender.tab.id] || 0) : 0
        });
    } else if (request.action === "cosmetic_ads_blocked") {
        const count = request.count || 1;
        blockedStats.totalBlocked += count;
        const tabId = sender.tab ? sender.tab.id : -1;
        if (tabId >= 0) {
            blockedStats.tabBlocked[tabId] = (blockedStats.tabBlocked[tabId] || 0) + count;
        }
        notifyNative({
            action: "ad_blocked",
            url: sender.tab ? sender.tab.url : "",
            domain: "",
            tabBlockedCount: tabId >= 0 ? blockedStats.tabBlocked[tabId] : count,
            totalBlockedCount: blockedStats.totalBlocked
        });
    } else {
        handleConfigUpdate(request);
        sendResponse({ success: true });
    }
    return true;
});
