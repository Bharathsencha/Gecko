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
    if (window !== window.top) return;

    let lastScanTime = 0;

    function reportMedia(videoData) {
        if (!videoData || !videoData.qualities || videoData.qualities.length === 0) return;
        try {
            if (!videoData.headers) {
                const host = window.location.hostname;
                const isYouTube = /(^|\.)youtube\.com$|(^|\.)youtu\.be$/.test(host);
                if (!isYouTube) {
                    videoData.headers = {
                        Referer: window.location.href,
                        Origin: window.location.origin
                    };
                }
            }
            if (browser.runtime && browser.runtime.sendMessage) {
                browser.runtime.sendMessage({
                    action: "video_detected",
                    data: videoData
                }).catch(() => {});
            }
        } catch (e) {}
    }

    // 1. YouTube Direct Player & StreamingData Extractor (Xray Vision - Immune to CSP)
    function extractYouTubePlayer() {
        const host = window.location.hostname.toLowerCase();
        if (!host.includes("youtube.com") && !host.includes("youtu.be")) return false;

        try {
            const pageWin = window.wrappedJSObject || window;
            let ytResp = pageWin.ytInitialPlayerResponse;

            if (!ytResp && pageWin.ytplayer && pageWin.ytplayer.config && pageWin.ytplayer.config.args) {
                try {
                    ytResp = JSON.parse(pageWin.ytplayer.config.args.raw_player_response || pageWin.ytplayer.config.args.player_response || "{}");
                } catch(e) {}
            }

            if (!ytResp) {
                for (const script of document.scripts) {
                    const text = script.textContent || "";
                    if (text.includes("ytInitialPlayerResponse")) {
                        const match = text.match(/ytInitialPlayerResponse\s*=\s*(\{.+?\});/);
                        if (match) {
                            try { ytResp = JSON.parse(match[1]); } catch(e) {}
                            if (ytResp && ytResp.videoDetails) break;
                        }
                    }
                }
            }

            if (!ytResp) {
                const player = (pageWin.document && pageWin.document.getElementById("movie_player")) || document.getElementById("movie_player");
                if (player && typeof player.getPlayerResponse === "function") {
                    try { ytResp = player.getPlayerResponse(); } catch(e) {}
                }
            }

            if (ytResp && ytResp.videoDetails) {
                const vDetails = ytResp.videoDetails;
                const sData = ytResp.streamingData || {};
                const formats = sData.formats || [];
                const adaptiveFormats = sData.adaptiveFormats || [];
                const allFormats = formats.concat(adaptiveFormats);
                const streamList = [];

                // Find best audio stream for muxing
                let bestAudioUrl = null;
                for (const f of adaptiveFormats) {
                    const mime = f.mimeType || "";
                    if (mime.startsWith("audio/")) {
                        let streamUrl = f.url || "";
                        if (streamUrl && streamUrl.startsWith("http")) {
                            if (!bestAudioUrl || mime.includes("mp4") || mime.includes("m4a")) {
                                bestAudioUrl = streamUrl;
                                if (mime.includes("mp4") || mime.includes("m4a")) break;
                            }
                        }
                    }
                }

                allFormats.forEach(f => {
                    if (!f) return;
                    let streamUrl = f.url || "";
                    if (streamUrl && streamUrl.startsWith("http")) {
                        const mime = f.mimeType || "";
                        const isAudio = mime.startsWith("audio/");
                        const isAv1 = mime.includes("av01");
                        const isWebm = mime.includes("webm");
                        const height = f.height || 0;
                        const isProgressive = formats.includes(f);

                        let qualityLabel = f.qualityLabel;
                        if (!qualityLabel) {
                            if (height >= 2160) qualityLabel = "4K Ultra HD";
                            else if (height >= 1440) qualityLabel = "1440p QHD";
                            else if (height >= 1080) qualityLabel = "1080p Full HD";
                            else if (height >= 720) qualityLabel = "720p HD";
                            else if (height >= 480) qualityLabel = "480p SD";
                            else if (height >= 360) qualityLabel = "360p SD";
                            else if (height > 0) qualityLabel = height + "p";
                            else if (isAudio) qualityLabel = "Audio Track (" + (f.audioQuality || "128k") + ")";
                            else qualityLabel = "Video Stream";
                        }

                        streamList.push({
                            label: qualityLabel,
                            resolution: (f.width && f.height) ? (f.width + "x" + f.height) : (height ? height + "p" : ""),
                            codec: isAv1 ? "AV1" : (isWebm ? "VP9" : (isAudio ? "AAC" : "H.264")),
                            format: isWebm ? "WEBM" : (isAudio ? "M4A" : "MP4"),
                            url: streamUrl,
                            estimatedSizeBytes: parseInt(f.contentLength || "0", 10),
                            isAudioOnly: isAudio,
                            isAv1: isAv1,
                            isProgressive: isProgressive,
                            audioUrl: isProgressive || isAudio ? null : bestAudioUrl,
                            mimeType: mime || (isAudio ? "audio/mp4" : "video/mp4")
                        });
                    }
                });

                const thumb = (vDetails.thumbnail && vDetails.thumbnail.thumbnails && vDetails.thumbnail.thumbnails.slice(-1)[0]?.url) ||
                              (vDetails.videoId ? `https://i.ytimg.com/vi/${vDetails.videoId}/hqdefault.jpg` : null);

                reportMedia({
                    pageUrl: window.location.href,
                    title: vDetails.title || document.title.replace(" - YouTube", "").trim(),
                    thumbnail: thumb,
                    durationSeconds: parseInt(vDetails.lengthSeconds || "0", 10),
                    sourceDomain: "youtube.com",
                    qualities: streamList
                });
                return true;
            }
        } catch(e) {}
        return false;
    }

    // 2. Reddit specialized extractor
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
                    { label: "1080p Full HD", resolution: "1920x1080", codec: "H.264", format: "MP4", url: baseUrl + "DASH_1080.mp4", isAudioOnly: false, isAv1: false, isProgressive: false, audioUrl: baseUrl + "DASH_audio.mp4" },
                    { label: "720p HD", resolution: "1280x720", codec: "H.264", format: "MP4", url: baseUrl + "DASH_720.mp4", isAudioOnly: false, isAv1: false, isProgressive: false, audioUrl: baseUrl + "DASH_audio.mp4" },
                    { label: "480p SD", resolution: "854x480", codec: "H.264", format: "MP4", url: baseUrl + "DASH_480.mp4", isAudioOnly: false, isAv1: false, isProgressive: false, audioUrl: baseUrl + "DASH_audio.mp4" },
                    { label: "HLS Master Stream", resolution: "Auto", codec: "H.264", format: "M3U8", url: baseUrl + "HLSPlaylist.m3u8", isAudioOnly: false, isAv1: false, isProgressive: false, mimeType: "application/x-mpegURL" },
                    { label: "Audio Only", resolution: "", codec: "AAC", format: "M4A", url: baseUrl + "DASH_audio.mp4", isAudioOnly: true, isAv1: false, isProgressive: true, mimeType: "audio/mp4" }
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

    // 3. Universal HTML5, Shadow DOM, OpenGraph & JSON-LD Video Scanner
    function collectVideoElements(root, result = []) {
        if (!root) return result;
        try {
            const vids = root.querySelectorAll("video, audio, [data-mp4-url], [data-video-url], shreddit-player, video-js");
            vids.forEach(v => result.push(v));

            const allElements = root.querySelectorAll("*");
            allElements.forEach(el => {
                if (el.shadowRoot) {
                    collectVideoElements(el.shadowRoot, result);
                }
            });
        } catch (e) {}
        return result;
    }

    function scanHtml5Media() {
        // 3a. Check OpenGraph / Twitter video meta tags
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
                            isAv1: false,
                            isProgressive: !isM3u8,
                            mimeType: isM3u8 ? "application/x-mpegURL" : "video/mp4"
                        }
                    ]
                });
            }
        }

        // 3b. Check HTML5 <video> & <audio> elements (including inside Shadow DOM)
        const mediaElements = collectVideoElements(document);
        if (mediaElements.length === 0) return;

        mediaElements.forEach(v => {
            let src = v.currentSrc || v.src || v.getAttribute("src") || v.getAttribute("data-src") || v.getAttribute("data-video-url") || v.getAttribute("data-mp4-url") || "";
            if (!src) {
                const source = v.querySelector("source");
                if (source) src = source.src || source.getAttribute("src") || "";
            }

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
            const isAudio = v.tagName === "AUDIO" || src.includes(".mp3") || src.includes(".m4a") || src.includes(".aac");
            const format = isM3u8 ? "M3U8" : (src.includes(".webm") ? "WEBM" : (isAudio ? "M4A" : "MP4"));

            reportMedia({
                pageUrl: window.location.href,
                title: title,
                thumbnail: thumbnail,
                durationSeconds: duration,
                sourceDomain: window.location.hostname,
                qualities: [
                    {
                        label: isAudio ? "Audio Stream" : qualityLabel,
                        resolution: resolution,
                        codec: format === "WEBM" ? "VP9" : (isAudio ? "AAC" : "H.264"),
                        format: format,
                        url: src,
                        isAudioOnly: isAudio,
                        isAv1: false,
                        isProgressive: !isM3u8,
                        mimeType: isM3u8 ? "application/x-mpegURL" : (format === "WEBM" ? "video/webm" : (isAudio ? "audio/mp4" : "video/mp4"))
                    }
                ]
            });
        });
    }

    function safeScan() {
        const now = Date.now();
        if (now - lastScanTime < 400) return;
        lastScanTime = now;

        const host = window.location.hostname.toLowerCase();
        if (host.includes("youtube.com") || host.includes("youtu.be")) {
            extractYouTubePlayer();
        } else if (host.includes("reddit.com")) {
            if (!checkReddit()) scanHtml5Media();
        } else {
            scanHtml5Media();
        }
    }

    // Attach listeners on media playback events (Triggered when video actually plays)
    ["play", "playing", "loadedmetadata", "loadeddata", "canplay", "timeupdate"].forEach(evt => {
        document.addEventListener(evt, (e) => {
            if (e.target && (e.target.tagName === "VIDEO" || e.target.tagName === "AUDIO")) {
                safeScan();
            }
        }, true);
    });

    // SPA Navigation & URL listeners
    window.addEventListener("popstate", () => setTimeout(safeScan, 200));
    window.addEventListener("yt-navigate-finish", () => {
        setTimeout(safeScan, 400);
        setTimeout(safeScan, 1200);
    });
    window.addEventListener("yt-page-data-updated", () => setTimeout(safeScan, 500));

    // Initial check
    setTimeout(safeScan, 500);
    setTimeout(safeScan, 1500);
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



