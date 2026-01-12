package com.zoontopia.crawling.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@Document(collection = "products")
public class Product {
    @Id
    private String id;
    private String platform; // e.g., "NAVER"
    private String keyword;
    private String productName;
    private Integer price;
    private Integer unitPrice;
    private String seller;
    private String productUrl;
    private String crawledDate; // 'YYYY-MM-DD' 형식
    private LocalDateTime crawledAt;
}
