package com.menswear.branch.repo;

import com.menswear.branch.entity.DispatchCostRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DispatchCostRuleRepository extends JpaRepository<DispatchCostRule, Long> {
    Optional<DispatchCostRule> findBySourceBranchIdAndDestinationBranchId(Long sourceBranchId, Long destinationBranchId);
    List<DispatchCostRule> findAllByOrderBySourceBranchIdAsc();
}
