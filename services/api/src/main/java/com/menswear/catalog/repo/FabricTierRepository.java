package com.menswear.catalog.repo;

import com.menswear.catalog.entity.FabricTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FabricTierRepository extends JpaRepository<FabricTier, Long> {
    List<FabricTier> findAllByOrderBySortOrderAsc();
}
