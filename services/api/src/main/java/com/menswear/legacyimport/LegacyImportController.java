package com.menswear.legacyimport;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/import")
public class LegacyImportController {

    private final LegacyImportService legacyImportService;

    public LegacyImportController(LegacyImportService legacyImportService) {
        this.legacyImportService = legacyImportService;
    }

    @PostMapping("/legacy-orders")
    public LegacyImportDtos.ImportResult importLegacyOrders(@Valid @RequestBody LegacyImportDtos.ImportRequest request) {
        return legacyImportService.importOrders(request);
    }
}
