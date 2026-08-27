package com.menswear.catalog.repo;

import com.menswear.catalog.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySlugAndActiveTrue(String slug);

    boolean existsBySlugIgnoreCase(String slug);

    List<Product> findByActiveTrueOrderByCreatedAtDesc();

    List<Product> findByActiveTrueAndCategoryIdOrderByCreatedAtDesc(Long categoryId);

    @Query("""
        SELECT p FROM Product p
        WHERE p.active = true
          AND LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY p.createdAt DESC
        """)
    List<Product> searchByName(@Param("q") String q);

    @Query("""
        SELECT p FROM Product p
        WHERE p.active = true
          AND p.categoryId = :categoryId
          AND LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY p.createdAt DESC
        """)
    List<Product> searchByCategoryAndName(@Param("categoryId") Long categoryId, @Param("q") String q);
}
