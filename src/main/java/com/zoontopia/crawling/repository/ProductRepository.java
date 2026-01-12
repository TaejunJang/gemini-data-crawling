package com.zoontopia.crawling.repository;

import com.zoontopia.crawling.domain.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findByKeyword(String keyword);
}
