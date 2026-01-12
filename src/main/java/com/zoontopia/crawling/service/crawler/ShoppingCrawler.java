package com.zoontopia.crawling.service.crawler;

import com.zoontopia.crawling.domain.Product;

import java.util.List;
import java.util.regex.Pattern;

public interface ShoppingCrawler {
    /**
     * Search for products by keyword and return a list of extracted products.
     * @param keyword The search term.
     * @return List of Product entities.
     */
    List<Product> searchProducts(String keyword);
    
    /**
     * Returns the platform name supported by this crawler.
     */
    String getPlatform();

    /**
     * Cleans the HTML content by removing scripts, styles, SVGs, and comments
     * to reduce the token count sent to the LLM.
     * @param html The raw HTML string.
     * @return Cleaned HTML string.
     */
    default String cleanHtml(String html) {
        if (html == null) return "";

        // 1. Remove script tags and content
        String cleaned = Pattern.compile("<script[^>]*>.*?</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(html).replaceAll("");
        
        // 2. Remove style tags and content
        cleaned = Pattern.compile("<style[^>]*>.*?</style>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(cleaned).replaceAll("");
        
        // 3. Remove SVG tags and content
        cleaned = Pattern.compile("<svg[^>]*>.*?</svg>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(cleaned).replaceAll("");
        
        // 4. Remove comments
        cleaned = Pattern.compile("<!--.*?-->", Pattern.DOTALL).matcher(cleaned).replaceAll("");
        
        // 5. Remove head tag and content (optional, but usually not needed for product list)
        cleaned = Pattern.compile("<head[^>]*>.*?</head>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(cleaned).replaceAll("");
        
        // 6. Remove excessive whitespace
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        
        return cleaned;
    }
}
