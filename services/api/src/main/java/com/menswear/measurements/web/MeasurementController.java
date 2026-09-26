package com.menswear.measurements.web;

import com.menswear.measurements.dto.MeasurementDtos;
import com.menswear.measurements.service.MeasurementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me/measurements")
public class MeasurementController {

    private final MeasurementService measurementService;

    public MeasurementController(MeasurementService measurementService) {
        this.measurementService = measurementService;
    }

    @GetMapping
    public List<MeasurementDtos.Response> list() {
        return measurementService.listMine();
    }

    @PostMapping
    public MeasurementDtos.Response create(@Valid @RequestBody MeasurementDtos.UpsertRequest request) {
        return measurementService.create(request);
    }

    @PutMapping("/{id}")
    public MeasurementDtos.Response update(
            @PathVariable Long id,
            @Valid @RequestBody MeasurementDtos.UpsertRequest request
    ) {
        return measurementService.update(id, request);
    }
}
