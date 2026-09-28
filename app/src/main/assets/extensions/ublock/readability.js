/**
 * Standalone Mozilla Readability Lightweight Engine
 * Extracts article title, author, content, and removes all clutter.
 */
function extractArticleContent(doc) {
    try {
        const documentClone = doc.cloneNode(true);
        
        // Remove known non-article elements from clone
        const junkSelectors = [
            "script", "style", "nav", "header", "footer", "aside", 
            ".comments", ".comment-section", ".sidebar", ".ad-container",
            ".social-share", ".newsletter-signup", ".cookie-banner",
            "[role='banner']", "[role='navigation']", "[role='complementary']"
        ];
        
        junkSelectors.forEach(sel => {
            const nodes = documentClone.querySelectorAll(sel);
            nodes.forEach(n => n.remove());
        });

        // 1. Try finding semantic <article> or <main>
        let articleNode = documentClone.querySelector("article") || 
                          documentClone.querySelector("main") || 
                          documentClone.querySelector("[role='main']") || 
                          documentClone.querySelector(".post-content") || 
                          documentClone.querySelector(".article-body") || 
                          documentClone.querySelector(".entry-content");

        // 2. If not found, score block elements by paragraph count and text length
        if (!articleNode) {
            const candidates = documentClone.querySelectorAll("div, section");
            let bestCandidate = null;
            let maxScore = 0;

            candidates.forEach(el => {
                const paragraphs = el.querySelectorAll("p");
                const textLength = el.textContent ? el.textContent.trim().length : 0;
                const linkLength = Array.from(el.querySelectorAll("a")).reduce((acc, a) => acc + (a.textContent ? a.textContent.length : 0), 0);
                
                // Penalize links heavy sections (menus/footers)
                const linkDensity = textLength > 0 ? (linkLength / textLength) : 1;
                const score = (paragraphs.length * 20 + textLength * 0.1) * (1 - linkDensity);

                if (score > maxScore) {
                    maxScore = score;
                    bestCandidate = el;
                }
            });

            articleNode = bestCandidate || documentClone.body;
        }

        // Clean up images: fix relative URLs
        const images = articleNode.querySelectorAll("img");
        images.forEach(img => {
            if (img.src) {
                try {
                    img.src = new URL(img.src, window.location.href).href;
                } catch(e) {}
            }
            img.removeAttribute("srcset");
            img.style.maxWidth = "100%";
            img.style.height = "auto";
            img.style.borderRadius = "8px";
            img.style.margin = "16px 0";
        });

        // Extract title
        let title = "";
        const h1 = doc.querySelector("h1");
        if (h1 && h1.textContent.trim()) {
            title = h1.textContent.trim();
        } else {
            title = doc.title || "Article";
        }

        // Extract author/byline
        let byline = "";
        const authorEl = doc.querySelector("[rel='author'], .byline, .author, meta[name='author']");
        if (authorEl) {
            byline = authorEl.getAttribute("content") || authorEl.textContent.trim();
        }

        return {
            title: title,
            byline: byline,
            contentHtml: articleNode.innerHTML,
            isArticle: (articleNode.textContent || "").trim().length > 250
        };
    } catch (e) {
        console.error("Readability extraction error:", e);
        return {
            title: doc.title || "Page Content",
            byline: "",
            contentHtml: doc.body ? doc.body.innerHTML : "",
            isArticle: false
        };
    }
}
