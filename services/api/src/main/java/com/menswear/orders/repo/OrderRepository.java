package com.menswear.orders.repo;

import com.menswear.orders.entity.ShopOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<ShopOrder, Long> {
    List<ShopOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<ShopOrder> findByPublicCode(String publicCode);
    Optional<ShopOrder> findByPublicCodeAndWhatsappPhone(String publicCode, String whatsappPhone);
    Optional<ShopOrder> findByIdAndUserId(Long id, Long userId);
    boolean existsByLegacyRef(String legacyRef);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ShopOrder o set o.createdAt = :date, o.updatedAt = :date where o.id = :id")
    void backdate(@Param("id") Long id, @Param("date") Instant date);
}
