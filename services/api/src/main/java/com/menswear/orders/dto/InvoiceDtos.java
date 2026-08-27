package com.menswear.orders.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.menswear.common.enums.OrderStatus;
import com.menswear.common.enums.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class InvoiceDtos {

    public record BusinessInfo(
            String name,
            String address,
            String phone,
            String email
    ) {}

    public record CustomerInfo(
            String fullName,
            String phone,
            String email,
            OrderDtos.AddressDto shippingAddress
    ) {}

    /** orderNumber and trackingId are deliberately the same value (see OrderInfo javadoc). */
    public record OrderInfo(
            String orderNumber,
            OrderType orderType,
            OrderStatus status,
            Instant orderDate
    ) {}

    /**
     * A snapshot of the measurement profile as it was at the time this item was ordered.
     * measurementJson was serialized from the full MeasurementProfile entity (more fields
     * than this summary needs), so unknown properties must be tolerated on deserialization.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MeasurementSnapshot(
            String name,
            String unit,
            BigDecimal kameezLength,
            BigDecimal chest,
            BigDecimal waist,
            BigDecimal hip,
            BigDecimal shoulder,
            BigDecimal sleeveLength,
            BigDecimal collarLength,
            BigDecimal shalwarLength,
            BigDecimal shalwarBottom,
            String backStyle,
            String sleeveStyle,
            String buttonStyle,
            String collarStyle,
            String cuffStyle,
            String notes
    ) {}

    public record InvoiceItem(
            String productName,
            boolean custom,
            String fabricLabel,
            int quantity,
            Long unitPricePaisa,
            Long lineTotalPaisa,
            MeasurementSnapshot measurements
    ) {}

    public record Totals(
            String currency,
            long subtotalPaisa,
            long shippingPaisa,
            long totalPaisa,
            long amountPaidPaisa,
            long balanceDuePaisa
    ) {}

    public record InvoiceResponse(
            BusinessInfo business,
            CustomerInfo customer,
            OrderInfo order,
            List<InvoiceItem> items,
            Totals totals
    ) {}
}
