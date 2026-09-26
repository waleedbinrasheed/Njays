package com.menswear.insights.dto;

import java.time.Instant;
import java.util.List;

public class InsightsDtos {

    public enum Severity { INFO, WARN, CRITICAL }

    public record Highlight(
            Severity severity,
            String category,
            String title,
            String detail
    ) {}

    public record StuckOrder(
            String publicCode,
            String status,
            String age
    ) {}

    public record Snapshot(
            String summary,
            List<Highlight> highlights,
            long ordersActive,
            long ordersStuckCount,
            List<StuckOrder> stuckOrders,
            long paymentsPendingCount,
            long pendingAmountPaisa,
            long bankAwaitingConfirmationCount,
            long bankAwaitingConfirmationPaisa,
            long jazzCashOpenCount,
            long jazzCashExpiringSoonCount,
            long codPendingCount,
            long revenueTodayPaisa,
            long revenueTodayCount,
            long revenueWeekPaisa,
            long revenueMonthPaisa,
            long customActiveCount,
            long readyActiveCount,
            Instant generatedAt
    ) {}

    public record AskRequest(String question) {}

    public record AskResponse(
            String answer,
            List<Highlight> highlights
    ) {}
}
