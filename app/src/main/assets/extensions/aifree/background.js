/**
 * AI Free & Hybrid Ad/Tracker Content Shield Engine
 * Combines high-speed in-engine WebRequest filtering with uBlock Origin & Dark Reader bridges.
 */

let blockedRules = new Set();
let tabBlockStats = {};
let totalBlocked = 0;

let adBlockingActive = true;
let nativePort = null;

// High-speed pre-compiled surrogate data URIs (0ms network time, fires script.onload without errors)
const SURROGATE_JS_NOOP = "data:application/javascript;charset=utf-8,%2F*%20noop%20*%2F";
const SURROGATE_ADSBYGOOGLE = "data:application/javascript;charset=utf-8,(function()%7Bwindow.adsbygoogle%3Dwindow.adsbygoogle%7C%7C%5B%5D%3Bwindow.adsbygoogle.loaded%3Dtrue%3Bwindow.adsbygoogle.push%3Dfunction(a)%7Bif(typeof%20a%3D%3D%3D'function')try%7Ba()%3B%7Dcatch(e)%7B%7D%7D%3B%7D)()%3B";
const SURROGATE_GPT = "data:application/javascript;charset=utf-8,(function()%7Bwindow.googletag%3Dwindow.googletag%7C%7C%7Bcmd%3A%5B%5D%7D%3Bwindow.googletag.apiReady%3Dtrue%3Bwindow.googletag.pubads%3Dfunction()%7Breturn%7BaddEventListener%3Afunction()%7B%7D%2CenableSingleRequest%3Afunction()%7B%7D%2CsetTargeting%3Afunction()%7B%7D%2CcollapseEmptyDivs%3Afunction()%7B%7D%2CclearTargeting%3Afunction()%7B%7D%2Crefresh%3Afunction()%7B%7D%2CdisableInitialLoad%3Afunction()%7B%7D%2CgetSlots%3Afunction()%7Breturn%5B%5D%3B%7D%7D%3B%7D%3Bwindow.googletag.enableServices%3Dfunction()%7B%7D%3Bwindow.googletag.display%3Dfunction()%7B%7D%3Bwindow.googletag.defineSlot%3Dfunction()%7Breturn%7BaddService%3Afunction()%7Breturn%20this%3B%7D%2CsetTargeting%3Afunction()%7Breturn%20this%3B%7D%2CsetCollapseEmptyDiv%3Afunction()%7Breturn%20this%3B%7D%7D%3B%7D%3B%7D)()%3B";
const SURROGATE_GA = "data:application/javascript;charset=utf-8,(function()%7Bwindow.ga%3Dwindow.ga%7C%7Cfunction()%7B%7D%3Bwindow.gtag%3Dwindow.gtag%7C%7Cfunction()%7B%7D%3Bwindow.dataLayer%3Dwindow.dataLayer%7C%7C%5B%5D%3B%7D)()%3B";
const SURROGATE_FBQ = "data:application/javascript;charset=utf-8,(function()%7Bwindow.fbq%3Dwindow.fbq%7C%7Cfunction()%7B%7D%3Bwindow._fbq%3Dwindow._fbq%7C%7Cwindow.fbq%3B%7D)()%3B";
const SURROGATE_1X1_GIF = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";

// Comprehensive built-in ad, tracker, popup, telemetry, and benchmark domain database
const coreBlocklist = [
    // Amazon Ads & S3 Endpoints
    "adtago.s3.amazonaws.com", "analyticsengine.s3.amazonaws.com", "advice-ads.s3.amazonaws.com",
    "advertising-api-eu.amazon.com", "amazon-adsystem.com", "aax.amazon-adsystem.com",
    "aan.amazon.com", "c.amazon-adsystem.com", "fls-na.amazon.com", "ir-na.amazon-adsystem.com",
    "rcm-na.amazon-adsystem.com", "ws-na.amazon-adsystem.com", "s3-ads.amazonaws.com",

    // Google Ads & DoubleClick
    "pagead2.googlesyndication.com", "adservice.google.com", "googleadservices.com",
    "pagead2.googleadservices.com", "afs.googlesyndication.com", "tpc.googlesyndication.com",
    "googletagservices.com", "googleads.g.doubleclick.net", "googleads4.g.doubleclick.net",
    "ad.doubleclick.net", "adclick.g.doubleclick.net", "cm.g.doubleclick.net",
    "stats.g.doubleclick.net", "static.doubleclick.net", "m.doubleclick.net",
    "mediavisor.doubleclick.net", "pubads.g.doubleclick.net", "securepubads.g.doubleclick.net",
    "pagead.l.doubleclick.net", "doubleclick.net", "googlesyndication.com",

    // Major Ad Networks & Exchanges
    "media.net", "static.media.net", "contextual.media.net", "adservetx.media.net",
    "ads30.adcolony.com", "adc3-launch.adcolony.com", "events3alt.adcolony.com", "wd.adcolony.com",
    "androidads23.adcolony.com", "adcolony.com",
    "criteo.com", "static.criteo.net", "bidder.criteo.com", "dis.criteo.com", "gum.criteo.com",
    "sslwidget.criteo.com", "criteo.net",
    "cdn.taboola.com", "trc.taboola.com", "images.taboola.com", "nr.taboola.com", "api.taboola.com", "taboola.com",
    "log.outbrain.com", "widgets.outbrain.com", "odb.outbrain.com", "outbrain.com",
    "mgid.com", "cdn.mgid.com", "servicer.mgid.com",
    "bat.bing.com", "bingads.microsoft.com", "ads.microsoft.com", "c.bing.com",
    "propellerads.com", "propellerclick.com", "onclickads.net",
    "applovin.com", "d.applovin.com", "rt.applovin.com", "ms.applovin.com", "pdn.applovin.com",
    "api.vungle.com", "vungle.com", "liftoff.io",
    "adnxs.com", "ib.adnxs.com", "secure.adnxs.com", "acdn.adnxs.com", "prebid.adnxs.com", "sin3-ib.adnxs.com",
    "pubmatic.com", "ads.pubmatic.com", "image6.pubmatic.com", "hbopenbid.pubmatic.com", "simage2.pubmatic.com",
    "openx.net", "us-ads.openx.net", "rtb.openx.net", "u.openx.net", "oas.openx.net",
    "rubiconproject.com", "pixel.rubiconproject.com", "fastlane.rubiconproject.com",
    "optimized-by.rubiconproject.com", "spotxchange.com", "search.spotxchange.com",
    "indexexchange.com", "casalemedia.com", "as.casalemedia.com", "cdn.indexexchange.com", "htlbid.com", "htlb.casalemedia.com",
    "ads.yahoo.com", "analytics.yahoo.com", "geo.yahoo.com", "udc.yahoo.com", "udcm.yahoo.com",
    "advertising.yahoo.com", "analytics.query.yahoo.com", "partnerads.ysm.yahoo.com", "log.fc.yahoo.com",
    "gemini.yahoo.com", "adtech.yahooinc.com", "adserver.yahoo.com",
    "auction.unityads.unity3d.com", "webview.unityads.unity3d.com", "config.unityads.unity3d.com",
    "adserver.unityads.unity3d.com", "unityads.unity3d.com",
    "metrika.yandex.ru", "adfox.yandex.ru", "adfstat.yandex.ru", "appmetrica.yandex.ru",
    "extmaps-api.yandex.net", "offerwall.yandex.net", "mc.yandex.ru", "advertising.yandex.ru",
    "an.yandex.ru", "yandex.ru/metrika",
    "live.chartboost.com", "init.supersonicads.com", "api.fyber.com", "inmobi.com", "config.inmobi.com",
    "ironsource.mobi", "outcome-ssp.supersonicads.com", "chartboost.com", "ironsrc.com", "supersonicads.com", "fyber.com",
    "ads.youtube.com",
    "adsrvr.org", "insights.adsrvr.org", "match.adsrvr.org", "smartyads.com", "ad.gt", "contextweb.com",
    "sharethrough.com", "stackadapt.com", "stickyadstv.com", "ads.stickyadstv.com", "eb2.3lift.com",
    "tlx.3lift.com", "apex.go.sonobi.com", "c.gumgum.com", "a.teads.tv", "cdn.teads.tv", "cdn.kargo.com",
    "sync.kargo.com", "triplelift.com", "yieldmo.com", "zemanta.com", "gumgum.com", "teads.tv",
    "d.adroll.com", "s.adroll.com", "adroll.com",
    "pangleglobal.com", "pangolin.snssdk.com", "pangle.io",
    "doubleverify.com", "cdn.doubleverify.com", "tps.doubleverify.com", "pixel.adsafeprotected.com",
    "static.adsafeprotected.com", "fw.adsafeprotected.com", "insightexpressai.com",
    "mintegral.com", "smaato.net", "adsmol.com", "ad-delivery.net", "ad-maven.com",
    "adpushup.com", "adskeeper.co.uk", "adthrive.com", "mediavine.com", "monetag.com",
    "adtrue.com", "adblade.com", "richaudience.com", "sovrn.com", "undertone.com",

    // Tracking, Telemetry & Analytics
    "analytics.google.com", "region1.google-analytics.com", "click.googleanalytics.com",
    "google-analytics.com", "ssl.google-analytics.com", "www.google-analytics.com",
    "googletagmanager.com", "tagmanager.google.com", "www.googletagmanager.com",
    "analytics.adobe.io", "omtrdc.net", "metrics.adobe.com",
    "clarity.ms", "c.clarity.ms", "s.clarity.ms",
    "adm.hotjar.com", "identify.hotjar.com", "insights.hotjar.com", "script.hotjar.com",
    "surveys.hotjar.com", "careers.hotjar.com", "events.hotjar.io", "static.hotjar.com",
    "vars.hotjar.com", "in.hotjar.com", "hotjar.com",
    "cdn.mouseflow.com", "o2.mouseflow.com", "gtm.mouseflow.com", "api.mouseflow.com",
    "tools.mouseflow.com", "cdn-test.mouseflow.com", "mouseflow.com",
    "claritybt.freshmarketer.com", "fwtracks.freshmarketer.com", "freshmarketer.com",
    "api.luckyorange.com", "realtime.luckyorange.com", "cdn.luckyorange.com", "w1.luckyorange.com",
    "upload.luckyorange.net", "cs.luckyorange.net", "settings.luckyorange.net", "luckyorange.com", "luckyorange.net",
    "stats.wp.com",
    "cdn.heapanalytics.com", "heapanalytics.com",
    "api.mixpanel.com", "api-js.mixpanel.com", "decide.mixpanel.com", "mixpanel.com", "cdn.mxpnl.com",
    "api.amplitude.com", "api2.amplitude.com", "amplitude.com",
    "cdn.segment.com", "api.segment.io", "events.segment.io", "segment.io",
    "rs.fullstory.com", "edge.fullstory.com", "fullstory.com",
    "quantserve.com", "pixel.quantserve.com", "quantcast.com", "edge.quantserve.com",
    "sb.scorecardresearch.com", "b.scorecardresearch.com", "scorecardresearch.com",
    "static.cloudflareinsights.com", "cloudflareinsights.com", "app.posthog.com",
    "eu.posthog.com", "us.i.posthog.com", "posthog.com", "rudderstack.com",
    "cdn.rudderlabs.com", "snowplowanalytics.com", "tracker.snowplowanalytics.com",

    // Tracking & Fingerprinting
    "fingerprintjs.com", "fpjs.io", "siftscience.com", "cdn.siftscience.com", "cdn.permutive.com", "permutive.com",
    "bluekai.com", "tags.bluekai.com", "onetag-sys.com", "pippio.com", "id5-sync.com",
    "sync.mathtag.com", "pixel.mathtag.com", "crwdcntrl.net", "bcp.crwdcntrl.net", "bidswitch.net",
    "sync-tm.everesttech.net", "prod.uidapi.com", "everesttech.net", "uidapi.com",
    "idsync.rlcdn.com", "api.rlcdn.com", "rlcdn.com",
    "tags.crwdcntrl.net", "ad.crwdcntrl.net", "sync.crwdcntrl.net",
    "app.appsflyer.com", "appsflyer.com", "app.adjust.com", "adjust.com", "api2.branch.io",
    "branch.io", "cdn.branch.io", "bnc.lt", "kochava.com", "control.kochava.com",
    "sdk-api-v1.singular.net", "singular.net", "wzrkt.com",

    // Error Trackers
    "notify.bugsnag.com", "sessions.bugsnag.com", "api.bugsnag.com", "app.bugsnag.com", "bugsnag.com",
    "browser.sentry-cdn.com", "app.getsentry.com", "ingest.sentry.io", "o0.ingest.sentry.io",
    "sentry.io", "js.sentry-cdn.com", "sentry-cdn.com",
    "bam.nr-data.net", "bam-cell.nr-data.net", "js-agent.newrelic.com", "newrelic.com",
    "browser-intake-datadoghq.com", "rum.browser-intake-datadoghq.com", "datadoghq.com",
    "cdn.lr-ingest.com", "r.lr-ingest.com", "logrocket.io", "cdn.logrocket.io", "r.logrocket.io",
    "raygun.io", "cdn.raygun.io", "api.raygun.io",

    // Cryptominers, Popups & Malvertising
    "coinimp.com", "www.coinimp.com", "webminepool.com", "minero.cc", "mineralt.io", "monerominer.rocks",
    "coinhive.com", "crypto-loot.com", "coin-have.com", "minr.pw", "authedmine.com", "browser-mining.com",
    "popads.net", "popcash.net", "popmyads.com", "clickadu.com", "trafficjunky.net", "trafficjunky.com",
    "exoclick.com", "juicyads.com", "adsterra.com", "adcash.com", "hilltopads.net", "yllix.com",
    "revenuehits.com", "bidvertiser.com", "chitika.com", "infolinks.com", "revcontent.com", "zergnet.com",
    "statdynamic.com",

    // Social Trackers
    "pixel.facebook.com", "an.facebook.com", "connect.facebook.net", "graph.facebook.com", "tr.facebook.com",
    "facebook.net", "facebook.com/tr",
    "graph.instagram.com", "i.instagram.com",
    "sc-static.net", "tr.snapchat.com", "ads.snapchat.com", "sc-analytics.appspot.com", "snapchat.com",
    "ads.linkedin.com", "analytics.pointdrive.linkedin.com", "snap.licdn.com", "px.ads.linkedin.com",
    "dc.ads.linkedin.com", "linkedin.com",
    "static.ads-twitter.com", "ads-api.twitter.com", "ads-api.x.com", "analytics.twitter.com",
    "analytics.x.com", "ads.x.com", "ads-twitter.com",
    "events.reddit.com", "events.redditmedia.com", "pixel.redditmedia.com", "d.reddit.com",
    "ads-api.tiktok.com", "analytics.tiktok.com", "ads-sg.tiktok.com", "analytics-sg.tiktok.com",
    "business-api.tiktok.com", "ads.tiktok.com", "log.byteoversea.com", "mon.byteoversea.com",
    "mcs-va.tiktokv.com", "mon.tiktokv.com",
    "ads.pinterest.com", "ct.pinterest.com", "log.pinterest.com", "analytics.pinterest.com",
    "trk.pinterest.com", "widgets.pinterest.com",
    "pixel.quora.com", "qevents.quora.com", "px.srvcs.tumblr.com",
    "ads.vk.com", "ad.mail.ru", "top-fwz1.mail.ru",

    // OEM Vendors & Telemetry
    "advertising.apple.com", "tr.iadsdk.apple.com", "iadsdk.apple.com", "metrics.icloud.com",
    "metrics.apple.com", "metrics.mzstatic.com", "api-adservices.apple.com", "books-analytics-events.apple.com",
    "notes-analytics-events.apple.com", "xp.apple.com",
    "iot-eu-logser.realme.com", "iot-logser.realme.com", "bdapi-ads.realmemobile.com", "bdapi-in-ads.realmemobile.com",
    "adsfs.oppomobile.com", "adx.ads.oppomobile.com", "ck.ads.oppomobile.com", "data.ads.oppomobile.com",
    "click.oneplus.cn", "open.oneplus.net",
    "metrics.data.hicloud.com", "metrics2.data.hicloud.com", "grs.hicloud.com", "logservice.hicloud.com",
    "logservice1.hicloud.com", "logbak.hicloud.com", "ads.huawei.com",
    "api.ad.xiaomi.com", "data.mistat.xiaomi.com", "data.mistat.india.xiaomi.com", "data.mistat.rus.xiaomi.com",
    "sdkconfig.ad.xiaomi.com", "sdkconfig.ad.intl.xiaomi.com", "globalapi.ad.xiaomi.com",
    "tracking.rus.miui.com", "tracking.miui.com",
    "samsungads.com", "smetrics.samsung.com", "nmetrics.samsung.com", "samsung-com.112.2o7.net",
    "analytics-api.samsunghealthcn.com", "config.samsungads.com",
    "adlog.vivo.com", "ads-api.vivo.com", "a.lenovo.com",
    "us.info.lgsmartad.com", "us.ibs.lgappstv.com", "ad.lgappstv.com", "info.lgsmartad.com",
    "ngfts.lge.com", "yumenetworks.com", "smartclip.net", "smartclip.com",
    "settings-win.data.microsoft.com", "vortex.data.microsoft.com", "vortex-win.data.microsoft.com",
    "watson.telemetry.microsoft.com", "telemetry.microsoft.com", "browser.events.data.msn.com",
    "device-metrics-us.amazon.com", "device-metrics-us-2.amazon.com", "mads-eu.amazon.com",
    "logs.roku.com", "ads.roku.com", "amoeba.web.roku.com", "ads.vizio.com", "tvinteractive.tv", "tvpixel.com",
    "firebase-settings.crashlytics.com",

    // Consent Management & CMP
    "cdn.cookielaw.org", "geolocation.onetrust.com", "privacyportal.onetrust.com",
    "consent.cookiebot.com", "consentcdn.cookiebot.com", "cookiebot.com", "consent.trustarc.com",
    "sdk.privacy-center.org", "cdn.privacy-mgmt.com", "app.usercentrics.eu", "cmp.usercentrics.eu",
    "cmp.inmobi.com", "sourcepoint.mgr.consensu.org", "cmp.osano.com", "fundingchoicesmessages.google.com",
    "onetrust.com", "cookielaw.org",

    // Affiliate Networks
    "www.anrdoezrs.net", "www.dpbolvw.net", "www.tkqlhce.com", "anrdoezrs.net", "dpbolvw.net", "tkqlhce.com",
    "shareasale.com", "click.linksynergy.com", "ad.linksynergy.com", "track.linksynergy.com", "linksynergy.com",
    "d.impactradius-event.com", "impactradius-event.com", "www.awin1.com", "zenaps.com", "prf.hn", "awin1.com",
    "partnerstack.com", "api.partnerstack.com", "refersion.com", "t.pepperjamnetwork.com", "pepperjamnetwork.com",
    "s.skimresources.com", "t.skimresources.com", "go.skimresources.com", "redirector.skimresources.com",
    "go.redirectingat.com", "skimresources.com", "redirectingat.com",
    "redirect.viglink.com", "cdn.viglink.com", "api.viglink.com", "viglink.com",

    // A/B Testing & Email/Chat Trackers
    "cdn.optimizely.com", "logx.optimizely.com", "api.optimizely.com", "cdn.dynamicyield.com",
    "st.dynamicyield.com", "events.launchdarkly.com", "clientstream.launchdarkly.com",
    "dynamicyield.com", "launchdarkly.com", "optimizely.com",
    "list-manage.com", "track.hubspot.com", "munchkin.marketo.net", "click.mailchimp.com",
    "widget.intercom.io", "js.driftt.com", "sdk.iad-01.braze.com", "cdn.onesignal.com",
    "api.onesignal.com", "static.klaviyo.com", "a.klaviyo.com", "customer.io", "track.customer.io",

    // Video Ads (VAST / VPAID / Players)
    "dai.google.com", "g.jwpsrv.com", "ssl.p.jwpcdn.com", "prd.jwpltx.com", "jwpsrv.com", "jwpcdn.com", "jwpltx.com",
    "mssl.fwmrm.net", "bea4.v.fwmrm.net", "2975c.v.fwmrm.net", "fwmrm.net",
    "cd.connatix.com", "capi.connatix.com", "vid.connatix.com", "connatix.com",
    "metrics.brightcove.com", "s.innovid.com", "innovid.com", "tremorhub.com", "ads.tremorhub.com"
];

// Initialize Set with core domains
coreBlocklist.forEach(domain => blockedRules.add(domain.toLowerCase().trim()));

// Load cached rules from storage
try {
    browser.storage.local.get(["cachedRules", "totalBlocked"], (res) => {
        if (res && res.cachedRules && Array.isArray(res.cachedRules)) {
            res.cachedRules.forEach(d => blockedRules.add(d.toLowerCase().trim()));
        }
        if (res && res.totalBlocked) {
            totalBlocked = res.totalBlocked;
        }
    });
} catch (e) {}

// High-performance URL Inspection & Decision Engine
const AD_PATTERN_PATH_LIST = [
    "/ads", "/ad", "/adserver", "/adserver/ads", "/pagead", "/pagead/ads",
    "/gampad/ads", "/doubleclick/ad", "/vast", "/vpaid", "/prebid", "/bidder",
    "/hb", "/ads.js", "/ad.js", "/pagead.js", "/advertisement.js", "/adsbygoogle.js",
    "/analytics/collect", "/tracking/pixel"
];

function shouldBlockOrRedirectUrl(urlStr, type) {
    if (!adBlockingActive) return null;
    let url;
    try {
        url = new URL(urlStr);
    } catch (e) {
        return null;
    }
    const host = url.hostname.toLowerCase();
    const pathname = url.pathname.toLowerCase();

    // Never block video playback streams
    if (host.includes("googlevideo.com") || (host.includes("youtube.com") && (pathname.includes("/videoplayback") || pathname.includes("/watch") || pathname.includes("/s/")))) {
        return null;
    }

    // 1. Scriptlet & Path Defusers (0ms surrogate responses to avoid broken page scripts)
    if (pathname.endsWith("/ads.js") || 
        pathname.endsWith("/pagead.js") || 
        pathname.endsWith("/advertisement.js") || 
        pathname.endsWith("/ad-banner.js") || 
        pathname.endsWith("/prebid.js") || 
        pathname.endsWith("/prebid-ads.js") ||
        pathname.endsWith("/ad.js")) {
        return { redirectUrl: SURROGATE_JS_NOOP };
    }

    if (host.includes("googlesyndication.com") && pathname.includes("/pagead/js/adsbygoogle.js")) {
        return { redirectUrl: SURROGATE_ADSBYGOOGLE };
    }

    if (host.includes("googletagservices.com") && (pathname.includes("/gpt.js") || pathname.includes("/tag/js/gpt.js"))) {
        return { redirectUrl: SURROGATE_GPT };
    }

    if ((host.includes("google-analytics.com") || host.includes("googletagmanager.com")) && 
        (pathname.includes("/analytics.js") || pathname.includes("/gtag/js") || pathname.includes("/gtm.js"))) {
        return { redirectUrl: SURROGATE_GA };
    }

    if (host.includes("connect.facebook.net") && pathname.includes("fbevents.js")) {
        return { redirectUrl: SURROGATE_FBQ };
    }

    // 2. Direct & Suffix Domain Matching
    const parts = host.split(".");
    for (let i = 0; i < parts.length - 1; i++) {
        const checkDomain = parts.slice(i).join(".");
        if (blockedRules.has(checkDomain)) {
            if (type === "image") return { redirectUrl: SURROGATE_1X1_GIF };
            if (type === "script") return { redirectUrl: SURROGATE_JS_NOOP };
            return { cancel: true };
        }
    }

    // 3. Amazon S3 Ad/Analytics Buckets
    if (host.endsWith(".amazonaws.com")) {
        const s3Prefix = parts[0];
        if (s3Prefix.includes("ad") || s3Prefix.includes("analytics") || s3Prefix.includes("telemetry") || s3Prefix.includes("tracker")) {
            if (type === "image") return { redirectUrl: SURROGATE_1X1_GIF };
            if (type === "script") return { redirectUrl: SURROGATE_JS_NOOP };
            return { cancel: true };
        }
    }

    // 4. Benchmark AD_PATTERN_PATHS checks (e.g. /gampad/ads, /tracking/pixel, etc.)
    for (let p of AD_PATTERN_PATH_LIST) {
        if (pathname.includes(p)) {
            if (type === "image") return { redirectUrl: SURROGATE_1X1_GIF };
            if (type === "script") return { redirectUrl: SURROGATE_JS_NOOP };
            return { cancel: true };
        }
    }

    // 5. Common Ad / Tracking file paths
    if (pathname.includes("/pagead/") || pathname.includes("/adserver/") || pathname.includes("/pixel.gif") || pathname.includes("/beacon.gif")) {
        if (type === "image") return { redirectUrl: SURROGATE_1X1_GIF };
        if (type === "script") return { redirectUrl: SURROGATE_JS_NOOP };
        return { cancel: true };
    }

    return null;
}

// Intercept WebRequests Before They Hit the Network
if (browser.webRequest && browser.webRequest.onBeforeRequest) {
    browser.webRequest.onBeforeRequest.addListener(
        (details) => {
            const result = shouldBlockOrRedirectUrl(details.url, details.type);
            if (result) {
                totalBlocked++;
                const tabId = details.tabId;
                if (tabId && tabId >= 0) {
                    tabBlockStats[tabId] = (tabBlockStats[tabId] || 0) + 1;
                    notifyNative({
                        action: "blocked_count",
                        tabId: tabId,
                        count: tabBlockStats[tabId],
                        total: totalBlocked
                    });
                }
                return result;
            }
            return {};
        },
        { urls: ["<all_urls>"] },
        ["blocking"]
    );
}

// Reset tab stats on navigation
if (browser.webNavigation && browser.webNavigation.onCommitted) {
    browser.webNavigation.onCommitted.addListener((details) => {
        if (details.frameId === 0 && details.tabId >= 0) {
            tabBlockStats[details.tabId] = 0;
            notifyNative({
                action: "blocked_count",
                tabId: details.tabId,
                count: 0,
                total: totalBlocked
            });
        }
    });
}

// Universal Network Media Sniffer (Cat-Catch Network Layer)
const MEDIA_EXT_REGEX = /\.(mp4|webm|mkv|m4v|mov|m3u8|mpd|m4a|mp3|aac|opus|flv|f4v)($|\?|#)/i;
const KNOWN_STREAM_REGEX = /googlevideo\.com\/videoplayback|v\.redd\.it\/|hotstar\.com.*\.m3u8|fbcdn\.net\/.*\.mp4|twimg\.com\/.*\.mp4|tiktokcdn\.com\/|byteoversea\.com\/|akamaihd\.net\/|cloudfront\.net\/.*(m3u8|mp4)|fastly\.net\/.*(m3u8|mp4)|vimeocdn\.com\/|dailymotion\.com\/.*\.m3u8/i;
const detectedMediaUrls = new Set();

if (browser.webRequest && browser.webRequest.onHeadersReceived) {
    browser.webRequest.onHeadersReceived.addListener(
        (details) => {
            try {
                if (!details || details.tabId < 0) return;
                const url = details.url;
                if (!url || url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("about:") || detectedMediaUrls.has(url)) return;

                // Ignore chunk segments (.ts, .m4s) to avoid spamming tiny fragments
                if (url.includes(".ts?") || url.endsWith(".ts") || url.includes(".m4s?") || url.endsWith(".m4s")) {
                    return;
                }

                const isMediaExt = MEDIA_EXT_REGEX.test(url);
                const isKnownStream = KNOWN_STREAM_REGEX.test(url);

                let contentType = "";
                let contentLength = 0;

                // Inspect responseHeaders if available
                if (details.responseHeaders) {
                    for (const header of details.responseHeaders) {
                        const name = (header.name || "").toLowerCase();
                        if (name === "content-type") {
                            contentType = (header.value || "").toLowerCase();
                        } else if (name === "content-length") {
                            contentLength = parseInt(header.value, 10) || 0;
                        }
                    }
                }

                const isVideoMime = contentType.startsWith("video/") ||
                                    contentType.includes("application/x-mpegurl") ||
                                    contentType.includes("vnd.apple.mpegurl") ||
                                    contentType.includes("application/dash+xml") ||
                                    contentType.includes("video/mp4") ||
                                    contentType.includes("video/webm");
                const isAudioMime = contentType.startsWith("audio/") || contentType.includes("audio/x-m4a");

                if (isVideoMime || isAudioMime || isMediaExt || isKnownStream || details.type === "media") {
                    if (contentLength > 0 && contentLength < 50000 && !url.includes(".m3u8") && !url.includes(".mpd") && !isKnownStream) {
                        return;
                    }

                    detectedMediaUrls.add(url);
                    notifyNative({
                        action: "media_detected",
                        tabId: details.tabId,
                        url: url,
                        mimeType: contentType || (isMediaExt ? "video/mp4" : "application/x-mpegURL"),
                        contentLength: contentLength,
                        isAudio: isAudioMime && !isVideoMime,
                        type: details.type || "media"
                    });
                }
            } catch (e) {}
            return {};
        },
        { urls: ["<all_urls>"] },
        ["responseHeaders", "blocking"]
    );
}

if (browser.webNavigation && browser.webNavigation.onCommitted) {
    browser.webNavigation.onCommitted.addListener((details) => {
        if (details.frameId === 0) {
            detectedMediaUrls.clear();
        }
    });
}

// Native Port Bridge
let isConnecting = false;

function connectToNativeBridge() {
    if (isConnecting) return;
    isConnecting = true;
    try {
        if (browser.runtime && browser.runtime.connectNative) {
            nativePort = browser.runtime.connectNative("browser_app");
            nativePort.onMessage.addListener((msg) => {
                handleConfigUpdate(msg);
            });
            nativePort.onDisconnect.addListener(() => {
                nativePort = null;
                isConnecting = false;
                setTimeout(connectToNativeBridge, 2000);
            });
            isConnecting = false;
        }
    } catch (e) {
        nativePort = null;
        isConnecting = false;
    }
}

function notifyNative(msg) {
    try {
        if (nativePort) {
            nativePort.postMessage(msg);
        } else if (browser.runtime && browser.runtime.sendNativeMessage) {
            browser.runtime.sendNativeMessage("browser_app", msg).catch(() => {});
            connectToNativeBridge();
        } else {
            connectToNativeBridge();
        }
    } catch (e) {
        if (browser.runtime && browser.runtime.sendNativeMessage) {
            browser.runtime.sendNativeMessage("browser_app", msg).catch(() => {});
        }
    }
}

function handleConfigUpdate(msg) {
    if (!msg) return;

    // Dark Reader toggle — forward to Dark Reader extension
    if (msg.action === "set_dark_mode") {
        browser.runtime.sendMessage(
            "addon@darkreader.org",
            {
                type: "change-settings",
                settings: { enabled: !!msg.enabled }
            }
        ).catch(() => {});
    }

    // Ad blocking toggle
    if (msg.action === "set_ad_blocking") {
        adBlockingActive = !!msg.enabled;
        browser.runtime.sendMessage(
            "uBlock0@raymondhill.net",
            { what: adBlockingActive ? "restoreAll" : "suspendAll" }
        ).catch(() => {});
    }
}

// Listen for messages from content scripts (download_blob, share_payload, video_detected, etc.) and forward to native app
browser.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    if (msg) {
        if (sender && sender.tab && sender.tab.id >= 0 && !msg.tabId) {
            msg.tabId = sender.tab.id;
        }
        notifyNative(msg);
    }
    return true;
});

connectToNativeBridge();

// Remote Blocklist Auto-Update (24h TTL)
async function fetchRemoteBlocklistUpdate() {
    const CACHE_KEY = "remote_blocklist_cache";
    const TS_KEY = "remote_blocklist_ts";
    const TTL = 24 * 60 * 60 * 1000;
    const UPDATE_URL = "https://raw.githubusercontent.com/Bharathsencha/Gecko/main/blocklist-update.json";

    try {
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
    } catch (e) {}
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

fetchRemoteBlocklistUpdate();
