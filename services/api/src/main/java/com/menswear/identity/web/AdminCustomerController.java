package com.menswear.identity.web;

import com.menswear.identity.dto.AdminCustomerDtos;
import com.menswear.identity.service.AdminCustomerService;
import com.menswear.measurements.dto.MeasurementDtos;
import com.menswear.measurements.service.MeasurementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/customers")
public class AdminCustomerController {

    private final AdminCustomerService adminCustomerService;
    private final MeasurementService measurementService;

    public AdminCustomerController(AdminCustomerService adminCustomerService, MeasurementService measurementService) {
        this.adminCustomerService = adminCustomerService;
        this.measurementService = measurementService;
    }

    @GetMapping
    public List<AdminCustomerDtos.CustomerSummary> search(@RequestParam(required = false) String query) {
        return adminCustomerService.search(query);
    }

    @PostMapping
    public AdminCustomerDtos.CustomerSummary create(@Valid @RequestBody AdminCustomerDtos.CreateWalkInRequest request) {
        return adminCustomerService.createWalkIn(request);
    }

    @GetMapping("/{id}/measurements")
    public List<MeasurementDtos.Response> measurements(@PathVariable Long id) {
        return measurementService.listForUser(id);
    }

    @PostMapping("/{id}/measurements")
    public MeasurementDtos.Response createMeasurement(
            @PathVariable Long id,
            @Valid @RequestBody MeasurementDtos.UpsertRequest request
    ) {
        return measurementService.createForUser(id, request);
    }
}
