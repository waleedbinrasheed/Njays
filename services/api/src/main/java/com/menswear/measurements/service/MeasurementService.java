package com.menswear.measurements.service;

import com.menswear.common.exception.NotFoundException;
import com.menswear.identity.security.SecurityUtils;
import com.menswear.measurements.dto.MeasurementDtos;
import com.menswear.measurements.entity.MeasurementProfile;
import com.menswear.measurements.repo.MeasurementProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MeasurementService {

    private final MeasurementProfileRepository repository;

    public MeasurementService(MeasurementProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<MeasurementDtos.Response> listMine() {
        return repository.findByUserIdOrderByUpdatedAtDesc(SecurityUtils.currentUserId())
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public MeasurementDtos.Response create(MeasurementDtos.UpsertRequest request) {
        Long userId = SecurityUtils.currentUserId();
        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefaults(userId);
        }
        MeasurementProfile profile = apply(new MeasurementProfile(), request);
        profile.setUserId(userId);
        return toDto(repository.save(profile));
    }

    @Transactional
    public MeasurementDtos.Response update(Long id, MeasurementDtos.UpsertRequest request) {
        Long userId = SecurityUtils.currentUserId();
        MeasurementProfile profile = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Measurement profile not found"));
        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefaults(userId);
        }
        return toDto(repository.save(apply(profile, request)));
    }

    private void clearDefaults(Long userId) {
        repository.findByUserIdOrderByUpdatedAtDesc(userId).forEach(p -> {
            if (p.isDefault()) {
                p.setDefault(false);
            }
        });
    }

    private MeasurementProfile apply(MeasurementProfile profile, MeasurementDtos.UpsertRequest request) {
        profile.setName(request.name());
        profile.setUnit(request.unit() == null ? "INCH" : request.unit());
        profile.setKameezLength(request.kameezLength());
        profile.setChest(request.chest());
        profile.setWaist(request.waist());
        profile.setHip(request.hip());
        profile.setShoulder(request.shoulder());
        profile.setSleeveLength(request.sleeveLength());
        profile.setCollarLength(request.collarLength());
        profile.setShalwarLength(request.shalwarLength());
        profile.setShalwarBottom(request.shalwarBottom());
        profile.setBackStyle(normalize(request.backStyle()));
        profile.setSleeveStyle(normalize(request.sleeveStyle()));
        profile.setButtonStyle(normalize(request.buttonStyle()));
        profile.setCollarStyle(normalize(request.collarStyle()));
        profile.setCuffStyle(normalize(request.cuffStyle()));
        profile.setNotes(request.notes());
        profile.setDefault(Boolean.TRUE.equals(request.isDefault()));
        return profile;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private MeasurementDtos.Response toDto(MeasurementProfile p) {
        return new MeasurementDtos.Response(
                p.getId(),
                p.getName(),
                p.getUnit(),
                p.getKameezLength(),
                p.getChest(),
                p.getWaist(),
                p.getHip(),
                p.getShoulder(),
                p.getSleeveLength(),
                p.getCollarLength(),
                p.getShalwarLength(),
                p.getShalwarBottom(),
                p.getBackStyle(),
                p.getSleeveStyle(),
                p.getButtonStyle(),
                p.getCollarStyle(),
                p.getCuffStyle(),
                p.getNotes(),
                p.isDefault()
        );
    }
}
