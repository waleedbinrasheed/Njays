package com.menswear.branch.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dispatch_cost_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DispatchCostRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_branch_id", nullable = false)
    private Long sourceBranchId;

    @Column(name = "destination_branch_id", nullable = false)
    private Long destinationBranchId;

    @Column(name = "cost_paisa", nullable = false)
    private Long costPaisa;
}
