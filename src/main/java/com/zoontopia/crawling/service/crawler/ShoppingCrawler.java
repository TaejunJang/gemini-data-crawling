package com.zoontopia.crawling.service.crawler;

import com.microsoft.playwright.Page;
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

    default void humanLikeType(Page page, String keyword) {
        // 1. 글자 하나씩 입력
        for (char c : keyword.toCharArray()) {
            page.keyboard().type(String.valueOf(c));

            // 각 글자 사이의 지연 시간을 50ms ~ 250ms 사이로 랜덤하게 부여
            long randomDelay = (long) (Math.random() * 200) + 50;
            page.waitForTimeout(randomDelay);

            // 2. 가끔 입력을 멈추고 고민하는 척 (10% 확률로 0.5초~1초 대기)
            if (Math.random() < 0.1) {
                page.waitForTimeout((long) (Math.random() * 500) + 500);
            }
        }

        // 3. 입력을 마친 후 바로 엔터를 치지 않고 '확인'하는 텀 (0.7초 ~ 1.5초)
        page.waitForTimeout((long) (Math.random() * 800) + 700);
    }
}
