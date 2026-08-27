package com.menswear.measurements.repo;

import com.menswear.measurements.entity.MeasurementProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeasurementProfileRepository extends JpaRepository<MeasurementProfile, Long> {
    List<MeasurementProfile> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<MeasurementProfile> findByIdAndUserId(Long id, Long userId);
}
