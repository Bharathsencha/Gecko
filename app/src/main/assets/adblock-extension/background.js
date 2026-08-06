let blocklist = new Set();
let adBlockCount = 0;

// Default basic domains to block in case download fails or is slow
const defaultBlockDomains = [
    "doubleclick.net",
    "googleadservices.com",
    "adservice.google.com",
    "googlesyndication.com",
    "adnxs.com",
    "adcolony.com",
    "pubmatic.com",
    "rubiconproject.com",
    "adform.net",
    "openx.net",
    "scorecardresearch.com",
    "taboola.com",
    "outbrain.com",
    "popads.net",
    "propellerads.com",
    "criteo.com",
    "amazon-adsystem.com",
    "adroll.com",
    "quantserve.com",
    "flurry.com"
];

// Initialize default blocker list
defaultBlockDomains.forEach(d => blocklist.add(d));

// Load cached blocklist from local storage
browser.storage.local.get(["blocklist"], (result) => {
    if (result.blocklist && Array.isArray(result.blocklist)) {
        blocklist = new Set(result.blocklist);
        console.log("Loaded cached blocklist of size: " + blocklist.size);
    }
    updateBlocklist();
});

// Periodically fetch updated blocklist (AdGuard DNS hosts list)
async function updateBlocklist() {
    try {
        console.log("Fetching live AdGuard DNS hosts list...");
        const response = await fetch("https://v.firebog.net/hosts/AdguardDNS.txt");
        if (!response.ok) throw new Error("HTTP error " + response.status);
        const text = await response.text();
        const domains = text.split("\n")
            .map(line => line.trim())
            .filter(line => line && !line.startsWith("#") && !line.includes(" "));
        
        if (domains.length > 100) {
            blocklist = new Set(domains);
            browser.storage.local.set({ blocklist: domains });
            console.log("Successfully updated blocklist. Total rules: " + blocklist.size);
        }
    } catch (e) {
        console.error("Failed to update blocklist from internet: ", e);
    }
}

// Check every 24 hours
browser.alarms?.create("update-blocklist", { periodInMinutes: 1440 });
browser.alarms?.onAlarm.addListener((alarm) => {
    if (alarm.name === "update-blocklist") {
        updateBlocklist();
    }
});

// Helper function to check if domain is blocked
function isDomainBlocked(hostname) {
    if (!hostname) return false;
    let host = hostname.toLowerCase();
    
    // Check exact or subdomains
    while (host.includes(".")) {
        if (blocklist.has(host)) {
            return true;
        }
        let nextDot = host.indexOf(".");
        if (nextDot === -1 || nextDot === host.length - 1) break;
        host = host.substring(nextDot + 1);
    }
    return false;
}

// 1. Request Interception for Ad Blocking
browser.webRequest.onBeforeRequest.addListener(
    function(details) {
        try {
            const urlString = details.url;
            const url = new URL(urlString);
            const hostname = url.hostname;

            // Block Ads
            if (isDomainBlocked(hostname)) {
                adBlockCount++;
                // Notify native Kotlin app of ad block
                browser.runtime.sendNativeMessage("browser-app", { 
                    action: "blocked_ad", 
                    count: adBlockCount,
                    url: urlString 
                });
                return { cancel: true };
            }
        } catch (e) {
            console.error("Error in onBeforeRequest: ", e);
        }
        return { cancel: false };
    },
    { urls: ["<all_urls>"] },
    ["blocking"]
);

// 2. Response Headers Interception for MSE/YouTube/Blob Video Detection
browser.webRequest.onHeadersReceived.addListener(
    function(details) {
        try {
            const urlString = details.url;
            
            // Check for YouTube Video Streams
            if (urlString.includes("googlevideo.com/videoplayback")) {
                // Determine resolution or audio/video from URL parameters
                let format = "MP4 Video";
                if (urlString.includes("mime=audio")) {
                    // Skip audio chunks to avoid adding them as separate download options
                    return { responseHeaders: details.responseHeaders };
                }
                
                browser.runtime.sendNativeMessage("browser-app", {
                    action: "video_detected",
                    url: urlString,
                    title: "YouTube Video Stream",
                    format: "MP4 (YouTube)"
                });
                return { responseHeaders: details.responseHeaders };
            }

            // Inspect Content-Type headers for generic web video streams
            const headers = details.responseHeaders;
            const contentTypeHeader = headers.find(h => h.name.toLowerCase() === "content-type");
            
            if (contentTypeHeader) {
                const contentType = contentTypeHeader.value.toLowerCase();
                
                if (contentType.startsWith("video/") || 
                    contentType.includes("mpegurl") || 
                    contentType.includes("m3u8") ||
                    contentType.includes("application/dash+xml")) {
                    
                    let format = "MP4";
                    if (contentType.includes("mpegurl") || contentType.includes("m3u8")) format = "HLS (M3U8)";
                    else if (contentType.includes("dash")) format = "DASH (MPD)";
                    else if (contentType.includes("webm")) format = "WEBM";

                    browser.runtime.sendNativeMessage("browser-app", {
                        action: "video_detected",
                        url: urlString,
                        title: "Web Video Stream",
                        format: format
                    });
                }
            }
        } catch (e) {
            console.error("Error in onHeadersReceived: ", e);
        }
        return { responseHeaders: details.responseHeaders };
    },
    { urls: ["<all_urls>"] },
    ["responseHeaders"]
);

// Listen for messages from content.js (HTML5 video playing events)
browser.runtime.onMessage.addListener((message, sender) => {
    if (message.action === "video_detected") {
        // Forward details to native Kotlin app
        browser.runtime.sendNativeMessage("browser-app", {
            action: "video_detected",
            url: message.url,
            title: message.title,
            format: message.format || "MP4"
        });
    }
});
