package com.menswear.branch.service;

import com.menswear.branch.dto.BranchDtos;
import com.menswear.branch.entity.Branch;
import com.menswear.branch.repo.BranchRepository;
import com.menswear.common.exception.BadRequestException;
import com.menswear.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BranchService {

    private final BranchRepository branchRepository;

    public BranchService(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    @Transactional(readOnly = true)
    public List<BranchDtos.BranchResponse> list() {
        return branchRepository.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public BranchDtos.BranchResponse create(BranchDtos.CreateBranchRequest request) {
        String name = request.name().trim();
        if (branchRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("A branch named '" + name + "' already exists");
        }
        Branch branch = Branch.builder()
                .name(name)
                .address(blankToNull(request.address()))
                .phone(blankToNull(request.phone()))
                .active(true)
                .build();
        return toDto(branchRepository.save(branch));
    }

    @Transactional
    public BranchDtos.BranchResponse update(Long id, BranchDtos.UpdateBranchRequest request) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Branch not found"));
        String name = request.name().trim();
        if (!name.equalsIgnoreCase(branch.getName()) && branchRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("A branch named '" + name + "' already exists");
        }
        branch.setName(name);
        branch.setAddress(blankToNull(request.address()));
        branch.setPhone(blankToNull(request.phone()));
        branch.setActive(request.active());
        return toDto(branchRepository.save(branch));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BranchDtos.BranchResponse toDto(Branch b) {
        return new BranchDtos.BranchResponse(b.getId(), b.getName(), b.getAddress(), b.getPhone(), b.isActive());
    }
}
