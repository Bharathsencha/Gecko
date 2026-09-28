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
    ".adsbox", ".ad-banner", ".ad-container", ".ad-wrapper", ".ad-placement",
    ".adsbygoogle", ".ad-slot", ".ad-unit", ".bn-wrapper", ".ad-zone",
    ".ad-holder", ".ad-space", ".ad-module", ".ad-break",
    
    // IDs
    "#ad-container", "#ad-slot", "#div-gpt-ad", "#google_ads_iframe",
    "#advertisement", "#header-ad", "#sidebar-ads", "#footer-ad",
    "#banner-ads", "#ad-banner-top", "#ad-box",
    
    // Attribute wildcards
    "div[id*='google_ad']", "div[class*='ad-server']", "div[id*='ad-banner']",
    "div[class*='ad-placement']", "div[class*='ad-wrapper']", "div[id*='gpt-ad-']",
    "div[id*='taboola-']", "div[id*='outbrain-']", "div[class*='sponsored-post']",
    "div[data-ad-client]", "div[data-ad-slot]", "div[data-ad-format]",
    
    // Ad iframes
    "iframe[src*='doubleclick.net']", "iframe[src*='googleads']", "iframe[src*='adnxs']",
    "iframe[src*='adcolony']", "iframe[src*='pubmatic']", "iframe[src*='taboola']",
    "iframe[src*='outbrain']", "iframe[src*='criteo']", "iframe[src*='smartadserver']",
    "iframe[id*='google_ads_iframe']", "iframe[title*='Advertisement']",
    
    // Video & Overlay Ads
    ".video-ad-overlay", ".ytp-ad-overlay-container", ".ytp-ad-message-container",
    ".ytp-ad-player-overlay", ".vjs-ad-overlay", ".ima-ad-container",
    
    // Annoyances, Cookie Modals, Popups & CMP Banners
    ".popup-ad", ".overlay-ad", ".interstitial-ad", ".ad-interstitial",
    ".cookie-consent", ".cookie-banner", "#cookie-notice", ".cookie-law-info",
    "#onetrust-consent-sdk", ".cc-banner", ".cmp-container", "#didomi-host",
    "#qc-cmp2-container", ".truste_overlay", ".truste_box_overlay", ".fc-ab-root",
    ".fc-dialog-container", "#sp_message_container", ".osano-cm-window",
    
    // Google AI Overviews, SGE & Generative AI Search Clutter
    "#Odp5De", "div[jsname='yDeuDf']", ".hdzaWe", "div[jscontroller*='GenerativeSearch']",
    "div[data-attrid*='generative']", ".g-blk", "[data-sge-block='true']",
    ".MjjYud:has(#Odp5De)", ".MjjYud:has(.hdzaWe)", "div[class*='copilot']", ".b_ans_ai"
];

// 1. Inject Early CSS Shield & Smooth Typography
let cosmeticBlockingActive = true;

function injectStyleShield() {
    if (!cosmeticBlockingActive) return;
    let styleEl = document.getElementById("ublock-cosmetic-shield");
    if (!styleEl) {
        styleEl = document.createElement("style");
        styleEl.id = "ublock-cosmetic-shield";
        
        const cosmeticRules = cosmeticSelectors.map(sel => 
            `${sel} { display: none !important; visibility: hidden !important; height: 0 !important; max-height: 0 !important; width: 0 !important; opacity: 0 !important; pointer-events: none !important; margin: 0 !important; padding: 0 !important; }`
        ).join("\n");

        const typographyRules = `
            html, body {
                -webkit-font-smoothing: antialiased !important;
                -moz-osx-font-smoothing: grayscale !important;
                text-rendering: optimizeLegibility !important;
            }
        `;

        styleEl.textContent = cosmeticRules + "\n" + typographyRules;

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

// 2. DOM Cleaner: Instantly remove ad elements
function cleanAdNodes(root) {
    if (!cosmeticBlockingActive) return;
    if (!root || !root.querySelectorAll) return;
    try {
        const query = cosmeticSelectors.join(", ");
        const elements = root.querySelectorAll(query);
        let removedCount = 0;
        for (let i = 0; i < elements.length; i++) {
            elements[i].remove();
            removedCount++;
        }
        if (removedCount > 0 && typeof browser !== "undefined" && browser.runtime && browser.runtime.sendMessage) {
            browser.runtime.sendMessage({ action: "cosmetic_ads_blocked", count: removedCount }).catch(() => {});
        }
    } catch (e) {}
}

// 3. Prevent popup hijacking (window.open without user direct click)
function neutralizePopups() {
    try {
        const originalOpen = window.open;
        window.open = function(url, target, features) {
            if (!url || typeof url !== "string") {
                return originalOpen.apply(this, arguments);
            }
            const lower = url.toLowerCase();
            if (lower.includes("popads") || lower.includes("propeller") || lower.includes("adcash") || lower.includes("adsterra") || lower.includes("trafficjunky")) {
                console.warn("[uBlock] Blocked popup attempt:", url);
                return null;
            }
            return originalOpen.apply(this, arguments);
        };
    } catch (e) {}
}

// 4. Smart Deep Dark Web Mode via Official Dark Reader Engine
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

injectStyleShield();
neutralizePopups();
observeFormLogins();

// Monitor DOM mutations to eradicate ad elements before render
const observer = new MutationObserver((mutations) => {
    if (!cosmeticBlockingActive) return;
    for (const mutation of mutations) {
        for (const node of mutation.addedNodes) {
            if (node.nodeType === 1) { // ELEMENT_NODE
                cleanAdNodes(node);
            }
        }
    }
});

observer.observe(document.documentElement, {
    childList: true,
    subtree: true
});

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
