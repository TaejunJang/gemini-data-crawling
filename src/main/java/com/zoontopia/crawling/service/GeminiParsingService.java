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
                쇼핑 검색 결과 페이지의 HTML 콘텐츠를 제공해 드릴 것입니다.
                당신의 임무는 이 HTML에서 상품 정보를 추출하는 것입니다.

                목록에 있는 각 상품에 대해 다음 필드를 추출해 주세요:
                - productName: 상품명 또는 제목
                - price: 상품 가격 (예: "10,000원") 숫자형으로 변환
                - unitPrice: 단가 (예: "100g당 1,000원" 또는 "1개당 500원 또는 kg당 1000원"), HTML에서 단가 정보를 찾을 수 있는 경우에만 추출하세요 숫자형으로 변환.
                - seller: 판매자 또는 쇼핑몰 이름
                - productUrl: 상품 상세 페이지 링크

                ### 제약 사항 (중요):
                - 출력은 반드시 JSON 배열 `[...]`로 시작하고 끝나야 합니다.
                - **마크다운 코드 블록(```json)을 절대 사용하지 마세요.**
                - 어떠한 서술형 설명이나 인사말도 포함하지 마세요.
                - **토큰 제한으로 인해 응답이 끊길 것 같으면, 마지막 상품 객체를 완전히 닫고(}) 배열을 닫아서(]) 유효한 JSON 형식을 유지하며 종료하세요.**
                - HTML 내에 상품이 너무 많다면 상위 50개까지만 추출하세요. (응답 끊김 방지용)
                
                HTML 콘텐츠:
                %s
                """;

        // Truncate HTML if it's too long to avoid token limits (Basic safeguard)
        // In a real scenario, we might want to be smarter about this (e.g. only sending specific divs)
        //String safeHtml = htmlContent.length() > 100000 ? htmlContent.substring(0, 100000) : htmlContent;

        String response = chatClientBuilder.build()
                .prompt()
                .user(String.format(prompt, htmlContent))
                .call()
                .content();

        return convertJsonToProducts(response, keyword, platform);
    }

    private List<Product> convertJsonToProducts(String jsonResponse, String keyword, String platform) {
        List<Product> products = new ArrayList<>();
        try {
            // Clean up if Gemini adds markdown despite instructions
            String cleanJson = jsonResponse.replace("```json", "").replace("```", "").trim();
            
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
            log.error("Failed to parse JSON from Gemini response: {}", jsonResponse, e);
            throw new RuntimeException("JSON parsing error", e);
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
