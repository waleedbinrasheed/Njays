package com.menswear.branch.web;

import com.menswear.branch.dto.BranchDtos;
import com.menswear.branch.service.BranchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    /** Public — needed on the registration form before the user has a session. */
    @GetMapping("/branches")
    public List<BranchDtos.BranchResponse> list() {
        return branchService.list();
    }

    @PostMapping("/admin/branches")
    public BranchDtos.BranchResponse create(@Valid @RequestBody BranchDtos.CreateBranchRequest request) {
        return branchService.create(request);
    }

    @PutMapping("/admin/branches/{id}")
    public BranchDtos.BranchResponse update(@PathVariable Long id, @Valid @RequestBody BranchDtos.UpdateBranchRequest request) {
        return branchService.update(id, request);
    }
}
