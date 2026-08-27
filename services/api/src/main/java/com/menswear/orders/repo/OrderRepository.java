package com.menswear.orders.repo;

import com.menswear.orders.entity.ShopOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<ShopOrder, Long> {
    List<ShopOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<ShopOrder> findByPublicCode(String publicCode);
    Optional<ShopOrder> findByPublicCodeAndWhatsappPhone(String publicCode, String whatsappPhone);
    Optional<ShopOrder> findByIdAndUserId(Long id, Long userId);
}
