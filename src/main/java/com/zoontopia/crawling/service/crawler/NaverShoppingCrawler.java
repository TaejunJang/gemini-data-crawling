package com.zoontopia.crawling.service.crawler;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.zoontopia.crawling.domain.Product;
import com.zoontopia.crawling.service.GeminiParsingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverShoppingCrawler implements ShoppingCrawler {

    private final GeminiParsingService geminiParsingService;
    private static final String NAVER_SHOPPING_URL = "https://www.naver.com/";

    @Override
    public List<Product> searchProducts(String keyword) {
        log.info("Starting crawl for keyword: {}", keyword);

        try (Playwright playwright = Playwright.create()) {
            // 1. Headless를 false로 설정하여 테스트 (차단 확인용)
            // 실제 운영 시에도 slowMo를 추가하여 동작 사이에 간격을 줍니다.
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false) // 차단될 때는 잠시 false로 두고 확인하세요
                    .setSlowMo(100));   // 각 동작 사이에 0.1초씩 지연 발생

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080)
                    .setExtraHTTPHeaders(Map.of(
                            "Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
                            "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
                    )));

            // [Stealth 모드 적용 - 중요 수정] 
            // page가 아니라 context에 적용해야 팝업(새 탭)에서도 우회 기능이 유지됨
            try (java.io.InputStream is = getClass().getResourceAsStream("/stealth.min.js")) {
                if (is != null) {
                    String stealthScript = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    context.addInitScript(stealthScript); // context에 주입
                    log.info("Stealth script injected to Context successfully.");
                } else {
                    log.warn("Stealth script not found in resources!");
                    context.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
                }
            } catch (Exception e) {
                log.error("Failed to inject stealth script", e);
            }

            Page page = context.newPage();

            // 1. 네이버 접속
            page.navigate(NAVER_SHOPPING_URL);
            
            // [수정] NETWORKIDLE은 광고/트래킹 스크립트로 인해 타임아웃 발생 가능성이 높음.
            // 대신 DOMCONTENTLOADED 상태를 기다리고, 추가적으로 짧은 시간을 대기하여 안정성 확보.
            page.waitForLoadState(LoadState.DOMCONTENTLOADED); 
            page.waitForTimeout(1000); // 1초 정도 추가 안정화 대기

            // 2. 검색어 입력 (가시성 필터링 추가)
            // input[name='q'] 중 현재 화면에 보이는 것을 찾아 첫 번째 것을 선택
            //Locator searchInput = page.locator("input[name='q']").filter(new Locator.FilterOptions().setVisible(true)).first();
            Locator searchInput = page.getByTitle("검색어를 입력해 주세요.");

            // 요소가 나타날 때까지 명시적으로 대기
            searchInput.waitFor(new Locator.WaitForOptions().setTimeout(20000));

            searchInput.click();

            // 섬세한 타이핑 실행
            humanLikeType(page, keyword);

            Locator searchButton = page.locator("#search-btn").first();

            // 1. 버튼 선택 (가장 추천하는 1번 방식 사용)
            //Locator searchButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("검색")).first();

            // 2. 버튼이 화면에 보일 때까지 대기
            searchButton.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));

            // 3. 마우스를 버튼 위로 이동 (Hover) - 봇 탐지 우회 핵심
            searchButton.hover();

            // 4. 이동 후 아주 잠깐의 랜덤 대기 (사람의 반응 속도)
            page.waitForTimeout((long) (Math.random() * 400) + 200);

            // 5. 클릭 실행
            searchButton.click();

            // 검색 결과 페이지 로딩 대기
            // [수정] NETWORKIDLE -> DOMCONTENTLOADED 로 변경하여 무한 대기 방지
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(1000); // 결과 렌더링을 위한 약간의 대기

            // '네이버 가격비교 더보기' 링크 클릭
            log.info("Searching for '네이버 가격비교 더보기' link...");
            
            // 텍스트가 포함된 링크를 정확히 찾음
            Locator moreLink = page.getByText("네이버 가격비교 더보기").first();
            
            try {
                // 1. 요소가 나타날 때까지 대기 (최대 5초)
                moreLink.waitFor(new Locator.WaitForOptions().setTimeout(5000).setState(WaitForSelectorState.VISIBLE));
                
                // 2. 해당 위치로 부드럽게 스크롤 (가려져 있으면 클릭이 안 될 수 있음)
                moreLink.scrollIntoViewIfNeeded();
                
                // [사람 흉내내기] 마우스 올리고 잠시 대기
                moreLink.hover();
                page.waitForTimeout((long) (Math.random() * 500) + 200); // 0.2~0.7초 대기
                
                log.info("Clicking '네이버 가격비교 더보기' link.");
                
                // 3. 새 탭이 열리는 것을 대기하며 클릭 실행
                Page newPage = context.waitForPage(() -> {
                    moreLink.click();
                });
                
                // 4. 새 페이지 로딩 대기
                newPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
                log.info("Switched to new tab: {}", newPage.title());
                
                // 기존 페이지는 닫고 새 페이지로 전환
                page.close();
                page = newPage;

            } catch (Exception e) {
                log.warn("'네이버 가격비교 더보기' link not found or clickable: {}. Proceeding with current page.", e.getMessage());
            }

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
            log.error("Error during NaverShopping crawling", e);
            throw new RuntimeException("Crawling failed", e);
        }
    }

    @Override
    public String getPlatform() {
        return "naver";
    }
}
