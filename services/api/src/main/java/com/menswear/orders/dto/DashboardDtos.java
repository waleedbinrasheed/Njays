package com.menswear.orders.dto;

public class DashboardDtos {

    public record SummaryResponse(
            long todaysOrders,
            long ordersInProgress,
            long readyForDelivery,
            long ordersDueToday,
            long overdueOrders,
            long todaysPaymentsPaisa,
            long outstandingPayments,
            long awaitingDispatch
    ) {}
}
