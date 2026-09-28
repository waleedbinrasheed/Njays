package com.menswear.branch.repo;

import com.menswear.branch.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findAllByOrderByNameAsc();
    List<Branch> findByActiveTrueOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
