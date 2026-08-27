package com.menswear.insights;

import com.menswear.insights.dto.InsightsDtos.AskResponse;
import com.menswear.insights.dto.InsightsDtos.Highlight;
import com.menswear.insights.dto.InsightsDtos.Snapshot;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Keyword-driven intent matching over {@link AdminInsightsService}'s live snapshot.
 * Deliberately not backed by an external LLM: answers are deterministic, free, and
 * traceable straight back to the order/payment tables an admin can already see.
 */
@Service
public class AdminAssistantService {

    private final AdminInsightsService insightsService;

    public AdminAssistantService(AdminInsightsService insightsService) {
        this.insightsService = insightsService;
    }

    public AskResponse ask(String rawQuestion) {
        Snapshot snap = insightsService.snapshot();
        String q = rawQuestion == null ? "" : rawQuestion.toLowerCase(Locale.ROOT);

        if (q.isBlank()) {
            return new AskResponse(snap.summary(), snap.highlights());
        }
        if (containsAny(q, "stuck", "delay", "late", "overdue", "bottleneck", "stalled", "too long")) {
            return stuckAnswer(snap);
        }
        if (containsAny(q, "bank")) {
            return bankAnswer(snap);
        }
        if (containsAny(q, "jazzcash", "jazz cash")) {
            return jazzCashAnswer(snap);
        }
        if (containsAny(q, "cod", "cash on delivery")) {
            return codAnswer(snap);
        }
        if (containsAny(q, "pending", "unpaid", "awaiting", "confirm")) {
            return pendingAnswer(snap);
        }
        if (containsAny(q, "revenue", "sales", "earn", "income", "money")) {
            return revenueAnswer(snap);
        }
        if (containsAny(q, "custom", "measure", "ready")) {
            return mixAnswer(snap);
        }
        return new AskResponse(snap.summary(), snap.highlights());
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }

    private AskResponse stuckAnswer(Snapshot snap) {
        if (snap.stuckOrders().isEmpty()) {
            return new AskResponse(
                    "Nothing's stuck right now - every active order has moved stage within its expected window.",
                    filter(snap.highlights(), "ORDERS"));
        }
        StringBuilder sb = new StringBuilder()
                .append(snap.ordersStuckCount())
                .append(" order(s) have been sitting in their current stage longer than expected:\n");
        snap.stuckOrders().forEach(s -> sb.append("- ").append(s.publicCode()).append(": ").append(s.status())
                .append(" for ").append(s.age()).append("\n"));
        sb.append("Consider nudging these forward or checking with the tailoring team.");
        return new AskResponse(sb.toString().trim(), filter(snap.highlights(), "ORDERS"));
    }

    private AskResponse pendingAnswer(Snapshot snap) {
        String answer = snap.paymentsPendingCount() == 0
                ? "No payments are currently pending or awaiting confirmation."
                : snap.paymentsPendingCount() + " payment(s) are open, worth " + AdminInsightsService.pkr(snap.pendingAmountPaisa())
                        + " in total. " + snap.bankAwaitingConfirmationCount() + " of those are bank transfers"
                        + (snap.bankAwaitingConfirmationCount() > 0
                                ? " worth " + AdminInsightsService.pkr(snap.bankAwaitingConfirmationPaisa())
                                : "")
                        + " - check Pending payments to confirm the ones with proof uploaded.";
        return new AskResponse(answer, filter(snap.highlights(), "PAYMENTS"));
    }

    private AskResponse bankAnswer(Snapshot snap) {
        String answer = snap.bankAwaitingConfirmationCount() == 0
                ? "No bank transfer payments are waiting on you right now."
                : snap.bankAwaitingConfirmationCount() + " bank transfer payment(s) are awaiting confirmation, worth "
                        + AdminInsightsService.pkr(snap.bankAwaitingConfirmationPaisa())
                        + ". Check the proof screenshot in Pending payments before confirming.";
        return new AskResponse(answer, filter(snap.highlights(), "PAYMENTS"));
    }

    private AskResponse jazzCashAnswer(Snapshot snap) {
        String answer = "There " + (snap.jazzCashOpenCount() == 1 ? "is" : "are") + " " + snap.jazzCashOpenCount()
                + " open JazzCash payment(s)"
                + (snap.jazzCashExpiringSoonCount() > 0
                        ? ", and " + snap.jazzCashExpiringSoonCount()
                                + " will expire within 15 minutes if the customer doesn't finish paying."
                        : ".");
        return new AskResponse(answer, filter(snap.highlights(), "JAZZCASH"));
    }

    private AskResponse codAnswer(Snapshot snap) {
        String answer = snap.codPendingCount() + " cash-on-delivery order(s) are awaiting collection at delivery. "
                + "Confirm them from Pending payments once the courier collects payment.";
        return new AskResponse(answer, filter(snap.highlights(), "PAYMENTS"));
    }

    private AskResponse revenueAnswer(Snapshot snap) {
        String answer = "Confirmed revenue today: " + AdminInsightsService.pkr(snap.revenueTodayPaisa())
                + " (" + snap.revenueTodayCount() + " payment(s)), last 7 days: "
                + AdminInsightsService.pkr(snap.revenueWeekPaisa()) + ", last 30 days: "
                + AdminInsightsService.pkr(snap.revenueMonthPaisa()) + ".";
        return new AskResponse(answer, filter(snap.highlights(), "REVENUE"));
    }

    private AskResponse mixAnswer(Snapshot snap) {
        String answer = "Of " + snap.ordersActive() + " active order(s), " + snap.customActiveCount()
                + " are made-to-measure and " + snap.readyActiveCount() + " are ready-made.";
        return new AskResponse(answer, filter(snap.highlights(), "ORDERS"));
    }

    private List<Highlight> filter(List<Highlight> highlights, String category) {
        return highlights.stream().filter(h -> h.category().equals(category)).toList();
    }
}
