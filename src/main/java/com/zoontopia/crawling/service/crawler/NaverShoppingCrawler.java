package com.zoontopia.crawling.service.crawler;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.zoontopia.crawling.domain.Product;
import com.zoontopia.crawling.service.GeminiParsingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverShoppingCrawler implements ShoppingCrawler {

    private final GeminiParsingService geminiParsingService;
    private static final String NAVER_SHOPPING_URL = "https://search.shopping.naver.com/home";

    @Override
    public List<Product> searchProducts(String keyword) {
        log.info("Starting crawl for keyword: {}", keyword);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext(new Browser.NewContextOptions().setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36"));
            Page page = context.newPage();

            // 1. 네이버 쇼핑 홈으로 이동
            page.navigate(NAVER_SHOPPING_URL);

            // 2. 검색어 입력 및 검색 실행
            // 제공해주신 HTML 구조를 기반으로 셀렉터 지정
            Locator searchInput = page.locator("input[title='검색어 입력']");
            searchInput.fill(keyword);
            
            // 검색 버튼 클릭 (제공된 클래스명 사용)
            Locator searchButton = page.locator("button._searchInput_button_search_wu9xq");
            if (searchButton.count() > 0) {
                searchButton.click();
            } else {
                // 버튼을 찾지 못한 경우 엔터 키로 검색 실행
                searchInput.press("Enter");
            }

            // 3. 결과 로딩 및 스크롤 다운 (전체 상품 로딩)
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            
            // 페이지 끝까지 스크롤하여 지연 로딩된 상품들 불러오기
            // 네이버 쇼핑은 스크롤을 내려야 추가 상품이 로딩됩니다.
            log.info("Scrolling down to load all products...");
            int previousHeight = 0;
            // 초기 높이 가져오기
            Object heightObj = page.evaluate("document.body.scrollHeight");
            int currentHeight = heightObj instanceof Integer ? (Integer) heightObj : ((Double) heightObj).intValue();
            
            int attempts = 0;
            while (previousHeight != currentHeight && attempts < 20) { // 최대 20번 스크롤 시도 (무한 루프 방지)
                previousHeight = currentHeight;
                page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                page.waitForTimeout(500); // 로딩 대기 (0.5초)
                
                heightObj = page.evaluate("document.body.scrollHeight");
                currentHeight = heightObj instanceof Integer ? (Integer) heightObj : ((Double) heightObj).intValue();
                
                attempts++;
            }
            log.info("Scroll finished. Attempts: {}", attempts);
            
            // 최종 렌더링 안정화 대기
            page.waitForTimeout(1000);

            // 4. HTML 추출
            // 효율성을 위해 가능하면 메인 리스트 컨테이너를 가져오려고 시도합니다.
            // 하지만 body를 가져오는 것이 AI가 파싱하기에 안전한 폴백 방법입니다.
            // 토큰을 절약하기 위해 리스트 div를 타겟팅할 수도 있습니다.
            
            // 브라우저 측면 최적화: 불필요한 태그 미리 제거
            page.evaluate("() => { " +
                    "document.querySelectorAll('script, style, svg, head, footer, header').forEach(el => el.remove()); " +
                    "}");

            String content = page.content();
            
            // 공통 메서드로 HTML 추가 정제 (주석 제거, 공백 정리 등)
            String cleanedContent = cleanHtml(content);
            
            // 5. Gemini를 사용하여 파싱
            return geminiParsingService.parseHtmlToProducts(cleanedContent, keyword, getPlatform());

        } catch (Exception e) {
            log.error("Error during Naver crawling", e);
            throw new RuntimeException("Crawling failed", e);
        }
    }

    @Override
    public String getPlatform() {
        return "NAVER";
    }
}
