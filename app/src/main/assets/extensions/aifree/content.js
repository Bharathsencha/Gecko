/**
 * AI Free Content Script
 * Handles: Google AI Overview removal, Reader mode extraction
 * Ad blocking → real uBlock Origin
 * Dark mode → real Dark Reader
 */

// ── 1. Google AI Overview Blocker ──────────────────────────────────────────
const AI_OVERVIEW_SELECTORS = [
    // 2024-2026 Google AI Overview containers
    "#Odp5De", "div[jsname='yDeuDf']", ".hdzaWe",
    "div[jscontroller*='GenerativeSearch']",
    "div[aria-label*='AI Overview']", "div[aria-label*='AI overview']",
    "div[data-attrid*='generative']", ".g-blk", "[data-sge-block='true']",
    ".MjjYud:has(#Odp5De)", ".MjjYud:has(.hdzaWe)",
    "#genai_carousel", "div[jsname='X9lQic']",
    "div[jsname='Bz112c']", "div[jsname='oIq1Zb']", "div[jsname='wx3skf']",
    "div[jsname='wMPnde']", "div[jsname='BiCFae']", "div[jsname='r3p0Jd']",
    "#gds-aic", "#kp-wp-tab-overview", ".YzCOte", ".XqgnIe", ".M8OgIe",
    "div[data-aioc]", "div[data-aipc]",
    "div[jscontroller='MxYRTe']", "div[jscontroller='AUvrTe']",
    "div[jscontroller='SHcN5']", ".k8XOCe", ".wDYxhc:has(.XqgnIe)",
    "div[jscontroller*='AI']", "div[data-oshdr]", "div[jsname='FzSmSb']",
    ".UA0FGd", ".BmP5tf", ".kkjOtf",
    "div[class*='ai-overview']", "[id*='ai-overview']",
    ".hJnd5c", "div[jsname='Bz112c']"
].join(", ");

function injectAiOverrideStyle() {
    const host = (window.location.hostname || "").toLowerCase();
    if (!host.includes("google.")) return;

    let style = document.getElementById("aifree-ai-block");
    if (!style) {
        style = document.createElement("style");
        style.id = "aifree-ai-block";
        style.textContent = AI_OVERVIEW_SELECTORS.split(", ")
            .map(s => `${s} { display: none !important; }`)
            .join("\n");
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

// ── 2. Reader Mode Article Extraction ──────────────────────────────────────
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

// ── 3. Init ────────────────────────────────────────────────────────────────
watchForGoogleAiOverview();

window.addEventListener("DOMContentLoaded", () => {
    removeAiNodes(document);
});

// Listen for commands from native app
browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.action === "extract_article") {
        sendResponse(extractArticleContent(document));
    }
    return true;
});
