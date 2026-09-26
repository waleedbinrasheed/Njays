package com.menswear.insights;

import com.menswear.insights.dto.InsightsDtos.AskResponse;
import com.menswear.insights.dto.InsightsDtos.Highlight;
import com.menswear.insights.dto.InsightsDtos.Severity;
import com.menswear.insights.dto.InsightsDtos.Snapshot;
import com.menswear.insights.dto.InsightsDtos.StuckOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAssistantServiceTest {

    private final AdminInsightsService insightsService = mock(AdminInsightsService.class);
    private final AdminAssistantService assistantService = new AdminAssistantService(insightsService);

    private Snapshot snapshotWithStuckOrder() {
        return new Snapshot(
                "general summary",
                List.of(new Highlight(Severity.WARN, "ORDERS", "stuck title", "stuck detail")),
                5, 1, List.of(new StuckOrder("JH-2026-99", "IN_STITCHING", "3d 2h")),
                2, 700000L,
                1, 500000L,
                1, 0,
                1,
                300000L, 1, 500000L, 900000L,
                3, 2,
                Instant.now()
        );
    }

    @BeforeEach
    void setUp() {
        when(insightsService.snapshot()).thenReturn(snapshotWithStuckOrder());
    }

    @Test
    void blankQuestionReturnsGeneralSummary() {
        AskResponse res = assistantService.ask("  ");
        assertThat(res.answer()).isEqualTo("general summary");
    }

    @Test
    void stuckKeywordListsStuckOrders() {
        AskResponse res = assistantService.ask("Are any orders stuck or delayed?");
        assertThat(res.answer()).contains("JH-2026-99").contains("IN_STITCHING");
    }

    @Test
    void revenueKeywordReturnsRevenueBreakdown() {
        AskResponse res = assistantService.ask("what's our revenue today?");
        assertThat(res.answer()).contains("PKR").contains("today");
    }

    @Test
    void pendingKeywordMentionsBankTransfers() {
        AskResponse res = assistantService.ask("any payments pending confirmation?");
        assertThat(res.answer()).contains("bank transfer");
    }

    @Test
    void unrecognizedQuestionFallsBackToSummary() {
        AskResponse res = assistantService.ask("tell me a joke");
        assertThat(res.answer()).isEqualTo("general summary");
    }
}
