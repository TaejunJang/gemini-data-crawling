package com.zoontopia.crawling.service.crawler;

import com.microsoft.playwright.*;
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
public class GeumCheonMeatCrawler implements ShoppingCrawler {

    private final GeminiParsingService geminiParsingService;
    private static final String GEUMCHOEN_MEAT_URL = "https://www.ekcm.co.kr/";

    @Override
    public List<Product> searchProducts(String keyword) {
        log.info("Starting crawl for keyword: {}", keyword);

        try (Playwright playwright = Playwright.create()) {
            // 1. Headless를 false로 설정하여 테스트 (차단 확인용)
            // 실제 운영 시에도 slowMo를 추가하여 동작 사이에 간격을 줍니다.
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true) // 차단될 때는 잠시 false로 두고 확인하세요
                    .setSlowMo(100));   // 각 동작 사이에 0.1초씩 지연 발생

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080));

            Page page = context.newPage();

            // [중요] 봇 감지 우회 스크립트 주입 (webdriver 속성 제거)
            page.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

            page.navigate(GEUMCHOEN_MEAT_URL);

            // 사람처럼 보이게 하기 위해 잠시 대기
            page.waitForTimeout(2000);

            // 검색어 입력 시 한 글자씩 입력하는 효과 (사람처럼 보이게)
            Locator searchInput = page.locator("#schText");
            searchInput.click(); // 먼저 클릭
            page.keyboard().type(keyword, new Keyboard.TypeOptions().setDelay(100)); // 0.1초 간격으로 타이핑

            page.keyboard().press("Enter");

            // 3. 결과 로딩 및 스크롤 다운 (전체 상품 로딩)
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            
            // 페이지 끝까지 스크롤하여 지연 로딩된 상품들 불러오기
            log.info("Scrolling down incrementally to load all products...");
            
            long lastHeight = ((Number) page.evaluate("document.body.scrollHeight")).longValue();
            int attempts = 0;
            int maxAttempts = 10; // 최대 스크롤 회수 10회로 제한

            while (attempts < maxAttempts) {
                // Java 루프 대신 브라우저 내부에서 JS로 부드럽게 스크롤 실행
                // 현재 위치에서 바닥까지 천천히 스크롤
                page.evaluate("async () => {" +
                        "const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));" +
                        "const totalHeight = document.body.scrollHeight;" +
                        "let currentY = window.scrollY;" +
                        "" +
                        "while (window.scrollY + window.innerHeight < totalHeight) {" +
                        "    window.scrollBy(0, 500);" +   // 100px씩 부드럽게 이동
                        "    await delay(1000);" +         // 1초 대기
                        "    if (document.body.scrollHeight > totalHeight) break;" + 
                        "}" +
                        "}");
                
                // 렌더링 및 추가 로딩 대기 (2초)
                page.waitForTimeout(2000);
                
                long newHeight = ((Number) page.evaluate("document.body.scrollHeight")).longValue();
                
                if (newHeight == lastHeight) {
                     // 높이 변화가 없으면 종료 (바닥 도달)
                    log.info("Finished scrolling. Final height: {}", newHeight);
                    break;
                }
                
                lastHeight = newHeight;
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
            log.error("Error during GeumCheonMeat crawling", e);
            throw new RuntimeException("Crawling failed", e);
        }
    }

    @Override
    public String getPlatform() {
        return "gcmeat";
    }
}
