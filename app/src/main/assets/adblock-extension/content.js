// Cosmetic Blocking: Add style sheet to hide common ad layout selectors (similar to uBlock)
const adSelectors = [
    // Standard ad containers and wrappers
    ".adsbox", ".ad-banner", ".ad-container", ".ad-wrapper", ".ad-placement",
    ".adsbygoogle", ".ad-slot", ".ad-unit", ".bn-wrapper",
    // Common ID selectors
    "#ad-container", "#ad-slot", "#div-gpt-ad", "#google_ads_iframe",
    // Attribute wildcards (handles many dynamically-generated ad ids/classes)
    "div[id*='google_ad']", "div[class*='ad-server']", "div[id*='ad-banner']",
    "iframe[src*='doubleclick.net']", "iframe[src*='googleads']", "iframe[src*='adnxs']",
    "iframe[src*='adcolony']", "iframe[src*='pubmatic']", "iframe[src*='taboola']",
    // Popup and video ad overlays
    ".video-ad-overlay", ".ytp-ad-overlay-container", ".ytp-ad-message-container"
];

// Apply cosmetic hiding stylesheet
function injectCosmeticFilters() {
    const style = document.createElement("style");
    style.id = "ublock-cosmetic-hider";
    style.textContent = adSelectors.map(selector => `${selector} { display: none !important; visibility: hidden !important; height: 0 !important; width: 0 !important; opacity: 0 !important; pointer-events: none !important; }`).join("\n");
    
    // Inject as early as possible
    if (document.head) {
        document.head.appendChild(style);
    } else {
        const observer = new MutationObserver((mutations, obs) => {
            if (document.head) {
                document.head.appendChild(style);
                obs.disconnect();
            }
        });
        observer.observe(document.documentElement, { childList: true });
    }
}

injectCosmeticFilters();

// Video Detection Logic
function reportVideo(videoElement) {
    try {
        let src = videoElement.src;
        if (!src && videoElement.currentSrc) {
            src = videoElement.currentSrc;
        }
        
        // If still empty, check inside <source> children
        if (!src) {
            const sources = videoElement.querySelectorAll("source");
            for (let i = 0; i < sources.length; i++) {
                if (sources[i].src) {
                    src = sources[i].src;
                    break;
                }
            }
        }
        
        if (src && !src.startsWith("blob:") && !src.startsWith("data:")) {
            // Resolve relative URLs to absolute
            const absoluteSrc = new URL(src, window.location.href).href;
            
            // Send to background script
            browser.runtime.sendMessage({
                action: "video_detected",
                url: absoluteSrc,
                title: document.title || "Embedded Web Video",
                format: absoluteSrc.includes(".m3u8") ? "HLS (M3U8)" : "MP4"
            });
        }
    } catch (e) {
        console.error("Error detecting video element src: ", e);
    }
}

// Listen to video plays
document.addEventListener("play", function(event) {
    if (event.target && event.target.tagName === "VIDEO") {
        reportVideo(event.target);
    }
}, true);

// Periodically scan page for already-loaded video elements
function scanForVideos() {
    const videos = document.querySelectorAll("video");
    videos.forEach(video => {
        // If video is playing or has metadata, report it
        if (video.src || video.currentSrc) {
            reportVideo(video);
        }
    });
}

// Scan on load and DOM mutations
window.addEventListener("DOMContentLoaded", scanForVideos);
window.addEventListener("load", scanForVideos);

// Keep an eye on DOM updates to catch late-inserted videos
const domObserver = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
        mutation.addedNodes.forEach((node) => {
            if (node.tagName === "VIDEO") {
                reportVideo(node);
            } else if (node.querySelectorAll) {
                const videos = node.querySelectorAll("video");
                videos.forEach(video => reportVideo(video));
            }
        });
    });
});

domObserver.observe(document.documentElement, {
    childList: true,
    subtree: true
});
