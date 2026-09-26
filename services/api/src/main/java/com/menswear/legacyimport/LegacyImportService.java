package com.menswear.legacyimport;

import com.menswear.catalog.entity.Product;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the legacy ledger backfill: resolves (or creates) the shared
 * placeholder product once, then delegates each order to
 * LegacyOrderImportWorker in its own transaction so one bad row can't affect
 * the rest of the batch. See LegacyOrderImportWorker for why that separation
 * matters and for the actual import logic.
 */
@Service
public class LegacyImportService {

    private final LegacyOrderImportWorker worker;

    public LegacyImportService(LegacyOrderImportWorker worker) {
        this.worker = worker;
    }

    public LegacyImportDtos.ImportResult importOrders(LegacyImportDtos.ImportRequest request) {
        Product legacyProduct = worker.getOrCreateLegacyProduct();

        int customersCreated = 0;
        int ordersCreated = 0;
        int ordersSkipped = 0;
        List<String> warnings = new ArrayList<>();

        for (LegacyImportDtos.OrderRequest req : request.orders()) {
            try {
                LegacyOrderImportWorker.Result result = worker.importOne(req, legacyProduct.getId(), legacyProduct.getName());
                if (result.outcome() == LegacyOrderImportWorker.Outcome.SKIPPED_DUPLICATE) {
                    ordersSkipped++;
                    continue;
                }
                ordersCreated++;
                if (result.customerCreated()) {
                    customersCreated++;
                }
                if (result.warning() != null) {
                    warnings.add(result.warning());
                }
            } catch (Exception e) {
                warnings.add("Order " + req.legacyRef() + " (" + req.customerName() + ") failed: " + e.getMessage());
            }
        }

        int customersMatched = ordersCreated - customersCreated;
        return new LegacyImportDtos.ImportResult(customersCreated, customersMatched, ordersCreated, ordersSkipped, warnings);
    }
}
