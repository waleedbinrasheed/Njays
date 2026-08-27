package com.menswear.payments.repo;

import com.menswear.common.enums.PaymentMethod;
import com.menswear.common.enums.PaymentStatus;
import com.menswear.payments.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<Payment> findByProviderRef(String providerRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.providerRef = :providerRef")
    Optional<Payment> findByProviderRefForUpdate(@Param("providerRef") String providerRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    List<Payment> findByStatusInOrderByCreatedAtDesc(List<PaymentStatus> statuses);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    Optional<Payment> findFirstByOrderIdAndMethodAndStatusInOrderByCreatedAtDesc(
            Long orderId,
            PaymentMethod method,
            Collection<PaymentStatus> statuses
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment p set p.status = com.menswear.common.enums.PaymentStatus.CANCELLED, "
            + "p.failureReason = :reason where p.orderId = :orderId and p.method = :method "
            + "and p.status in :statuses and p.id <> :keepId")
    int cancelOpenPayments(
            @Param("orderId") Long orderId,
            @Param("method") PaymentMethod method,
            @Param("statuses") Collection<PaymentStatus> statuses,
            @Param("keepId") Long keepId,
            @Param("reason") String reason
    );
}
