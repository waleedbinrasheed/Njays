package com.menswear.branch.service;

import com.menswear.branch.dto.BranchDtos;
import com.menswear.branch.entity.Branch;
import com.menswear.branch.entity.DispatchCostRule;
import com.menswear.branch.repo.BranchRepository;
import com.menswear.branch.repo.DispatchCostRuleRepository;
import com.menswear.common.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Resolves the dispatch cost for a created-branch -> dispatch-branch pair. The
 * resolved value is meant to be snapshotted onto the order at creation time
 * (see AdminOrderService) so later changes to these rules don't rewrite the
 * price on historical orders/receipts.
 */
@Service
public class DispatchCostService {

    private final DispatchCostRuleRepository ruleRepository;
    private final BranchRepository branchRepository;
    private final long defaultCostPaisa;

    public DispatchCostService(
            DispatchCostRuleRepository ruleRepository,
            BranchRepository branchRepository,
            @Value("${menswear.dispatch.default-cost-paisa:0}") long defaultCostPaisa
    ) {
        this.ruleRepository = ruleRepository;
        this.branchRepository = branchRepository;
        this.defaultCostPaisa = defaultCostPaisa;
    }

    @Transactional(readOnly = true)
    public long resolve(Long sourceBranchId, Long destinationBranchId) {
        if (sourceBranchId == null || destinationBranchId == null || sourceBranchId.equals(destinationBranchId)) {
            return 0L;
        }
        return ruleRepository.findBySourceBranchIdAndDestinationBranchId(sourceBranchId, destinationBranchId)
                .map(DispatchCostRule::getCostPaisa)
                .orElse(defaultCostPaisa);
    }

    @Transactional(readOnly = true)
    public BranchDtos.DispatchCostSettingsResponse listSettings() {
        Map<Long, String> branchNames = branchRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Branch::getId, Branch::getName));
        List<BranchDtos.DispatchCostRuleResponse> rules = ruleRepository.findAllByOrderBySourceBranchIdAsc().stream()
                .map(r -> new BranchDtos.DispatchCostRuleResponse(
                        r.getId(),
                        r.getSourceBranchId(),
                        branchNames.getOrDefault(r.getSourceBranchId(), "—"),
                        r.getDestinationBranchId(),
                        branchNames.getOrDefault(r.getDestinationBranchId(), "—"),
                        r.getCostPaisa()
                ))
                .toList();
        return new BranchDtos.DispatchCostSettingsResponse(defaultCostPaisa, rules);
    }

    @Transactional
    public BranchDtos.DispatchCostRuleResponse upsert(BranchDtos.DispatchCostRuleRequest request) {
        Branch source = branchRepository.findById(request.sourceBranchId())
                .orElseThrow(() -> new NotFoundException("Source branch not found"));
        Branch destination = branchRepository.findById(request.destinationBranchId())
                .orElseThrow(() -> new NotFoundException("Destination branch not found"));

        DispatchCostRule rule = ruleRepository
                .findBySourceBranchIdAndDestinationBranchId(source.getId(), destination.getId())
                .orElseGet(() -> DispatchCostRule.builder()
                        .sourceBranchId(source.getId())
                        .destinationBranchId(destination.getId())
                        .build());
        rule.setCostPaisa(request.costPaisa());
        DispatchCostRule saved = ruleRepository.save(rule);

        return new BranchDtos.DispatchCostRuleResponse(
                saved.getId(), source.getId(), source.getName(), destination.getId(), destination.getName(), saved.getCostPaisa()
        );
    }
}
