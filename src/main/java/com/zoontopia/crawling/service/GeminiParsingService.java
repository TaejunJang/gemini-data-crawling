package com.zoontopia.crawling.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zoontopia.crawling.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
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
                - price: 상품 가격 (예: "10,000원")
                - seller: 판매자 또는 쇼핑몰 이름
                - productUrl: 상품 상세 페이지 링크

                결과는 반드시 순수한 JSON 객체 배열 형태로만 반환해야 합니다.
                마크다운 코드 블록(예: ```json ... ```)으로 감싸지 마세요.
                어떠한 설명도 추가하지 말고 오직 JSON 배열만 반환하세요.

                HTML 콘텐츠:
                %s
                """;

        // Truncate HTML if it's too long to avoid token limits (Basic safeguard)
        // In a real scenario, we might want to be smarter about this (e.g. only sending specific divs)
        String safeHtml = htmlContent.length() > 100000 ? htmlContent.substring(0, 100000) : htmlContent;

        String response = chatClientBuilder.build()
                .prompt()
                .user(String.format(prompt, safeHtml))
                .call()
                .content();

        return convertJsonToProducts(response, keyword, platform);
    }

    private List<Product> convertJsonToProducts(String jsonResponse, String keyword, String platform) {
        List<Product> products = new ArrayList<>();
        try {
            // Clean up if Gemini adds markdown despite instructions
            String cleanJson = jsonResponse.replace("```json", "").replace("```", "").trim();
            
            List<Map<String, String>> rawList = objectMapper.readValue(cleanJson, new TypeReference<>() {});

            for (Map<String, String> map : rawList) {
                products.add(Product.builder()
                        .platform(platform)
                        .keyword(keyword)
                        .productName(map.getOrDefault("productName", ""))
                        .price(map.getOrDefault("price", ""))
                        .seller(map.getOrDefault("seller", ""))
                        .productUrl(map.getOrDefault("productUrl", ""))
                        .crawledAt(LocalDateTime.now())
                        .build());
            }
        } catch (IOException e) {
            log.error("Failed to parse JSON from Gemini response: {}", jsonResponse, e);
            throw new RuntimeException("JSON parsing error", e);
        }
        return products;
    }
}
