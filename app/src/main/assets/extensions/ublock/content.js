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
 * Embedded uBlock Origin & Web Presentation Engine
 * Handles cosmetic ad blocking, font smoothing, dark web mode, and Safari reader extraction.
 */

const cosmeticSelectors = [
    // General ad containers & slots
    ".adsbox", ".ad-banner", ".ad_banner", ".banner_ad", ".banner-ad",
    ".banner_ads", ".banner-ads", ".ad-container", ".ad-wrapper", ".ad-placement",
    ".adsbygoogle", ".ad-slot", ".ad_slot", ".ad-unit", ".ad_unit",
    ".bn-wrapper", ".ad-zone", ".ad-holder", ".ad-space", ".ad-module", ".ad-break",
    ".carbon-ads", "#adblock-tester", "[class*='adblock-test']", "[id*='adblock-test']",
    ".pub_300x250", ".pub_728x90", ".pub_160x600", "[class*='pub_']", "[id*='pub_']",
    ".rvm-banner", "[class*='rvm-']", "[id*='rvm-']", ".test-banner", ".ad-placeholder",
    
    // AdBlock Tester, Yandex & Custom test containers
    "[data-ads]", "div[data-ads]", "[id*='yandex_rtb']", "div[id*='yandex_rtb']",
    "ins.adsbygoogle", "ins[class*='adsbygoogle']",
    
    // Flash banners, embed & object ad elements
    "object[type*='flash']", "embed[type*='flash']",
    "object[data*='banner']", "embed[src*='banner']",
    "object[data*='.swf']", "embed[src*='.swf']",
    "object[data*='advertising']", "embed[src*='advertising']",
    "object[data*='ads']", "embed[src*='ads']",
    ".includeWrapper object", ".includeWrapper embed",
    
    // IDs
    "#ad-container", "#ad-slot", "#div-gpt-ad", "#google_ads_iframe",
    "#advertisement", "#header-ad", "#sidebar-ads", "#footer-ad",
    "#banner-ads", "#ad-banner-top", "#ad-box", "#banner_ad", "#ad_banner",
    
    // Attribute wildcards
    "div[id*='google_ad']", "div[class*='ad-server']", "div[id*='ad-banner']",
    "div[class*='ad-placement']", "div[class*='ad-wrapper']", "div[id*='gpt-ad-']",
    "div[id*='taboola-']", "div[id*='outbrain-']", "div[class*='sponsored-post']",
    "div[data-ad-client]", "div[data-ad-slot]", "div[data-ad-format]",
    "div[class*='banner']", "div[id*='banner']", "div[class*='ad-']", "div[id*='ad-']",
    "div[class*='pub_']", "div[id*='pub_']",
    
    // Banner & Ad images
    "img[src*='banner']", "img[src*='300x250']", "img[src*='728x90']",
    "img[src*='160x600']", "img[src*='468x60']", "img[src*='320x50']",
    "img[src*='300x600']", "img[src*='970x250']", "img[src*='/ads/']",
    "img[src*='/banners/']", "img[src*='ad_']", "img[src*='ads_']",
    "img[src*='pr_advertising']", ".includeWrapper img",
    
    // Ad iframes
    "iframe[src*='doubleclick.net']", "iframe[src*='googleads']", "iframe[src*='adnxs']",
    "iframe[src*='adcolony']", "iframe[src*='pubmatic']", "iframe[src*='taboola']",
    "iframe[src*='outbrain']", "iframe[src*='criteo']", "iframe[src*='smartadserver']",
    "iframe[id*='google_ads_iframe']", "iframe[title*='Advertisement']",
    
    // Video & Overlay Ads
    ".video-ad-overlay", ".ytp-ad-overlay-container", ".ytp-ad-message-container",
    ".ytp-ad-player-overlay", ".vjs-ad-overlay", ".ima-ad-container",
    
    // Popups & Banners
    ".popup-ad", ".overlay-ad", ".interstitial-ad", ".ad-interstitial",
    
    // Google AI Overviews, SGE & Generative AI Search Clutter
    // Classic SGE selectors
    "#Odp5De", "div[jsname='yDeuDf']", ".hdzaWe", "div[jscontroller*='GenerativeSearch']",
    "div[data-attrid*='generative']", ".g-blk", "[data-sge-block='true']",
    ".MjjYud:has(#Odp5De)", ".MjjYud:has(.hdzaWe)", "div[class*='copilot']", ".b_ans_ai",
    "div[data-asoch-targets*='generative']", "#genai_carousel", "div[jsname='X9lQic']",
    "div[aria-label*='AI Overview']", "div[aria-label*='AI overview']",
    "div:has(> div[aria-label*='AI Overview'])", "div:has(> div[aria-label*='AI overview'])",
    "div[data-attrid='wa:/description']", ".ai-overview-box", "[data-attrid*='sge']",
    "div[jsname='b4e54d']", "div[jsname='bN97ce']", "div[jsname='LgbsSe']",
    // 2025/2026 Google AI Overview - new containers
    "div[jsname='Bz112c']", "div[jsname='oIq1Zb']", "div[jsname='wx3skf']",
    "div[jsname='wMPnde']", "div[jsname='BiCFae']", "div[jsname='r3p0Jd']",
    "#gds-aic", "#kp-wp-tab-overview", ".YzCOte", ".XqgnIe", ".M8OgIe",
    "div[data-aioc]", "div[data-aipc]", "div[jscontroller='MxYRTe']",
    "div[jscontroller='AUvrTe']", "div[jscontroller='SHcN5']",
    ".k8XOCe", ".wDYxhc:has(.XqgnIe)", "div[jscontroller*='AI']",
    // AI overview heading text container
    "div[data-oshdr]", "div[jsname='FzSmSb']",
    // Wrapper that holds the entire AI overview block
    ".UA0FGd", ".BmP5tf", ".kkjOtf",
    "div[class*='ai-overview']", "[id*='ai-overview']"
];

// Honeypot Bait Containers: Off-screen fallback positioning
const honeypotBaitSelectors = [];

// Anti-Adblock Modal Overlays & Paywall Curtains
const antiAdblockModalSelectors = [
    ".fc-ab-root", ".fc-dialog-container", "#sp_message_container", ".sp_veil",
    "[class*='adblock-modal']", "[id*='adblock-overlay']", "[class*='adblock-notice']",
    ".adblocker-overlay", "[id*='anti-adblock']", "[class*='anti-adblock']",
    "[id*='adblock-backdrop']", ".adblock-blocker", "#admiral-root", "[id^='admiral']",
    ".admiral-active", ".cmp-box", "div[class*='AdBlockDialog']", ".adblock-message",
    "#adblock-curtain", ".cookie-consent", ".cookie-banner", "#cookie-notice",
    "#onetrust-consent-sdk", ".cc-banner", ".cmp-container", "#didomi-host",
    "#qc-cmp2-container", ".truste_overlay", ".truste_box_overlay", ".osano-cm-window"
];

// 1. Inject Early CSS Shield, Honeypot Geometry Defusal & Scroll Restoration
let cosmeticBlockingActive = true;

function injectStyleShield() {
    if (!cosmeticBlockingActive) return;
    let styleEl = document.getElementById("ublock-cosmetic-shield");
    if (!styleEl) {
        styleEl = document.createElement("style");
        styleEl.id = "ublock-cosmetic-shield";
        
        // Standard ads: hidden cleanly
        const cosmeticRules = cosmeticSelectors.map(sel => 
            `${sel} { display: none !important; visibility: hidden !important; height: 0 !important; max-height: 0 !important; width: 0 !important; opacity: 0 !important; pointer-events: none !important; margin: 0 !important; padding: 0 !important; }`
        ).join("\n");

        // Honeypot bait: rendered offscreen so height & width !== 0
        const baitRules = honeypotBaitSelectors.map(sel =>
            `${sel} { position: absolute !important; left: -9999px !important; top: -9999px !important; width: 300px !important; height: 250px !important; opacity: 0 !important; pointer-events: none !important; z-index: -9999 !important; }`
        ).join("\n");

        // Anti-adblock modals: eradicated
        const modalRules = antiAdblockModalSelectors.map(sel =>
            `${sel} { display: none !important; visibility: hidden !important; opacity: 0 !important; pointer-events: none !important; z-index: -9999 !important; }`
        ).join("\n");

        // Force scrollability and prevent scroll locks
        const typographyRules = `
            html, body {
                overflow: auto !important;
                position: static !important;
                filter: none !important;
                pointer-events: auto !important;
                -webkit-font-smoothing: antialiased !important;
                -moz-osx-font-smoothing: grayscale !important;
                text-rendering: optimizeLegibility !important;
            }
        `;

        styleEl.textContent = cosmeticRules + "\n" + baitRules + "\n" + modalRules + "\n" + typographyRules;

        if (document.head) {
            document.head.appendChild(styleEl);
        } else {
            const obs = new MutationObserver(() => {
                if (document.head) {
                    document.head.appendChild(styleEl);
                    obs.disconnect();
                }
            });
            obs.observe(document.documentElement, { childList: true });
        }
    }
}

function removeStyleShield() {
    const styleEl = document.getElementById("ublock-cosmetic-shield");
    if (styleEl) styleEl.remove();
}

// 2. High-Performance DOM Cleaner: Instantly remove ad elements & anti-adblock modals
function cleanAdNodes(root) {
    if (!cosmeticBlockingActive) return;
    if (!root || !root.querySelectorAll) return;
    try {
        const query = cosmeticSelectors.concat(antiAdblockModalSelectors).join(", ");
        const elements = root.querySelectorAll(query);
        let removedCount = 0;
        for (let i = 0; i < elements.length; i++) {
            elements[i].remove();
            removedCount++;
        }
        
        // Force unlock body scroll if locked by anti-adblock modals
        if (document.body && (document.body.style.overflow === "hidden" || document.documentElement.style.overflow === "hidden")) {
            document.body.style.setProperty("overflow", "auto", "important");
            document.documentElement.style.setProperty("overflow", "auto", "important");
        }

        if (removedCount > 0 && typeof browser !== "undefined" && browser.runtime && browser.runtime.sendMessage) {
            browser.runtime.sendMessage({ action: "cosmetic_ads_blocked", count: removedCount }).catch(() => {});
        }
    } catch (e) {}
}

// 3. Smart Deep Dark Web Mode via Official Dark Reader Engine
function applyDarkWebMode(enabled) {
    if (enabled) {
        // 1. Trigger native dark theme attributes & classes
        try {
            document.documentElement.setAttribute("data-theme", "dark");
            document.documentElement.setAttribute("data-color-mode", "dark");
            document.documentElement.setAttribute("data-dark-mode", "true");
            document.documentElement.classList.add("dark", "theme-dark", "dark-mode");
            if (document.body) document.body.classList.add("dark", "theme-dark", "dark-mode");
            document.documentElement.style.colorScheme = "dark";
        } catch (e) {}

        // 2. Use official DarkReader dynamic engine if available
        if (typeof DarkReader !== "undefined" && typeof DarkReader.enable === "function") {
            try {
                DarkReader.enable({
                    brightness: 100,
                    contrast: 95,
                    sepia: 0,
                    darkSchemeBackgroundColor: "#000000",
                    darkSchemeTextColor: "#e8e8e8"
                });
                return;
            } catch (err) {
                console.warn("[uBlock] DarkReader enable error, falling back to CSS", err);
            }
        }

        // 3. Fallback CSS if DarkReader isn't loaded
        let darkStyle = document.getElementById("adfree-dark-web-style");
        if (!darkStyle) {
            darkStyle = document.createElement("style");
            darkStyle.id = "adfree-dark-web-style";
            darkStyle.textContent = `
                :root {
                    color-scheme: dark !important;
                }
                html {
                    color-scheme: dark !important;
                    background-color: #000000 !important;
                    filter: invert(90%) hue-rotate(180deg) !important;
                }
                img, video, canvas, svg, picture, iframe, [style*="background-image"] {
                    filter: invert(100%) hue-rotate(180deg) !important;
                }
            `;
            (document.head || document.documentElement).appendChild(darkStyle);
        }
    } else {
        if (typeof DarkReader !== "undefined" && typeof DarkReader.disable === "function") {
            try {
                DarkReader.disable();
            } catch (err) {}
        }
        let darkStyle = document.getElementById("adfree-dark-web-style");
        if (darkStyle) darkStyle.remove();
        try {
            document.documentElement.style.colorScheme = "";
            document.documentElement.classList.remove("dark", "theme-dark", "dark-mode");
            if (document.body) document.body.classList.remove("dark", "theme-dark", "dark-mode");
        } catch (e) {}
    }
}

// 5. Text Zoom
function applyTextZoom(percent) {
    let zoomStyle = document.getElementById("adfree-zoom-style");
    if (!zoomStyle) {
        zoomStyle = document.createElement("style");
        zoomStyle.id = "adfree-zoom-style";
        (document.head || document.documentElement).appendChild(zoomStyle);
    }
    const scale = (percent / 100).toFixed(2);
    zoomStyle.textContent = `
        body {
            zoom: ${scale} !important;
        }
    `;
}

// 6. Automatic Login & Password Detection / Form Observer
function observeFormLogins() {
    function handleLoginSubmit(formOrDoc) {
        try {
            const passInput = formOrDoc.querySelector ? formOrDoc.querySelector('input[type="password"]') : null;
            if (!passInput || !passInput.value || passInput.value.length < 2) return;

            const passVal = passInput.value;
            let userVal = "";

            // Find matching username/email field in same form or preceding
            const userInputs = formOrDoc.querySelectorAll ? formOrDoc.querySelectorAll('input[type="text"], input[type="email"], input[name*="user"], input[name*="login"], input[name*="email"], input[id*="user"], input[id*="email"]') : [];
            for (let i = 0; i < userInputs.length; i++) {
                const inp = userInputs[i];
                if (inp !== passInput && inp.value && inp.value.trim().length > 0) {
                    userVal = inp.value.trim();
                    break;
                }
            }

            const host = window.location.hostname || window.location.host;
            if (host && passVal) {
                if (typeof browser !== "undefined" && browser.runtime && browser.runtime.sendNativeMessage) {
                    browser.runtime.sendNativeMessage("browser-app", {
                        action: "offer_save_password",
                        domain: host,
                        username: userVal,
                        password: passVal
                    }).catch(() => {});
                }
            }
        } catch (e) {}
    }

    // Intercept form submit events
    document.addEventListener("submit", (e) => {
        if (e.target) handleLoginSubmit(e.target);
    }, true);

    // Intercept button clicks for AJAX login forms (e.g. SPAs, Google, GitHub)
    document.addEventListener("click", (e) => {
        const btn = e.target.closest ? e.target.closest('button, input[type="submit"], [role="button"]') : null;
        if (btn) {
            const form = btn.closest ? btn.closest("form") : null;
            handleLoginSubmit(form || document);
        }
    }, true);
}

// 7. Stealth Global Scriptlet Stubs (Clean data objects, zero native prototype modification)
function injectAntiDetectionShield() {
    try {
        const host = (window.location.hostname || "").toLowerCase();
        if (host.includes("google.") || host.includes("gstatic.") || host.includes("recaptcha")) {
            return;
        }
        const script = document.createElement("script");
        script.textContent = `
            (function() {
                "use strict";

                // 1. Google AdSense Stub
                try {
                    window.adsbygoogle = window.adsbygoogle || [];
                    window.adsbygoogle.loaded = true;
                    if (!window.adsbygoogle.push || typeof window.adsbygoogle.push !== "function") {
                        window.adsbygoogle.push = function(arg) {
                            if (typeof arg === "function") {
                                try { arg(); } catch(e) {}
                            }
                            return 1;
                        };
                    }
                } catch(e) {}

                // 2. Google Publisher Tag (GPT) Stub
                try {
                    if (!window.googletag) {
                        const dummySlot = {
                            addService: function() { return this; },
                            setTargeting: function() { return this; },
                            setCollapseEmptyDiv: function() { return this; },
                            defineSizeMapping: function() { return this; },
                            setClickUrl: function() { return this; },
                            setCategoryExclusion: function() { return this; },
                            clearTargeting: function() { return this; },
                            setForceSafeFrame: function() { return this; }
                        };

                        const dummyPubads = {
                            addEventListener: function() {},
                            enableSingleRequest: function() {},
                            setTargeting: function() {},
                            collapseEmptyDivs: function() {},
                            clearTargeting: function() {},
                            refresh: function() {},
                            disableInitialLoad: function() {},
                            getSlots: function() { return []; },
                            setCentering: function() {},
                            setLocation: function() {},
                            enableVideoAds: function() {},
                            setCookieOptions: function() {},
                            setTagForChildDirectedTreatment: function() {},
                            setPublisherProvidedId: function() {}
                        };

                        window.googletag = {
                            cmd: [],
                            apiReady: true,
                            pubadsReady: true,
                            pubads: function() { return dummyPubads; },
                            enableServices: function() {},
                            display: function() {},
                            defineSlot: function() { return dummySlot; },
                            defineOutOfPageSlot: function() { return dummySlot; },
                            sizeMapping: function() {
                                return { addSize: function() { return this; }, build: function() { return []; } };
                            },
                            getVersion: function() { return "1.0.0"; },
                            setAdIframeTitle: function() {}
                        };
                    }
                } catch(e) {}

                // 3. Global Anti-Adblock Indicator Flag Stubs
                try {
                    window.canRunAds = true;
                    window.isAdBlockActive = false;
                    window.adblocker = false;
                    window.adBlocker = false;
                    window.google_ad_client = true;
                    window.google_ad_status = 1;
                    window.google_ad_channel = "1";
                    window.__adblocker = false;
                } catch(e) {}

                // 4. BlockAdBlock / FuckAdBlock / SnackAdBlock Library Defusers
                try {
                    function FakeAdBlockDefuser() {
                        this.check = function() { return false; };
                        this.clearEvent = function() {};
                        this.on = function(detected, fn) {
                            if (!detected && typeof fn === "function") {
                                try { fn(); } catch(e) {}
                            }
                            return this;
                        };
                        this.onDetected = function() { return this; };
                        this.onNotDetected = function(fn) {
                            if (typeof fn === "function") {
                                try { setTimeout(fn, 1); } catch(e) {}
                            }
                            return this;
                        };
                        this.setOption = function() {};
                        this.options = { checkOnLoad: false, resetOnEnd: false };
                    }
                    window.BlockAdBlock = FakeAdBlockDefuser;
                    window.blockAdBlock = new FakeAdBlockDefuser();
                    window.FuckAdBlock = FakeAdBlockDefuser;
                    window.fuckAdBlock = new FakeAdBlockDefuser();
                    window.SnackAdBlock = FakeAdBlockDefuser;
                    window.snackAdBlock = new FakeAdBlockDefuser();
                } catch(e) {}

                // 5. Harmless Analytics & Telemetry Mocks
                try {
                    window.ga = window.ga || function() {};
                    window.gtag = window.gtag || function() {};
                    window.dataLayer = window.dataLayer || [];
                    window.fbq = window.fbq || function() {};
                    window._fbq = window._fbq || window.fbq;
                    window.clarity = window.clarity || function() {};
                } catch(e) {}
            })();
        `;
        (document.head || document.documentElement).appendChild(script);
        script.remove();
    } catch(e) {}
}

// 8. Anti-Fingerprinting Shield — uses wrappedJSObject + exportFunction to bypass Firefox Xray
// Content scripts in Firefox run in an isolated world (Xray wrappers). Direct prototype patches
// are invisible to page JS. wrappedJSObject gives access to the REAL page window object.
(function applyFingerprintingProtection() {
    try {
        const host = (window.location.hostname || "").toLowerCase();
        if (host.includes("google.") || host.includes("gstatic.") || host.includes("recaptcha") ||
            host.includes("paypal.com") || host.includes("stripe.com") || host.includes("accounts.")) {
            return;
        }

        // wrappedJSObject = the real page window, bypassing Xray security wrappers
        const pageWindow = window.wrappedJSObject;
        if (!pageWindow) return; // safety: not available in all frames

        // Helper: export a function into page scope (Firefox-specific API)
        function exportToPage(fn, targetObj, name) {
            try {
                exportFunction(fn, targetObj, { defineAs: name });
            } catch(e) {}
        }

        // 1. Canvas — patch toDataURL and getImageData in page world
        try {
            const origToDataURL = pageWindow.HTMLCanvasElement.prototype.toDataURL;
            exportToPage(function() {
                try {
                    const ctx = this.getContext("2d");
                    if (ctx && this.width > 0 && this.height > 0 && this.width <= 500 && this.height <= 500) {
                        const imgData = ctx.getImageData(0, 0, Math.min(this.width, 16), Math.min(this.height, 16));
                        for (let i = 0; i < imgData.data.length; i += 4) {
                            imgData.data[i] = imgData.data[i] ^ 1;
                        }
                        ctx.putImageData(imgData, 0, 0);
                    }
                } catch(e) {}
                return origToDataURL.apply(this, arguments);
            }, pageWindow.HTMLCanvasElement.prototype, "toDataURL");
        } catch(e) {}

        try {
            const origGetImageData = pageWindow.CanvasRenderingContext2D.prototype.getImageData;
            exportToPage(function() {
                const imgData = origGetImageData.apply(this, arguments);
                try {
                    if (imgData && imgData.data && imgData.data.length > 0) {
                        const max = Math.min(imgData.data.length, 64);
                        for (let i = 0; i < max; i += 4) {
                            imgData.data[i] = imgData.data[i] ^ 1;
                        }
                    }
                } catch(e) {}
                return imgData;
            }, pageWindow.CanvasRenderingContext2D.prototype, "getImageData");
        } catch(e) {}

        // 2. AudioContext float jitter
        try {
            if (pageWindow.AudioBuffer && pageWindow.AudioBuffer.prototype.getChannelData) {
                const origGetChannelData = pageWindow.AudioBuffer.prototype.getChannelData;
                exportToPage(function(channel) {
                    const buffer = origGetChannelData.apply(this, arguments);
                    try {
                        const max = Math.min(buffer.length, 32);
                        for (let i = 0; i < max; i++) {
                            buffer[i] = buffer[i] + (0.00000001 * Math.sin(i));
                        }
                    } catch(e) {}
                    return buffer;
                }, pageWindow.AudioBuffer.prototype, "getChannelData");
            }
        } catch(e) {}

        // 3. WebGL vendor/renderer masking
        try {
            const patchWebGL = (ctx) => {
                const orig = ctx.prototype.getParameter;
                exportToPage(function(param) {
                    if (param === 0x9245) return "Qualcomm";
                    if (param === 0x9246) return "Adreno (TM) 750";
                    return orig.apply(this, arguments);
                }, ctx.prototype, "getParameter");
            };
            patchWebGL(pageWindow.WebGLRenderingContext);
            if (pageWindow.WebGL2RenderingContext) patchWebGL(pageWindow.WebGL2RenderingContext);
        } catch(e) {}

        // 4. Hardware concurrency & memory (via Object.defineProperty on pageWindow)
        try {
            Object.defineProperty(pageWindow.navigator.wrappedJSObject, "hardwareConcurrency", { get: exportFunction(() => 8, pageWindow), configurable: true });
        } catch(e) {}
        try {
            Object.defineProperty(pageWindow.navigator.wrappedJSObject, "deviceMemory", { get: exportFunction(() => 8, pageWindow), configurable: true });
        } catch(e) {}

        // 5. Screen standardisation
        try {
            Object.defineProperty(pageWindow.screen.wrappedJSObject, "width",      { get: exportFunction(() => 412, pageWindow), configurable: true });
            Object.defineProperty(pageWindow.screen.wrappedJSObject, "height",     { get: exportFunction(() => 915, pageWindow), configurable: true });
            Object.defineProperty(pageWindow.screen.wrappedJSObject, "colorDepth", { get: exportFunction(() => 24,  pageWindow), configurable: true });
            Object.defineProperty(pageWindow.screen.wrappedJSObject, "pixelDepth", { get: exportFunction(() => 24,  pageWindow), configurable: true });
        } catch(e) {}

        // 6. Empty plugins list
        try {
            Object.defineProperty(pageWindow.navigator.wrappedJSObject, "plugins", { get: exportFunction(() => cloneInto([], pageWindow), pageWindow), configurable: true });
        } catch(e) {}

    } catch(e) {}
})();


// 9. Google AI Overview — targeted MutationObserver (only on Google search pages)
function watchForGoogleAiOverview() {
    const host = (window.location.hostname || "").toLowerCase();
    if (!host.includes("google.")) return;

    // All known AI Overview container selectors combined
    const AI_SELECTORS = [
        "#Odp5De", ".hdzaWe", "#gds-aic", ".XqgnIe", ".M8OgIe",
        ".YzCOte", ".UA0FGd", ".BmP5tf", ".kkjOtf", ".k8XOCe",
        "div[jsname='Bz112c']", "div[jsname='oIq1Zb']", "div[jsname='wx3skf']",
        "div[jsname='wMPnde']", "div[jsname='BiCFae']", "div[jsname='r3p0Jd']",
        "div[jsname='yDeuDf']", "div[jsname='X9lQic']", "div[jsname='b4e54d']",
        "div[jsname='bN97ce']", "div[jsname='LgbsSe']", "div[jsname='FzSmSb']",
        "div[aria-label*='AI Overview']", "div[aria-label*='AI overview']",
        "div[data-attrid*='generative']", "div[jscontroller*='GenerativeSearch']",
        "div[jscontroller='MxYRTe']", "div[jscontroller='AUvrTe']",
        "[data-sge-block='true']", "div[data-aioc]", "div[data-aipc]",
        "#genai_carousel", "[data-attrid*='sge']", "div[data-oshdr]"
    ].join(", ");

    function removeAiNodes(root) {
        try {
            root.querySelectorAll(AI_SELECTORS).forEach(el => {
                // Walk up to remove the containing card if it only holds AI content
                let target = el;
                while (target.parentElement &&
                       target.parentElement.children.length === 1 &&
                       !target.parentElement.id.startsWith("rso") &&
                       target.parentElement !== document.body) {
                    target = target.parentElement;
                }
                target.remove();
            });
        } catch(e) {}
    }

    // Run immediately on DOMContentLoaded and load
    window.addEventListener("DOMContentLoaded", () => removeAiNodes(document));
    window.addEventListener("load", () => removeAiNodes(document));

    // Watch for dynamic injection (Google renders AI Overview after initial load)
    const aiObserver = new MutationObserver((mutations) => {
        for (const m of mutations) {
            if (m.addedNodes.length > 0) {
                removeAiNodes(document);
                break;
            }
        }
    });

    // Start observing once body exists
    function startObserver() {
        if (document.body) {
            aiObserver.observe(document.body, { childList: true, subtree: true });
        } else {
            window.addEventListener("DOMContentLoaded", () => {
                aiObserver.observe(document.body, { childList: true, subtree: true });
            }, { once: true });
        }
    }
    startObserver();
}

injectStyleShield();
observeFormLogins();
injectAntiDetectionShield();
watchForGoogleAiOverview();

// Run lightweight DOM cleaner only on DOM ready and load events (zero continuous CPU overhead)
window.addEventListener("DOMContentLoaded", () => {
    cleanAdNodes(document);
    observeFormLogins();
});
window.addEventListener("load", () => cleanAdNodes(document));

// Listen to commands from native app / background
browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.action === "extract_article") {
        if (typeof extractArticleContent === "function") {
            const article = extractArticleContent(document);
            sendResponse(article);
        } else {
            sendResponse({
                title: document.title,
                byline: "",
                contentHtml: document.body ? document.body.innerHTML : "",
                isArticle: false
            });
        }
    } else if (message.action === "set_dark_mode") {
        applyDarkWebMode(message.enabled);
        sendResponse({ success: true });
    } else if (message.action === "set_cosmetic_blocking") {
        cosmeticBlockingActive = !!message.enabled;
        if (cosmeticBlockingActive) {
            injectStyleShield();
            cleanAdNodes(document);
        } else {
            removeStyleShield();
        }
        sendResponse({ success: true });
    } else if (message.action === "set_zoom") {
        applyTextZoom(message.percent);
        sendResponse({ success: true });
    } else if (message.action === "autofill_login") {
        try {
            const passInput = document.querySelector('input[type="password"]');
            if (passInput && message.password) {
                passInput.value = message.password;
                passInput.dispatchEvent(new Event("input", { bubbles: true }));
                passInput.dispatchEvent(new Event("change", { bubbles: true }));
            }
            if (message.username) {
                const userInputs = document.querySelectorAll('input[type="text"], input[type="email"], input[name*="user"], input[name*="login"], input[id*="user"]');
                if (userInputs.length > 0) {
                    userInputs[0].value = message.username;
                    userInputs[0].dispatchEvent(new Event("input", { bubbles: true }));
                    userInputs[0].dispatchEvent(new Event("change", { bubbles: true }));
                }
            }
        } catch (e) {}
        sendResponse({ success: true });
    }
    return true;
});
