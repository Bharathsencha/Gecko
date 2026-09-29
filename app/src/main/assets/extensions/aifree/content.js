/**
 * AI Free Content Script
 * Handles: Fast Cosmetic Ad-Hiding, Scriptlet Surrogates, Google AI Overview removal, Reader mode, SPA Scroll, Media
 */

// ── 0. High-Speed Cosmetic Ad Hiding & Scriptlet Surrogates ─────────────────
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

// ── 1. Google AI Overview Blocker ──────────────────────────────────────────
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

// ── 2. SPA Scroll Restoration Engine (YouTube, Twitter, Reddit, etc.) ──────
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

// ── 3. Background Audio & Video Playback Engine ─────────────────────────────
function initBackgroundMediaPlayback() {
    try {
        const script = document.createElement('script');
        script.textContent = `(function() {
            try {
                Object.defineProperty(document, 'hidden', {
                    get: () => false,
                    configurable: true
                });
                Object.defineProperty(document, 'visibilityState', {
                    get: () => 'visible',
                    configurable: true
                });
                Object.defineProperty(document, 'webkitVisibilityState', {
                    get: () => 'visible',
                    configurable: true
                });
            } catch(e) {}

            // Suppress visibility/pagehide pause events from reaching video elements
            const blockedEventTypes = new Set(['visibilitychange', 'webkitvisibilitychange', 'pagehide']);
            
            const origAddEventListener = EventTarget.prototype.addEventListener;
            EventTarget.prototype.addEventListener = function(type, listener, options) {
                if (blockedEventTypes.has(type)) {
                    const wrapped = function(e) {
                        // Do not fire pause actions when browser is backgrounded
                        return;
                    };
                    return origAddEventListener.call(this, type, wrapped, options);
                }
                return origAddEventListener.call(this, type, listener, options);
            };

            // Intercept media pause calls triggered by backgrounding
            const origPause = HTMLMediaElement.prototype.pause;
            HTMLMediaElement.prototype.pause = function() {
                const stack = new Error().stack || '';
                if (stack.includes('visibilitychange') || 
                    stack.includes('onVisibilityChange') || 
                    stack.includes('pagehide') || 
                    stack.includes('onHidden') || 
                    stack.includes('background') ||
                    stack.includes('onBlur')) {
                    return;
                }
                return origPause.apply(this, arguments);
            };
        })();`;
        (document.head || document.documentElement).appendChild(script);
        script.remove();
    } catch (e) {}
}

// ── 4. Reader Mode Article Extraction ──────────────────────────────────────
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

// ── 5. Client-Side Blob / Data URI Download Interceptor ──────────────────────
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

// ── 6. Web Share API Native Chooser Bridge ──────────────────────────────────
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

// ── 7. Init ────────────────────────────────────────────────────────────────
injectCosmeticAdBlockStyle();
injectScriptletSurrogates();
watchForGoogleAiOverview();
initSpaScrollRestoration();
initBackgroundMediaPlayback();
initClientDownloadInterceptor();
initWebShareBridge();

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


