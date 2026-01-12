# Gemini Data Crawling Project

이 프로젝트는 **Playwright**를 사용하여 웹 페이지의 HTML 구조를 추출하고, Google **Gemini AI**를 활용하여 비정형 HTML 데이터에서 구조화된 상품 정보(JSON)를 파싱하는 지능형 크롤링 애플리케이션입니다.

## 🚀 주요 기능

- **지능형 파싱:** 복잡한 CSS Selector나 XPath 하드코딩 없이, HTML 전체를 Gemini에게 전달하여 필요한 데이터(상품명, 가격, 판매자 등)를 추출합니다.
- **동적 웹 크롤링:** **Playwright**를 사용하여 JavaScript로 렌더링되는 동적 페이지(네이버 쇼핑 등)를 완벽하게 크롤링합니다.
- **사람 같은 스크롤링:** 봇 탐지를 우회하고 지연 로딩 데이터를 확보하기 위해 점진적이고 자연스러운 스크롤 로직을 구현했습니다.
- **대용량 처리:** Gemini API의 출력 토큰 제한을 최적화하여 긴 상품 목록도 한 번에 처리합니다.
- **데이터 저장:** 추출된 데이터는 **MongoDB**에 저장되어 관리됩니다.

## 🛠 기술 스택

- **Language:** Java 25
- **Framework:** Spring Boot 3.5.9
- **AI Integration:** Spring AI (Google Gemini 2.5 Flash)
- **Crawling:** Microsoft Playwright 1.57.0
- **Database:** MongoDB
- **Build Tool:** Gradle

## ⚙️ 설정 및 실행 방법

### 1. 환경 변수 설정
프로젝트 루트에 `.env` 파일을 생성하거나 시스템 환경 변수로 다음 값을 설정해야 합니다.

```properties
GOOGLE_PROJECT_ID=your-google-project-id
GOOGLE_API_KEY=your-gemini-api-key
```

### 2. MongoDB 설정
`application.yml` 또는 `compose.yaml`을 통해 MongoDB가 실행 중이어야 합니다.
기본 설정:
- URI: `mongodb://admin:1234@localhost:27017/crawling_db?authSource=admin`

### 3. 애플리케이션 실행
```bash
./gradlew bootRun
```

## 📡 API 사용법

### 상품 크롤링 요청
특정 키워드로 상품을 검색하고 크롤링을 수행합니다.

- **URL:** `POST /api/crawl/products` (예시 경로, 실제 컨트롤러 확인 필요)
- **Body:**
  ```json
  {
    "keyword": "노트북",
    "platform": "NAVER"
  }
  ```

## 📁 프로젝트 구조
```
src/main/java/com/zoontopia/crawling
├── controller      # API 엔드포인트 처리
├── domain          # MongoDB 엔티티 (Product)
├── repository      # MongoDB 리포지토리
├── service
│   ├── crawler     # Playwright 기반 크롤러 (NaverShoppingCrawler)
│   └── GeminiParsingService.java # Gemini AI 파싱 로직
└── CrawlingApplication.java
```

## 📝 주요 로직 설명
1. **HTML 추출:** `NaverShoppingCrawler`가 브라우저를 띄워 스크롤하며 페이지 로딩을 완료하고 HTML을 가져옵니다.
2. **AI 파싱:** `GeminiParsingService`가 HTML을 Gemini에 전송합니다. 프롬프트를 통해 "상품명", "가격", "단가" 등을 JSON 포맷으로 추출하도록 지시합니다.
3. **데이터 정제:** AI 응답(JSON)을 파싱하여 `Product` 객체로 변환하고, 날짜 포맷(`YYYY-MM-DD`) 및 숫자형 데이터를 정규화합니다.
4. **저장:** 최종 결과물을 MongoDB에 저장합니다.