package com.menswear.branch.web;

import com.menswear.branch.dto.BranchDtos;
import com.menswear.branch.service.BranchService;
import com.menswear.branch.service.DispatchCostService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class BranchController {

    private final BranchService branchService;
    private final DispatchCostService dispatchCostService;

    public BranchController(BranchService branchService, DispatchCostService dispatchCostService) {
        this.branchService = branchService;
        this.dispatchCostService = dispatchCostService;
    }

    /** Public — active branches only, needed on order/registration forms before login. */
    @GetMapping("/branches")
    public List<BranchDtos.BranchResponse> listActive() {
        return branchService.listActive();
    }

    @GetMapping("/admin/branches")
    public List<BranchDtos.BranchResponse> listAll() {
        return branchService.listAll();
    }

    @PostMapping("/admin/branches")
    public BranchDtos.BranchResponse create(@Valid @RequestBody BranchDtos.CreateBranchRequest request) {
        return branchService.create(request);
    }

    @PutMapping("/admin/branches/{id}")
    public BranchDtos.BranchResponse update(@PathVariable Long id, @Valid @RequestBody BranchDtos.UpdateBranchRequest request) {
        return branchService.update(id, request);
    }

    @GetMapping("/admin/dispatch-costs")
    public BranchDtos.DispatchCostSettingsResponse dispatchCosts() {
        return dispatchCostService.listSettings();
    }

    /** Lets the order-creation form preview the cost before submitting. */
    @GetMapping("/admin/dispatch-costs/resolve")
    public java.util.Map<String, Long> resolveDispatchCost(
            @RequestParam Long sourceBranchId,
            @RequestParam Long destinationBranchId
    ) {
        return java.util.Map.of("costPaisa", dispatchCostService.resolve(sourceBranchId, destinationBranchId));
    }

    @PostMapping("/admin/dispatch-costs")
    public BranchDtos.DispatchCostRuleResponse upsertDispatchCost(@Valid @RequestBody BranchDtos.DispatchCostRuleRequest request) {
        return dispatchCostService.upsert(request);
    }
}
