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
    type: "socks",
    host: "",
    port: 0,
    countryCode: "US"
};

// High-speed pre-compiled surrogate data URIs (0ms network time, fires script.onload without network errors)
const SURROGATE_JS_NOOP = "data:application/javascript;charset=utf-8,%2F*%20noop%20*%2F";
const SURROGATE_ADSBYGOOGLE = "data:application/javascript;charset=utf-8,(function()%7Bwindow.adsbygoogle%3Dwindow.adsbygoogle%7C%7C%5B%5D%3Bwindow.adsbygoogle.loaded%3Dtrue%3Bwindow.adsbygoogle.push%3Dfunction(a)%7Bif(typeof%20a%3D%3D%3D'function')try%7Ba()%3B%7Dcatch(e)%7B%7D%7D%3B%7D)()%3B";
const SURROGATE_GPT = "data:application/javascript;charset=utf-8,(function()%7Bwindow.googletag%3Dwindow.googletag%7C%7C%7Bcmd%3A%5B%5D%7D%3Bwindow.googletag.apiReady%3Dtrue%3Bwindow.googletag.pubads%3Dfunction()%7Breturn%7BaddEventListener%3Afunction()%7B%7D%2CenableSingleRequest%3Afunction()%7B%7D%2CsetTargeting%3Afunction()%7B%7D%2CcollapseEmptyDivs%3Afunction()%7B%7D%2CclearTargeting%3Afunction()%7B%7D%2Crefresh%3Afunction()%7B%7D%2CdisableInitialLoad%3Afunction()%7B%7D%2CgetSlots%3Afunction()%7Breturn%5B%5D%3B%7D%7D%3B%7D%3Bwindow.googletag.enableServices%3Dfunction()%7B%7D%3Bwindow.googletag.display%3Dfunction()%7B%7D%3Bwindow.googletag.defineSlot%3Dfunction()%7Breturn%7BaddService%3Afunction()%7Breturn%20this%3B%7D%2CsetTargeting%3Afunction()%7Breturn%20this%3B%7D%2CsetCollapseEmptyDiv%3Afunction()%7Breturn%20this%3B%7D%7D%3B%7D%3B%7D)()%3B";
const SURROGATE_GA = "data:application/javascript;charset=utf-8,(function()%7Bwindow.ga%3Dwindow.ga%7C%7Cfunction()%7B%7D%3Bwindow.gtag%3Dwindow.gtag%7C%7Cfunction()%7B%7D%3Bwindow.dataLayer%3Dwindow.dataLayer%7C%7C%5B%5D%3B%7D)()%3B";
const SURROGATE_FBQ = "data:application/javascript;charset=utf-8,(function()%7Bwindow.fbq%3Dwindow.fbq%7C%7Cfunction()%7B%7D%3Bwindow._fbq%3Dwindow._fbq%7C%7Cwindow.fbq%3B%7D)()%3B";
const SURROGATE_1X1_GIF = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";

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
    
    // Tracking, Telemetry & Error Loggers
    "google-analytics.com", "analytics.google.com", "statcounter.com", "yandex.ru/metrika",
    "mc.yandex.ru", "clarity.ms", "segment.io", "mixpanel.com", "amplitude.com",
    "newrelic.com", "nr-data.net", "branch.io", "appsflyer.com", "adjust.com",
    "facebook.net/en_US/fbevents.js", "connect.facebook.net/signals", "ads-twitter.com",
    "bat.bing.com", "ads.linkedin.com", "tr.snapchat.com", "analytics.tiktok.com",
    "sentry.io", "browser.sentry-cdn.com", "js.sentry-cdn.com", "sentry-cdn.com",
    "bugsnag.com", "notify.bugsnag.com", "sessions.bugsnag.com", "d2wy8f7a9urs9l.cloudfront.net", "d2wy8f7a9ursnm.cloudfront.net",
    "logrocket.io", "cdn.logrocket.io", "r.logrocket.io",
    "raygun.io", "cdn.raygun.io", "api.raygun.io",
    "static.hotjar.com", "script.hotjar.com", "vars.hotjar.com",
    "js-agent.newrelic.com", "bam.nr-data.net",
    "googletagmanager.com", "matomo.js", "piwik.js",
    "ymatuhin.ru", "an.yandex.ru", "context.js",
    
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

function recordBlock(details, host) {
    blockedStats.totalBlocked++;
    blockedStats.sessionBlocked++;

    const tabId = details.tabId;
    if (tabId !== undefined && tabId >= 0) {
        blockedStats.tabBlocked[tabId] = (blockedStats.tabBlocked[tabId] || 0) + 1;
    }

    notifyNative({
        action: "ad_blocked",
        url: details.url,
        domain: host,
        tabBlockedCount: tabId >= 0 ? blockedStats.tabBlocked[tabId] : 1,
        totalBlockedCount: blockedStats.totalBlocked
    });
}

// 1. High-Efficiency Request Interception, Surrogate Redirection & Anti-Bait Engine
browser.webRequest.onBeforeRequest.addListener(
    function(details) {
        try {
            const urlObj = new URL(details.url);
            const host = urlObj.hostname.toLowerCase();
            const fullUrl = details.url.toLowerCase();

            if (!adBlockingActive) {
                return { cancel: false };
            }

            // 1. Direct Host match check
            let matched = isBlocked(host);

            // 2. Comprehensive pattern heuristics for banner ads, tracker scripts, analytics, and error catchers (subresources only)
            if (!matched && details.type !== "main_frame") {
                if (fullUrl.includes("/ads.js") ||
                    fullUrl.includes("/adservice.") ||
                    fullUrl.includes("/pagead/") ||
                    fullUrl.includes("adsbygoogle") ||
                    fullUrl.includes("/gpt.js") ||
                    fullUrl.includes("pubads_impl") ||
                    fullUrl.includes("/google-analytics.com/") ||
                    fullUrl.includes("/googletagmanager.com/") ||
                    fullUrl.includes("analytics.js") ||
                    fullUrl.includes("gtag/js") ||
                    fullUrl.includes("/ga.js") ||
                    fullUrl.includes("/fbevents.js") ||
                    fullUrl.includes("/sentry.") ||
                    fullUrl.includes("/sentry-") ||
                    fullUrl.includes("sentry-cdn.com") ||
                    fullUrl.includes("bundle.tracing") ||
                    fullUrl.includes("bugsnag") ||
                    fullUrl.includes("logrocket") ||
                    fullUrl.includes("raygun") ||
                    fullUrl.includes("hotjar") ||
                    fullUrl.includes("clarity.ms") ||
                    fullUrl.includes("matomo.js") ||
                    fullUrl.includes("piwik.js") ||
                    fullUrl.includes("/metrika/") ||
                    fullUrl.includes("/mc.yandex.") ||
                    fullUrl.includes("context.js") ||
                    fullUrl.includes(".swf") ||
                    fullUrl.includes("pr_advertising") ||
                    fullUrl.includes("banner_300x250") ||
                    fullUrl.includes("banner_728x90") ||
                    fullUrl.includes("banner_160x600") ||
                    fullUrl.includes("banner_468x60") ||
                    fullUrl.includes("banner_320x50") ||
                    fullUrl.includes("banner_300x600") ||
                    fullUrl.includes("banner_970x250") ||
                    fullUrl.includes("/banners/") ||
                    fullUrl.includes("/banner_") ||
                    fullUrl.includes("/banner-") ||
                    fullUrl.includes("/ads/") ||
                    fullUrl.includes("/ad_") ||
                    fullUrl.includes("/ad-") ||
                    fullUrl.includes("_ad_") ||
                    fullUrl.includes("_ads_") ||
                    fullUrl.includes("/advert") ||
                    fullUrl.includes("/popunder") ||
                    fullUrl.includes("/clickunder") ||
                    fullUrl.includes("popup.js") ||
                    fullUrl.includes("/pixel") ||
                    fullUrl.includes("/tr?") ||
                    fullUrl.includes("ad_type=") ||
                    fullUrl.includes("/ad_status.") ||
                    fullUrl.includes("/showads.")) {
                    matched = true;
                }
            }

            if (matched) {
                recordBlock(details, host);
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
                    type: vpnConfig.type || "socks",
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
        vpnConfig.type = request.type || "socks";
        vpnConfig.countryCode = request.countryCode || "US";

        // Broadcast updated VPN country to all open tabs
        if (browser.tabs && browser.tabs.query) {
            browser.tabs.query({}).then((tabs) => {
                for (const t of tabs) {
                    if (t.id) {
                        browser.tabs.sendMessage(t.id, {
                            action: "update_vpn_country",
                            countryCode: vpnConfig.countryCode,
                            enabled: vpnConfig.enabled
                        }).catch(() => {});
                    }
                }
            }).catch(() => {});
        }
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

// Remote blocklist auto-update (24h TTL, silent fail)
async function fetchRemoteBlocklistUpdate() {
    const CACHE_KEY = "remote_blocklist_cache";
    const TS_KEY = "remote_blocklist_ts";
    const TTL = 24 * 60 * 60 * 1000; // 24 hours
    const UPDATE_URL = "https://raw.githubusercontent.com/Bharathsencha/Gecko/main/blocklist-update.json";

    try {
        if (!browser.storage || !browser.storage.local) return;
        const stored = await browser.storage.local.get([CACHE_KEY, TS_KEY]);
        const cachedTs = stored[TS_KEY] || 0;
        const now = Date.now();

        if (stored[CACHE_KEY] && (now - cachedTs) < TTL) {
            applyRemoteBlocklist(stored[CACHE_KEY]);
            return;
        }

        const response = await fetch(UPDATE_URL, { cache: "no-store" });
        if (!response.ok) return;
        const data = await response.json();

        await browser.storage.local.set({ [CACHE_KEY]: data, [TS_KEY]: now });
        applyRemoteBlocklist(data);
    } catch (e) {
        // Silent fail — built-in blocklist remains active
    }
}

function applyRemoteBlocklist(data) {
    if (!data) return;
    if (Array.isArray(data.domains)) {
        data.domains.forEach(d => {
            if (typeof d === "string" && d.length > 0) {
                blockedRules.add(d.toLowerCase().trim());
            }
        });
    }
}

// Initialize remote blocklist update on startup
fetchRemoteBlocklistUpdate();

