package com.zoontopia.crawling.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zoontopia.crawling.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiParsingService {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    public List<Product> parseHtmlToProducts(String htmlContent, String keyword, String platform) {
        log.info("Sending HTML to Gemini for parsing. Length: {}", htmlContent.length());

        String prompt = """
                당신은 데이터 추출 전문가입니다.
                제공된 HTML에서 상품 정보를 추출하여 **Minified JSON 배열** 형식으로 반환하세요.
                
                ### 추출 필드:
                - productName (문자열)
                - price (정수형 숫자만, 예: 10000)
                - unitPrice (정수형 숫자만, 예: 100g당 1000원이면 1000)
                - seller (문자열)
                - productUrl (문자열)

                ### 필수 제약 사항:
                1. **최대 상위 40개의 상품만 추출하세요.** (매우 중요: 응답 길이 제한)
                2. 공백과 줄바꿈을 제거한 **Minified JSON** 포맷으로 응답하세요.
                3. 마크다운(```json)을 사용하지 말고 순수 JSON 문자열만 반환하세요.
                4. 설명이나 인사를 포함하지 마세요.
                5. 응답이 길어져서 잘릴 것 같으면 마지막 완성된 객체까지만 출력하고 배열을 닫으세요.
                
                HTML 콘텐츠:
                %s
                """;

        String response = chatClientBuilder.build()
                .prompt()
                .user(String.format(prompt, htmlContent))
                .call()
                .content();

        return convertJsonToProducts(response, keyword, platform);
    }

    private List<Product> convertJsonToProducts(String jsonResponse, String keyword, String platform) {
        List<Product> products = new ArrayList<>();
        
        // 1. JSON 정제 및 복구 시도
        String cleanJson = jsonResponse.replace("```json", "").replace("```", "").trim();
        
        // 만약 JSON이 제대로 닫히지 않았다면 복구 시도
        if (!cleanJson.endsWith("]")) {
            log.warn("JSON response appears truncated. Attempting to repair...");
            int lastCloseBrace = cleanJson.lastIndexOf("}");
            if (lastCloseBrace != -1) {
                // 마지막으로 닫힌 객체까지만 살리고 배열 닫기
                cleanJson = cleanJson.substring(0, lastCloseBrace + 1) + "]";
                log.info("Repaired JSON: {}", cleanJson);
            }
        }

        try {
            List<Map<String, Object>> rawList = objectMapper.readValue(cleanJson, new TypeReference<>() {});

            for (Map<String, Object> map : rawList) {
                products.add(Product.builder()
                        .platform(platform)
                        .keyword(keyword)
                        .productName(String.valueOf(map.getOrDefault("productName", "")))
                        .price(parsePrice(map.getOrDefault("price", "0")))
                        .unitPrice(parsePrice(map.getOrDefault("unitPrice", "0")))
                        .seller(String.valueOf(map.getOrDefault("seller", "")))
                        .productUrl(String.valueOf(map.getOrDefault("productUrl", "")))
                        .crawledDate(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
                        .crawledAt(LocalDateTime.now())
                        .build());
            }
        } catch (IOException e) {
            log.error("Failed to parse JSON from Gemini response. Raw: {}, Repaired: {}", jsonResponse, cleanJson, e);
            // 복구 실패 시 빈 리스트 반환보다는 에러를 던지거나 부분 성공 처리
            // 여기서는 안전하게 빈 리스트 반환 (필요시 수정)
        }
        return products;
    }

    private Integer parsePrice(Object priceObj) {
        if (priceObj == null) return 0;
        try {
            if (priceObj instanceof Integer) {
                return (Integer) priceObj;
            }
            if (priceObj instanceof Number) {
                return ((Number) priceObj).intValue();
            }
            String priceStr = String.valueOf(priceObj);
            // "10,000원", "10,000" 등에서 숫자만 추출
            String cleanStr = priceStr.replaceAll("[^0-9]", "");
            if (cleanStr.isEmpty()) return 0;
            return Integer.parseInt(cleanStr);
        } catch (Exception e) {
            log.warn("Failed to parse price: {}", priceObj);
            return 0;
        }
    }
}
