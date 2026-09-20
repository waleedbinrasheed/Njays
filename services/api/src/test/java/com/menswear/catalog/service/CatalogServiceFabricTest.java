package com.menswear.catalog.service;

import com.menswear.catalog.dto.CatalogDtos;
import com.menswear.catalog.entity.FabricTier;
import com.menswear.catalog.repo.CategoryRepository;
import com.menswear.catalog.repo.FabricColorRepository;
import com.menswear.catalog.repo.FabricTierRepository;
import com.menswear.catalog.repo.ProductRepository;
import com.menswear.common.exception.BadRequestException;
import com.menswear.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogServiceFabricTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final FabricTierRepository fabricTierRepository = mock(FabricTierRepository.class);
    private final FabricColorRepository fabricColorRepository = mock(FabricColorRepository.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);

    private final CatalogService service = new CatalogService(
            productRepository, categoryRepository, fabricTierRepository, fabricColorRepository, fileStorageService
    );

    @Test
    void createsFabricTierWithDefaults() {
        var request = new CatalogDtos.CreateFabricTierRequest("PREMIUM", "Premium Cotton", 50000L, null);
        when(fabricTierRepository.existsByCodeIgnoreCase("PREMIUM")).thenReturn(false);
        when(fabricTierRepository.save(any())).thenAnswer(inv -> {
            FabricTier t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });

        CatalogDtos.FabricTierResponse response = service.createFabricTier(request);

        assertThat(response.code()).isEqualTo("PREMIUM");
        assertThat(response.surchargePaisa()).isEqualTo(50000L);
    }

    @Test
    void rejectsDuplicateFabricTierCode() {
        var request = new CatalogDtos.CreateFabricTierRequest("PREMIUM", "Premium Cotton", 50000L, 1);
        when(fabricTierRepository.existsByCodeIgnoreCase("PREMIUM")).thenReturn(true);

        assertThatThrownBy(() -> service.createFabricTier(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
        verify(fabricTierRepository, never()).save(any());
    }

    @Test
    void createsFabricColorNormalizingHex() {
        FabricTier tier = FabricTier.builder().id(1L).code("PREMIUM").name("Premium").surchargePaisa(0L).sortOrder(0).build();
        when(fabricTierRepository.findById(1L)).thenReturn(Optional.of(tier));
        when(fabricColorRepository.existsByFabricTierIdAndCodeIgnoreCase(1L, "NV1")).thenReturn(false);
        when(fabricColorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new CatalogDtos.CreateFabricColorRequest("NV1", "Navy", "1a2b3c");
        CatalogDtos.FabricColorResponse response = service.createFabricColor(1L, request);

        assertThat(response.name()).isEqualTo("Navy");
        assertThat(response.hexColor()).isEqualTo("#1a2b3c");
    }

    @Test
    void rejectsFabricColorForMissingTier() {
        when(fabricTierRepository.findById(99L)).thenReturn(Optional.empty());
        var request = new CatalogDtos.CreateFabricColorRequest("NV1", "Navy", "#1a2b3c");

        assertThatThrownBy(() -> service.createFabricColor(99L, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsDuplicateColorCodeWithinSameTier() {
        FabricTier tier = FabricTier.builder().id(1L).code("PREMIUM").name("Premium").surchargePaisa(0L).sortOrder(0).build();
        when(fabricTierRepository.findById(1L)).thenReturn(Optional.of(tier));
        when(fabricColorRepository.existsByFabricTierIdAndCodeIgnoreCase(1L, "NV1")).thenReturn(true);

        var request = new CatalogDtos.CreateFabricColorRequest("NV1", "Navy", "#1a2b3c");

        assertThatThrownBy(() -> service.createFabricColor(1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already has a color");
        verify(fabricColorRepository, never()).save(any());
    }
}
