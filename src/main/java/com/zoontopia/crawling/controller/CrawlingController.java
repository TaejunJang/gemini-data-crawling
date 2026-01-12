package com.zoontopia.crawling.controller;

import com.zoontopia.crawling.domain.Product;
import com.zoontopia.crawling.service.CrawlingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/crawl")
@RequiredArgsConstructor
public class CrawlingController {

    private final CrawlingService crawlingService;

    @PostMapping("/{platform}")
    public ResponseEntity<List<Product>> crawlProducts(
            @PathVariable String platform,
            @RequestParam String keyword) {
        
        List<Product> products = crawlingService.crawlAndSave(platform, keyword);
        return ResponseEntity.ok(products);
    }
}
