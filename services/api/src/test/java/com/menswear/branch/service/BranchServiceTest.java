package com.menswear.branch.service;

import com.menswear.branch.dto.BranchDtos;
import com.menswear.branch.entity.Branch;
import com.menswear.branch.repo.BranchRepository;
import com.menswear.common.exception.BadRequestException;
import com.menswear.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    @Mock
    private BranchRepository branchRepository;

    @InjectMocks
    private BranchService branchService;

    private Branch existing;

    @BeforeEach
    void setUp() {
        existing = Branch.builder().id(1L).name("Downtown").address("123 Main St").phone("555-0100").active(true).build();
    }

    @Test
    void list_returnsBranchesOrderedByName() {
        when(branchRepository.findAllByOrderByNameAsc()).thenReturn(List.of(existing));

        List<BranchDtos.BranchResponse> result = branchService.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Downtown");
    }

    @Test
    void create_savesNewBranchAsActive() {
        var request = new BranchDtos.CreateBranchRequest("Uptown", "  456 Oak Ave  ", " ");
        when(branchRepository.existsByNameIgnoreCase("Uptown")).thenReturn(false);
        when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> {
            Branch b = inv.getArgument(0);
            b.setId(2L);
            return b;
        });

        BranchDtos.BranchResponse response = branchService.create(request);

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.active()).isTrue();
        assertThat(response.address()).isEqualTo("456 Oak Ave");
        assertThat(response.phone()).isNull();
    }

    @Test
    void create_rejectsDuplicateName() {
        var request = new BranchDtos.CreateBranchRequest("Downtown", null, null);
        when(branchRepository.existsByNameIgnoreCase("Downtown")).thenReturn(true);

        assertThatThrownBy(() -> branchService.create(request))
                .isInstanceOf(BadRequestException.class);

        verify(branchRepository, never()).save(any());
    }

    @Test
    void update_appliesChangesWhenNameUnchanged() {
        var request = new BranchDtos.UpdateBranchRequest("Downtown", "New Address", "555-9999", false);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

        BranchDtos.BranchResponse response = branchService.update(1L, request);

        assertThat(response.address()).isEqualTo("New Address");
        assertThat(response.active()).isFalse();
        verify(branchRepository, never()).existsByNameIgnoreCase(any());
    }

    @Test
    void update_rejectsRenameToExistingName() {
        var request = new BranchDtos.UpdateBranchRequest("Uptown", null, null, true);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(branchRepository.existsByNameIgnoreCase("Uptown")).thenReturn(true);

        assertThatThrownBy(() -> branchService.update(1L, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_throwsWhenBranchMissing() {
        var request = new BranchDtos.UpdateBranchRequest("Anything", null, null, true);
        when(branchRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> branchService.update(99L, request))
                .isInstanceOf(NotFoundException.class);
    }
}
