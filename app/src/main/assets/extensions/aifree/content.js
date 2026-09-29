/**
 * AI Free Content Script
 * Handles: Fast Cosmetic Ad-Hiding, Scriptlet Surrogates, Google AI Overview removal, Reader mode, SPA Scroll, Media
 */

// Cosmetic Ad Hiding & Scriptlet Surrogates
const COSMETIC_AD_SELECTORS = [
    ".adsbygoogle",
    "[id*='google_ads']",
    "[id^='ad-']",
    "[id*='_ad_']",
    "[class*='ad-banner']",
    "[class*='dynamic-ad']",
    "[class*='ad-placement']",
    "[class*='ad-container']",
    "[class*='ad-wrapper']",
    "[data-ad-client]",
    "[data-ad-slot]",
    ".taboola-ad",
    ".outbrain-ad",
    "iframe[src*='doubleclick']",
    "iframe[src*='googlesyndication']",
    "iframe[src*='amazon-adsystem']",
    ".banner-ad",
    ".header-ad",
    ".sidebar-ad",
    ".sponsored-post",
    "[aria-label='advertisement' i]",
    "[aria-label='sponsored' i]"
].join(", ");

function injectCosmeticAdBlockStyle() {
    let style = document.getElementById("aifree-cosmetic-ad-block");
    if (!style) {
        style = document.createElement("style");
        style.id = "aifree-cosmetic-ad-block";
        style.textContent = `
            ${COSMETIC_AD_SELECTORS} {
                display: none !important;
                visibility: hidden !important;
                opacity: 0 !important;
                height: 0 !important;
                min-height: 0 !important;
                width: 0 !important;
                min-width: 0 !important;
                pointer-events: none !important;
            }
        `;
        (document.head || document.documentElement).appendChild(style);
    }
}

function injectScriptletSurrogates() {
    try {
        const script = document.createElement("script");
        script.textContent = `(function() {
            try {
                // AdsByGoogle stub
                window.adsbygoogle = window.adsbygoogle || [];
                window.adsbygoogle.loaded = true;
                window.adsbygoogle.push = function(a) {
                    if (typeof a === 'function') {
                        try { a(); } catch (e) {}
                    }
                };

                // Google Publisher Tag (GPT) stub
                window.googletag = window.googletag || { cmd: [] };
                window.googletag.apiReady = true;
                window.googletag.pubads = function() {
                    return {
                        addEventListener: function() {},
                        enableSingleRequest: function() {},
                        setTargeting: function() {},
                        collapseEmptyDivs: function() {},
                        clearTargeting: function() {},
                        refresh: function() {},
                        disableInitialLoad: function() {},
                        getSlots: function() { return []; }
                    };
                };
                window.googletag.enableServices = function() {};
                window.googletag.display = function() {};
                window.googletag.defineSlot = function() {
                    return {
                        addService: function() { return this; },
                        setTargeting: function() { return this; },
                        setCollapseEmptyDiv: function() { return this; }
                    };
                };

                // Google Analytics / GTag stub
                window.ga = window.ga || function() {};
                window.gtag = window.gtag || function() {};
                window.dataLayer = window.dataLayer || [];

                // Facebook Pixel stub
                window.fbq = window.fbq || function() {};
                window._fbq = window._fbq || window.fbq;
            } catch (e) {}
        })();`;
        (document.head || document.documentElement).appendChild(script);
        script.remove();
    } catch (e) {}
}

// Google AI Overview Blocker
const AI_OVERVIEW_SELECTORS = [
    "#Odp5De",
    "div[jsname='yDeuDf']",
    ".hdzaWe",
    "div[jscontroller*='GenerativeSearch']",
    "div[aria-label*='AI Overview']",
    "div[aria-label*='AI overview']",
    "div[data-attrid*='generative']",
    "[data-sge-block='true']",
    "#genai_carousel",
    "#gds-aic",
    "div[data-aioc='true']",
    "div[data-aipc='true']",
    "div[class*='ai-overview']",
    "[id*='ai-overview']"
].join(", ");

function injectAiOverrideStyle() {
    const host = (window.location.hostname || "").toLowerCase();
    if (!host.includes("google.")) return;

    let style = document.getElementById("aifree-ai-block");
    if (!style) {
        style = document.createElement("style");
        style.id = "aifree-ai-block";
        style.textContent = `
            ${AI_OVERVIEW_SELECTORS} { 
                display: none !important; 
            }
            /* Protect Google Search accordion and expandable chevron SVG icons from scaling out of bounds */
            g-accordion-expander svg, 
            div[jscontroller] svg:not([width]), 
            .MjjYud svg:not([width]) {
                max-width: 24px !important;
                max-height: 24px !important;
            }
        `;
        (document.head || document.documentElement).appendChild(style);
    }
}

function removeAiNodes(root) {
    const host = (window.location.hostname || "").toLowerCase();
    if (!host.includes("google.")) return;
    try {
        root.querySelectorAll(AI_OVERVIEW_SELECTORS).forEach(el => el.remove());
    } catch (e) {}
}

function watchForGoogleAiOverview() {
    const host = (window.location.hostname || "").toLowerCase();
    if (!host.includes("google.")) return;

    injectAiOverrideStyle();
    removeAiNodes(document);

    const observer = new MutationObserver((mutations) => {
        for (const m of mutations) {
            if (m.addedNodes.length > 0) {
                removeAiNodes(document);
                break;
            }
        }
    });
    observer.observe(document.documentElement, { childList: true, subtree: true });
}

// SPA Scroll Restoration Engine (YouTube, Twitter, Reddit, etc.)
function initSpaScrollRestoration() {
    const scrollMap = new Map();
    let isRestoring = false;

    function getScrollY() {
        return window.scrollY || document.documentElement.scrollTop || (document.body ? document.body.scrollTop : 0);
    }

    function saveCurrentPos() {
        const y = getScrollY();
        if (y > 0 && !location.pathname.startsWith('/watch')) {
            scrollMap.set(location.href, y);
            try {
                sessionStorage.setItem('__spa_scroll_' + location.pathname, y.toString());
            } catch (e) {}
        }
    }

    // Capture scroll continuously
    window.addEventListener('scroll', () => {
        if (!isRestoring) {
            saveCurrentPos();
        }
    }, { passive: true });

    // Capture on clicks (before SPA navigation begins)
    document.addEventListener('click', () => {
        saveCurrentPos();
    }, true);

    function restoreScrollForCurrentPage() {
        const targetUrl = location.href;
        let savedY = scrollMap.get(targetUrl);
        if (savedY === undefined) {
            try {
                const stored = sessionStorage.getItem('__spa_scroll_' + location.pathname);
                if (stored) savedY = parseInt(stored, 10);
            } catch (e) {}
        }

        if (savedY !== undefined && savedY > 0) {
            isRestoring = true;
            let attempts = 0;
            const maxAttempts = 25;

            function tryScroll() {
                attempts++;
                const docH = Math.max(
                    document.documentElement.scrollHeight,
                    document.body ? document.body.scrollHeight : 0
                );

                if (docH >= savedY || attempts >= maxAttempts) {
                    window.scrollTo({ top: savedY, behavior: 'instant' });
                    if (document.documentElement) document.documentElement.scrollTop = savedY;
                    if (document.body) document.body.scrollTop = savedY;

                    setTimeout(() => { isRestoring = false; }, 250);
                } else {
                    requestAnimationFrame(tryScroll);
                }
            }

            requestAnimationFrame(tryScroll);
            setTimeout(tryScroll, 150);
            setTimeout(tryScroll, 400);
        }
    }

    // Restore on popstate (back button) & YouTube custom SPA navigation
    window.addEventListener('popstate', restoreScrollForCurrentPage);
    window.addEventListener('yt-navigate-finish', () => {
        setTimeout(restoreScrollForCurrentPage, 100);
    });
}

// Background Audio & Video Playback Engine
function initBackgroundMediaPlayback() {
    try {
        const script = document.createElement('script');
        script.textContent = `(function() {
            try {
                Object.defineProperty(document, 'hidden', { get: () => false, configurable: true });
                Object.defineProperty(document, 'visibilityState', { get: () => 'visible', configurable: true });
                Object.defineProperty(document, 'webkitVisibilityState', { get: () => 'visible', configurable: true });
            } catch(e) {}
        })();`;
        (document.head || document.documentElement).appendChild(script);
        script.remove();
    } catch (e) {}
}

// Reader Mode Article Extraction
function extractArticleContent(doc) {
    try {
        const documentClone = doc.cloneNode(true);
        ["script","style","nav","header","footer","aside",
         ".comments",".comment-section",".sidebar",".ad-container",
         ".social-share",".newsletter-signup",".cookie-banner",
         "[role='banner']","[role='navigation']","[role='complementary']"
        ].forEach(sel => {
            documentClone.querySelectorAll(sel).forEach(n => n.remove());
        });

        let articleNode = documentClone.querySelector("article") ||
                          documentClone.querySelector("main") ||
                          documentClone.querySelector("[role='main']") ||
                          documentClone.querySelector(".post-content") ||
                          documentClone.querySelector(".article-body") ||
                          documentClone.querySelector(".entry-content");

        if (!articleNode) {
            const candidates = documentClone.querySelectorAll("div, section");
            let bestCandidate = null, maxScore = 0;
            candidates.forEach(el => {
                const paragraphs = el.querySelectorAll("p");
                const textLength = el.textContent ? el.textContent.trim().length : 0;
                const linkLength = Array.from(el.querySelectorAll("a"))
                    .reduce((acc, a) => acc + (a.textContent ? a.textContent.length : 0), 0);
                const linkDensity = textLength > 0 ? (linkLength / textLength) : 1;
                const score = (paragraphs.length * 20 + textLength * 0.1) * (1 - linkDensity);
                if (score > maxScore) { maxScore = score; bestCandidate = el; }
            });
            articleNode = bestCandidate || documentClone.body;
        }

        articleNode.querySelectorAll("img").forEach(img => {
            if (img.src) {
                try { img.src = new URL(img.src, window.location.href).href; } catch(e) {}
            }
            img.removeAttribute("srcset");
            img.style.maxWidth = "100%";
            img.style.height = "auto";
        });

        const h1 = doc.querySelector("h1");
        const title = (h1 && h1.textContent.trim()) ? h1.textContent.trim() : (doc.title || "Article");
        const authorEl = doc.querySelector("[rel='author'], .byline, .author, meta[name='author']");
        const byline = authorEl ? (authorEl.getAttribute("content") || authorEl.textContent.trim()) : "";

        return {
            title,
            byline,
            contentHtml: articleNode.innerHTML,
            isArticle: (articleNode.textContent || "").trim().length > 250
        };
    } catch (e) {
        return { title: doc.title || "", byline: "", contentHtml: doc.body ? doc.body.innerHTML : "", isArticle: false };
    }
}

// Client-Side Blob / Data URI Download Interceptor
function initClientDownloadInterceptor() {
    async function handleDownloadElement(el) {
        try {
            let href = el.getAttribute("href") || el.href || "";
            let downloadAttr = el.getAttribute("download") || "";

            if (href.startsWith("blob:") || href.startsWith("data:")) {
                let filename = downloadAttr || "";
                let mimeType = "";

                if (href.startsWith("data:")) {
                    const match = href.match(/^data:([^;,]+)/);
                    if (match) mimeType = match[1];
                    if (!filename) {
                        const ext = mimeType.includes("/") ? mimeType.split("/")[1] : "txt";
                        filename = "export_" + Date.now() + "." + ext;
                    }
                    browser.runtime.sendMessage({
                        action: "download_blob",
                        dataUrl: href,
                        filename: filename,
                        mimeType: mimeType
                    }).catch(() => {});
                } else if (href.startsWith("blob:")) {
                    const response = await fetch(href);
                    const blob = await response.blob();
                    mimeType = blob.type;
                    if (!filename) {
                        const ext = mimeType.includes("/") ? mimeType.split("/")[1] : "bin";
                        filename = "export_" + Date.now() + "." + ext;
                    }
                    const reader = new FileReader();
                    reader.onloadend = function() {
                        const base64Data = reader.result;
                        browser.runtime.sendMessage({
                            action: "download_blob",
                            dataUrl: base64Data,
                            filename: filename,
                            mimeType: mimeType
                        }).catch(() => {});
                    };
                    reader.readAsDataURL(blob);
                }
            }
        } catch (err) {}
    }

    document.addEventListener("click", (e) => {
        const target = e.target;
        if (!target) return;
        const link = target.closest("a[download], a[href^='blob:'], a[href^='data:']");
        if (link) {
            handleDownloadElement(link);
        }
    }, true);
}

// Web Share API Native Chooser Bridge
function initWebShareBridge() {
    try {
        const script = document.createElement("script");
        script.textContent = `(function() {
            try {
                const handler = async function(data) {
                    window.postMessage({
                        type: "__gecko_web_share__",
                        title: (data && data.title) || document.title || "",
                        text: (data && data.text) || "",
                        url: (data && data.url) || window.location.href
                    }, "*");
                    return Promise.resolve();
                };
                if (navigator.share) {
                    navigator.share = handler;
                } else {
                    Object.defineProperty(navigator, 'share', {
                        value: handler,
                        configurable: true,
                        writable: true
                    });
                }
            } catch (e) {}
        })();`;
        (document.head || document.documentElement).appendChild(script);
        script.remove();

        window.addEventListener("message", (event) => {
            if (event.data && event.data.type === "__gecko_web_share__") {
                browser.runtime.sendMessage({
                    action: "share_payload",
                    title: event.data.title,
                    text: event.data.text,
                    url: event.data.url
                }).catch(() => {});
            }
        });
    } catch (e) {}
}

// Universal Video & Media Sniffer Engine (Cat-Catch In-Browser Architecture)
function initUniversalMediaSniffer() {
    let lastScanTime = 0;

    function reportMedia(videoData) {
        if (!videoData || !videoData.qualities || videoData.qualities.length === 0) return;
        try {
            if (browser.runtime && browser.runtime.sendMessage) {
                browser.runtime.sendMessage({
                    action: "video_detected",
                    data: videoData
                }).catch(() => {});
            }
        } catch (e) {}
        try {
            if (browser.runtime && browser.runtime.sendNativeMessage) {
                browser.runtime.sendNativeMessage("browser_app", {
                    action: "video_detected",
                    data: videoData
                }).catch(() => {});
            }
        } catch (e) {}
    }

    // 1. Injected Player Hook & Fetch / XHR / MediaSource Interceptor
    try {
        const hookScript = document.createElement("script");
        hookScript.textContent = `(function() {
            const MEDIA_REGEX = /\\.(m3u8|mpd|mp4|webm|m4v|mov|m4a|aac|flv)(\\?|#|$)/i;
            const KNOWN_CDN = /(googlevideo\\.com|v\\.redd\\.it|fbcdn\\.net|twimg\\.com|tiktokcdn\\.com|vimeocdn\\.com|byteoversea\\.com|hotstar\\.com|akamaihd\\.net|cloudfront\\.net)/i;

            function checkUrl(url) {
                if (!url || typeof url !== 'string' || url.startsWith('blob:') || url.startsWith('data:')) return;
                // Ignore chunk segments
                if (url.includes('.ts?') || url.endsWith('.ts') || url.includes('.m4s?') || url.endsWith('.m4s')) return;

                if (MEDIA_REGEX.test(url) || KNOWN_CDN.test(url)) {
                    window.postMessage({
                        type: "__gecko_sniffed_media__",
                        url: url,
                        pageTitle: document.title || "Web Video",
                        pageUrl: window.location.href
                    }, "*");
                }
            }

            // Hook window.fetch
            if (window.fetch) {
                const origFetch = window.fetch;
                window.fetch = function() {
                    try {
                        const arg = arguments[0];
                        const url = (typeof arg === 'string') ? arg : (arg && arg.url ? arg.url : '');
                        checkUrl(url);
                    } catch(e) {}
                    return origFetch.apply(this, arguments);
                };
            }

            // Hook XMLHttpRequest
            if (window.XMLHttpRequest) {
                const origOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    try {
                        checkUrl(url);
                    } catch(e) {}
                    return origOpen.apply(this, arguments);
                };
            }

            // Hook HTMLMediaElement src
            try {
                const origSrcDesc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
                if (origSrcDesc && origSrcDesc.set) {
                    Object.defineProperty(HTMLMediaElement.prototype, 'src', {
                        set: function(val) {
                            try { checkUrl(val); } catch(e) {}
                            return origSrcDesc.set.call(this, val);
                        },
                        get: origSrcDesc.get,
                        configurable: true
                    });
                }
            } catch(e) {}

            // YouTube in-page player response
            function tryExtractPagePlayer() {
                try {
                    const ytResp = window.ytInitialPlayerResponse || 
                                   (window.ytplayer && window.ytplayer.config && window.ytplayer.config.args && JSON.parse(window.ytplayer.config.args.raw_player_response || "{}"));
                    if (ytResp && ytResp.videoDetails) {
                        const vDetails = ytResp.videoDetails;
                        const sData = ytResp.streamingData || {};
                        const formats = (sData.formats || []).concat(sData.adaptiveFormats || []);
                        const streamList = [];

                        formats.forEach(f => {
                            if (f && f.url) {
                                const isAudio = (f.mimeType || "").startsWith("audio/");
                                const isAv1 = (f.mimeType || "").includes("av01");
                                const isWebm = (f.mimeType || "").includes("webm");
                                const label = f.qualityLabel || (isAudio ? "Audio Stream (" + (f.audioQuality || "128k") + ")" : "Stream " + (f.height ? f.height + "p" : ""));
                                streamList.push({
                                    label: label,
                                    resolution: (f.width && f.height) ? (f.width + "x" + f.height) : (f.height ? f.height + "p" : ""),
                                    codec: isAv1 ? "AV1" : (isWebm ? "VP9" : (isAudio ? "AAC" : "H.264")),
                                    format: isWebm ? "WEBM" : (isAudio ? "M4A" : "MP4"),
                                    url: f.url,
                                    estimatedSizeBytes: parseInt(f.contentLength || "0", 10),
                                    isAudioOnly: isAudio,
                                    isAv1: isAv1,
                                    mimeType: f.mimeType
                                });
                            }
                        });

                        const thumb = (vDetails.thumbnail && vDetails.thumbnail.thumbnails && vDetails.thumbnail.thumbnails.slice(-1)[0]?.url) || null;

                        window.postMessage({
                            type: "__gecko_inpage_media__",
                            media: {
                                pageUrl: window.location.href,
                                title: vDetails.title || document.title,
                                thumbnail: thumb,
                                durationSeconds: parseInt(vDetails.lengthSeconds || "0", 10),
                                sourceDomain: "youtube.com",
                                qualities: streamList
                            }
                        }, "*");
                    }
                } catch(e) {}
            }

            tryExtractPagePlayer();
            window.addEventListener("yt-navigate-finish", () => setTimeout(tryExtractPagePlayer, 600));
            window.addEventListener("load", () => setTimeout(tryExtractPagePlayer, 1000));
        })();`;
        (document.head || document.documentElement).appendChild(hookScript);
        hookScript.remove();

        window.addEventListener("message", (event) => {
            if (event.data) {
                if (event.data.type === "__gecko_inpage_media__" && event.data.media) {
                    const media = event.data.media;
                    if (media.qualities && media.qualities.length > 0) {
                        reportMedia(media);
                    }
                } else if (event.data.type === "__gecko_sniffed_media__" && event.data.url) {
                    const mediaUrl = event.data.url;
                    if (!mediaUrl.startsWith("blob:") && !mediaUrl.startsWith("data:") && (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://"))) {
                        const isM3u8 = mediaUrl.includes(".m3u8");
                        const isMpd = mediaUrl.includes(".mpd");
                        const isWebm = mediaUrl.includes(".webm");
                        const format = isM3u8 ? "M3U8" : (isMpd ? "DASH" : (isWebm ? "WEBM" : "MP4"));
                        
                        reportMedia({
                            pageUrl: window.location.href,
                            title: event.data.pageTitle || document.title || "Web Video",
                            thumbnail: document.querySelector("meta[property='og:image']")?.content || null,
                            durationSeconds: 0,
                            sourceDomain: window.location.hostname,
                            qualities: [
                                {
                                    label: isM3u8 ? "HLS Master Stream" : (isMpd ? "DASH Stream" : ("Direct " + format + " Stream")),
                                    resolution: "Auto",
                                    codec: (format === "WEBM") ? "VP9" : "H.264",
                                    format: format,
                                    url: mediaUrl,
                                    isAudioOnly: false,
                                    isAv1: false
                                }
                            ]
                        });
                    }
                }
            }
        });
    } catch(e) {}

    // 2. YouTube DOM detector fallback (only for direct non-blob URLs)
    function checkYouTube() {
        const host = window.location.hostname.toLowerCase();
        if (!host.includes("youtube.com") && !host.includes("youtu.be")) return false;

        const video = document.querySelector("video");
        if (!video) return false;

        const src = video.currentSrc || video.src || "";
        if (src && (src.startsWith("http://") || src.startsWith("https://")) && !src.startsWith("blob:") && !src.startsWith("data:")) {
            const urlParams = new URLSearchParams(window.location.search);
            const videoId = urlParams.get("v") || (window.location.pathname.split("/watch/")[1]) || (window.location.pathname.split("/shorts/")[1]) || "";
            let title = document.title ? document.title.replace(" - YouTube", "").trim() : "YouTube Video";
            const duration = Math.round(video.duration || 0);
            const thumbnail = videoId ? `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg` : null;

            reportMedia({
                pageUrl: window.location.href,
                title: title,
                thumbnail: thumbnail,
                durationSeconds: duration,
                sourceDomain: "youtube.com",
                qualities: [
                    {
                        label: "Direct Stream",
                        resolution: (video.videoWidth && video.videoHeight) ? `${video.videoWidth}x${video.videoHeight}` : "720p",
                        codec: "H.264",
                        format: "MP4",
                        url: src,
                        isAudioOnly: false,
                        isAv1: false
                    }
                ]
            });
            return true;
        }
        return false;
    }

    // 3. Reddit specialized extractor
    function checkReddit() {
        const host = window.location.hostname.toLowerCase();
        if (!host.includes("reddit.com")) return false;

        const videoElem = document.querySelector("video[src*='v.redd.it'], shreddit-player, [data-mp4-url], video");
        if (videoElem) {
            let src = videoElem.src || videoElem.getAttribute("src") || videoElem.getAttribute("data-mp4-url") || "";
            if (!src && videoElem.querySelector("source")) {
                src = videoElem.querySelector("source").src;
            }
            const title = document.title.replace(" : r/", " - r/").replace(" - Reddit", "") || "Reddit Video";
            const poster = videoElem.getAttribute("poster") || videoElem.getAttribute("preview") || null;

            if (src && src.includes("v.redd.it")) {
                const baseUrl = src.substring(0, src.lastIndexOf("/") + 1);
                const qualities = [
                    { label: "1080p Full HD", resolution: "1920x1080", codec: "H.264", format: "MP4", url: baseUrl + "DASH_1080.mp4", isAudioOnly: false, isAv1: false },
                    { label: "720p HD", resolution: "1280x720", codec: "H.264", format: "MP4", url: baseUrl + "DASH_720.mp4", isAudioOnly: false, isAv1: false },
                    { label: "480p SD", resolution: "854x480", codec: "H.264", format: "MP4", url: baseUrl + "DASH_480.mp4", isAudioOnly: false, isAv1: false },
                    { label: "HLS Master Stream", resolution: "Auto", codec: "H.264", format: "M3U8", url: baseUrl + "HLSPlaylist.m3u8", isAudioOnly: false, isAv1: false },
                    { label: "Audio Only", resolution: "", codec: "AAC", format: "M4A", url: baseUrl + "DASH_audio.mp4", isAudioOnly: true, isAv1: false }
                ];

                reportMedia({
                    pageUrl: window.location.href,
                    title: title,
                    thumbnail: poster,
                    durationSeconds: Math.round(videoElem.duration || 0),
                    sourceDomain: "reddit.com",
                    qualities: qualities
                });
                return true;
            }
        }
        return false;
    }

    // 4. Universal HTML5, OpenGraph & JSON-LD Video Scanner
    function scanHtml5Media() {
        // 4a. Check OpenGraph / Twitter video meta tags
        const metaVideo = document.querySelector("meta[property='og:video'], meta[property='og:video:url'], meta[property='og:video:secure_url'], meta[name='twitter:player:stream']")?.content;
        if (metaVideo && (metaVideo.startsWith("http://") || metaVideo.startsWith("https://")) && !metaVideo.includes("youtube.com/embed")) {
            const isM3u8 = metaVideo.includes(".m3u8");
            const isMp4 = metaVideo.includes(".mp4");
            if (isM3u8 || isMp4 || metaVideo.includes(".webm")) {
                let title = document.title ? document.title.trim() : "Web Video";
                const thumbnail = document.querySelector("meta[property='og:image']")?.content || null;
                reportMedia({
                    pageUrl: window.location.href,
                    title: title,
                    thumbnail: thumbnail,
                    durationSeconds: 0,
                    sourceDomain: window.location.hostname,
                    qualities: [
                        {
                            label: isM3u8 ? "HLS Master Stream" : "High Quality MP4",
                            resolution: "Auto",
                            codec: "H.264",
                            format: isM3u8 ? "M3U8" : "MP4",
                            url: metaVideo,
                            isAudioOnly: false,
                            isAv1: false
                        }
                    ]
                });
            }
        }

        // 4b. Check JSON-LD schema.org/VideoObject
        try {
            document.querySelectorAll("script[type='application/ld+json']").forEach(script => {
                try {
                    const data = JSON.parse(script.textContent || "{}");
                    const items = Array.isArray(data) ? data : (data["@graph"] || [data]);
                    for (const item of items) {
                        if (item && (item["@type"] === "VideoObject" || item.type === "VideoObject")) {
                            const contentUrl = item.contentUrl || item.embedUrl;
                            if (contentUrl && typeof contentUrl === "string" && (contentUrl.startsWith("http://") || contentUrl.startsWith("https://"))) {
                                const isM3u8 = contentUrl.includes(".m3u8");
                                reportMedia({
                                    pageUrl: window.location.href,
                                    title: item.name || document.title || "Web Video",
                                    thumbnail: (typeof item.thumbnailUrl === "string" ? item.thumbnailUrl : (item.thumbnailUrl && item.thumbnailUrl[0])) || null,
                                    durationSeconds: 0,
                                    sourceDomain: window.location.hostname,
                                    qualities: [
                                        {
                                            label: isM3u8 ? "HLS Master Stream" : "Direct Stream",
                                            resolution: "Auto",
                                            codec: "H.264",
                                            format: isM3u8 ? "M3U8" : "MP4",
                                            url: contentUrl,
                                            isAudioOnly: false,
                                            isAv1: false
                                        }
                                    ]
                                });
                            }
                        }
                    }
                } catch(e) {}
            });
        } catch(e) {}

        // 4c. Check HTML5 <video> elements
        const videos = document.querySelectorAll("video");
        if (videos.length === 0) return;

        videos.forEach(v => {
            let src = v.currentSrc || v.src || "";
            if (!src) {
                const source = v.querySelector("source");
                if (source) src = source.src;
            }

            // Only report if there is a direct downloadable HTTP/HTTPS video URL
            if (!src || src.startsWith("blob:") || src.startsWith("data:") || (!src.startsWith("http://") && !src.startsWith("https://"))) {
                return;
            }

            let title = document.title ? document.title.trim() : "";
            if (!title || title === "Start Page" || title === "Video") {
                title = document.querySelector("meta[property='og:title']")?.content ||
                        document.querySelector("meta[name='twitter:title']")?.content ||
                        document.querySelector("h1")?.textContent?.trim() || "Web Video";
            }
            const thumbnail = v.poster ||
                              document.querySelector("meta[property='og:image']")?.content ||
                              document.querySelector("meta[name='twitter:image']")?.content || null;
            const duration = Math.round(v.duration || 0);
            const width = v.videoWidth || 0;
            const height = v.videoHeight || 0;
            const resolution = (width && height) ? `${width}x${height}` : (height ? `${height}p` : "");

            const qualityLabel = height >= 1080 ? "1080p Full HD" : (height >= 720 ? "720p HD" : (height >= 480 ? "480p SD" : (height >= 360 ? "360p" : (resolution || "Web Video"))));
            const isM3u8 = src.includes(".m3u8");
            const format = isM3u8 ? "M3U8" : (src.includes(".webm") ? "WEBM" : "MP4");

            reportMedia({
                pageUrl: window.location.href,
                title: title,
                thumbnail: thumbnail,
                durationSeconds: duration,
                sourceDomain: window.location.hostname,
                qualities: [
                    {
                        label: qualityLabel,
                        resolution: resolution,
                        codec: format === "WEBM" ? "VP9" : "H.264",
                        format: format,
                        url: src,
                        isAudioOnly: false,
                        isAv1: false
                    }
                ]
            });
        });
    }

    function safeScan() {
        const now = Date.now();
        if (now - lastScanTime < 800) return; // Responsive throttle
        lastScanTime = now;

        const host = window.location.hostname.toLowerCase();
        if (host.includes("youtube.com") || host.includes("youtu.be")) {
            checkYouTube();
        } else if (host.includes("reddit.com")) {
            if (!checkReddit()) scanHtml5Media();
        } else {
            scanHtml5Media();
        }
    }

    // Attach listeners on media & document events (no timeupdate to avoid CPU lag)
    ["play", "playing", "loadedmetadata", "loadeddata", "canplay"].forEach(evt => {
        document.addEventListener(evt, (e) => {
            if (e.target && (e.target.tagName === "VIDEO" || e.target.tagName === "AUDIO")) {
                safeScan();
            }
        }, true);
    });

    // YouTube SPA navigation hook
    window.addEventListener("yt-navigate-finish", () => {
        setTimeout(safeScan, 500);
        setTimeout(safeScan, 1500);
    });

    // Initial check after page is settled
    setTimeout(safeScan, 1000);
    setTimeout(safeScan, 2500);
}


// Init
injectCosmeticAdBlockStyle();
injectScriptletSurrogates();
watchForGoogleAiOverview();
initSpaScrollRestoration();
initBackgroundMediaPlayback();
initClientDownloadInterceptor();
initWebShareBridge();
initUniversalMediaSniffer();

window.addEventListener("DOMContentLoaded", () => {
    injectCosmeticAdBlockStyle();
    removeAiNodes(document);
});

// Listen for commands from native app
browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.action === "extract_article") {
        sendResponse(extractArticleContent(document));
    }
    return true;
});



