package com.menswear.legacyimport;

import com.menswear.catalog.entity.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyImportServiceTest {

    private final LegacyOrderImportWorker worker = mock(LegacyOrderImportWorker.class);
    private final LegacyImportService service = new LegacyImportService(worker);

    private LegacyImportDtos.OrderRequest req(String ref) {
        var item = new LegacyImportDtos.ItemRequest("Grace", 5.0, 110000L, 550000L, 300000L);
        return new LegacyImportDtos.OrderRequest(ref, "Someone", "3350390242", "1", 1, "Turbat branch", "2026-04-26", 850000L, 850000L, "Delivered", List.of(item));
    }

    @Test
    void aggregatesCreatedMatchedAndSkippedAcrossABatch() {
        Product legacyProduct = Product.builder().id(1L).name("Legacy").slug("legacy").basePricePaisa(0L).currency("PKR").supportsCustom(true).active(false).build();
        when(worker.getOrCreateLegacyProduct()).thenReturn(legacyProduct);

        when(worker.importOne(any(), anyLong(), anyString()))
                .thenReturn(new LegacyOrderImportWorker.Result(LegacyOrderImportWorker.Outcome.CREATED, true, null))   // new customer
                .thenReturn(new LegacyOrderImportWorker.Result(LegacyOrderImportWorker.Outcome.CREATED, false, null))  // matched existing
                .thenReturn(new LegacyOrderImportWorker.Result(LegacyOrderImportWorker.Outcome.SKIPPED_DUPLICATE, false, null))
                .thenReturn(new LegacyOrderImportWorker.Result(LegacyOrderImportWorker.Outcome.CREATED, true, "phone warning"));

        var request = new LegacyImportDtos.ImportRequest(List.of(req("a"), req("b"), req("c"), req("d")));
        LegacyImportDtos.ImportResult result = service.importOrders(request);

        assertThat(result.ordersCreated()).isEqualTo(3);
        assertThat(result.ordersSkipped()).isEqualTo(1);
        assertThat(result.customersCreated()).isEqualTo(2);
        assertThat(result.customersMatched()).isEqualTo(1);
        assertThat(result.warnings()).containsExactly("phone warning");
    }

    @Test
    void oneFailingOrderDoesNotAbortTheRestOfTheBatch() {
        Product legacyProduct = Product.builder().id(1L).name("Legacy").slug("legacy").basePricePaisa(0L).currency("PKR").supportsCustom(true).active(false).build();
        when(worker.getOrCreateLegacyProduct()).thenReturn(legacyProduct);

        when(worker.importOne(any(), anyLong(), anyString()))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(new LegacyOrderImportWorker.Result(LegacyOrderImportWorker.Outcome.CREATED, true, null));

        var request = new LegacyImportDtos.ImportRequest(List.of(req("a"), req("b")));
        LegacyImportDtos.ImportResult result = service.importOrders(request);

        assertThat(result.ordersCreated()).isEqualTo(1);
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("failed").contains("boom");
    }
}
