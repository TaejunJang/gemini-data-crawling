package com.zoontopia.crawling.service;

import com.zoontopia.crawling.domain.Product;
import com.zoontopia.crawling.repository.ProductRepository;
import com.zoontopia.crawling.service.crawler.ShoppingCrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlingService {

    private final List<ShoppingCrawler> crawlers;
    private final ProductRepository productRepository;

    public List<Product> crawlAndSave(String platform, String keyword) {
        ShoppingCrawler crawler = crawlers.stream()
                .filter(c -> c.getPlatform().equalsIgnoreCase(platform))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported platform: " + platform));

        List<Product> products = crawler.searchProducts(keyword);
        
        if (!products.isEmpty()) {
            productRepository.saveAll(products);
            log.info("Saved {} products for keyword '{}' from {}", products.size(), keyword, platform);
        }
        
        return products;
    }
}
