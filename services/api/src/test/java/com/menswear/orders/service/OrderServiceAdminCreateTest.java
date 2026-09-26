package com.menswear.orders.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menswear.cart.service.CartService;
import com.menswear.catalog.entity.FabricColor;
import com.menswear.catalog.entity.FabricTier;
import com.menswear.catalog.entity.Product;
import com.menswear.catalog.repo.FabricColorRepository;
import com.menswear.catalog.repo.ProductRepository;
import com.menswear.common.enums.OrderStatus;
import com.menswear.common.enums.OrderType;
import com.menswear.common.exception.BadRequestException;
import com.menswear.common.exception.NotFoundException;
import com.menswear.measurements.entity.MeasurementProfile;
import com.menswear.measurements.repo.MeasurementProfileRepository;
import com.menswear.orders.dto.AdminOrderDtos;
import com.menswear.orders.dto.OrderDtos;
import com.menswear.orders.entity.ShopOrder;
import com.menswear.orders.repo.OrderRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceAdminCreateTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final CartService cartService = mock(CartService.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final FabricColorRepository fabricColorRepository = mock(FabricColorRepository.class);
    private final MeasurementProfileRepository measurementProfileRepository = mock(MeasurementProfileRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OrderService service = new OrderService(
            orderRepository, cartService, productRepository, fabricColorRepository,
            measurementProfileRepository, objectMapper
    );

    private static final Long CUSTOMER_ID = 9L;
    private static final Long ADMIN_ID = 1L;

    private OrderDtos.AddressDto address() {
        return new OrderDtos.AddressDto("Shop counter", null, "Karachi", "Sindh", "75500", "PK");
    }

    private Product readyMadeProduct() {
        return Product.builder().id(1L).name("Classic Waistcoat").slug("waistcoat")
                .basePricePaisa(150000L).currency("PKR").supportsCustom(false).active(true).build();
    }

    private Product customCapableProduct() {
        return Product.builder().id(2L).name("Kameez Shalwar").slug("kameez")
                .basePricePaisa(850000L).currency("PKR").supportsCustom(true).active(true).build();
    }

    @Test
    void createsReadyMadeOrderWithoutRequiringMeasurements() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(readyMadeProduct()));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            ShopOrder o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });

        var item = new AdminOrderDtos.CreateOrderItemRequest(1L, 2, false, null, null);
        var request = new AdminOrderDtos.CreateOrderRequest(CUSTOMER_ID, address(), "03001234567", null, List.of(item));

        OrderDtos.OrderResponse result = service.createForCustomer(CUSTOMER_ID, request, ADMIN_ID);

        assertThat(result.orderType()).isEqualTo(OrderType.READY);
        assertThat(result.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(result.totalPaisa()).isEqualTo(300000L); // 150,000 x 2
        assertThat(result.whatsappPhone()).isEqualTo("923001234567");
    }

    @Test
    void customItemAddsSurchargeAndCapturesMeasurementSnapshot() {
        FabricTier tier = FabricTier.builder().id(1L).code("PREMIUM").name("Premium").surchargePaisa(50000L).sortOrder(0).build();
        FabricColor color = FabricColor.builder().id(1L).fabricTier(tier).code("NV1").name("Navy").hexColor("#1a2b3c").build();
        MeasurementProfile profile = MeasurementProfile.builder().id(5L).userId(CUSTOMER_ID).name("Fit").unit("INCH").build();

        when(productRepository.findById(2L)).thenReturn(Optional.of(customCapableProduct()));
        when(fabricColorRepository.findById(1L)).thenReturn(Optional.of(color));
        when(measurementProfileRepository.findByIdAndUserId(5L, CUSTOMER_ID)).thenReturn(Optional.of(profile));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            ShopOrder o = inv.getArgument(0);
            o.setId(101L);
            return o;
        });

        var item = new AdminOrderDtos.CreateOrderItemRequest(2L, 1, true, 1L, 5L);
        var request = new AdminOrderDtos.CreateOrderRequest(CUSTOMER_ID, address(), "03001234567", null, List.of(item));

        OrderDtos.OrderResponse result = service.createForCustomer(CUSTOMER_ID, request, ADMIN_ID);

        assertThat(result.orderType()).isEqualTo(OrderType.CUSTOM);
        assertThat(result.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(result.totalPaisa()).isEqualTo(900000L); // 850,000 + 50,000 surcharge
        assertThat(result.items().get(0).fabricLabel()).contains("Navy");
    }

    @Test
    void rejectsMeasurementProfileBelongingToAnotherCustomer() {
        FabricColor color = FabricColor.builder().id(1L)
                .fabricTier(FabricTier.builder().id(1L).code("P").name("P").surchargePaisa(0L).sortOrder(0).build())
                .code("NV1").name("Navy").build();
        when(productRepository.findById(2L)).thenReturn(Optional.of(customCapableProduct()));
        when(fabricColorRepository.findById(1L)).thenReturn(Optional.of(color));
        when(measurementProfileRepository.findByIdAndUserId(5L, CUSTOMER_ID)).thenReturn(Optional.empty());

        var item = new AdminOrderDtos.CreateOrderItemRequest(2L, 1, true, 1L, 5L);
        var request = new AdminOrderDtos.CreateOrderRequest(CUSTOMER_ID, address(), "03001234567", null, List.of(item));

        assertThatThrownBy(() -> service.createForCustomer(CUSTOMER_ID, request, ADMIN_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsCustomItemOnProductThatDoesNotSupportIt() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(readyMadeProduct()));

        var item = new AdminOrderDtos.CreateOrderItemRequest(1L, 1, true, 1L, 5L);
        var request = new AdminOrderDtos.CreateOrderRequest(CUSTOMER_ID, address(), "03001234567", null, List.of(item));

        assertThatThrownBy(() -> service.createForCustomer(CUSTOMER_ID, request, ADMIN_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not support custom");
    }

    @Test
    void rejectsCustomItemMissingFabricOrMeasurement() {
        when(productRepository.findById(2L)).thenReturn(Optional.of(customCapableProduct()));

        var item = new AdminOrderDtos.CreateOrderItemRequest(2L, 1, true, null, null);
        var request = new AdminOrderDtos.CreateOrderRequest(CUSTOMER_ID, address(), "03001234567", null, List.of(item));

        assertThatThrownBy(() -> service.createForCustomer(CUSTOMER_ID, request, ADMIN_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("require a fabric color and a measurement profile");
    }

    @Test
    void rejectsInactiveProduct() {
        Product inactive = Product.builder().id(3L).name("Discontinued").slug("old")
                .basePricePaisa(100000L).currency("PKR").supportsCustom(false).active(false).build();
        when(productRepository.findById(3L)).thenReturn(Optional.of(inactive));

        var item = new AdminOrderDtos.CreateOrderItemRequest(3L, 1, false, null, null);
        var request = new AdminOrderDtos.CreateOrderRequest(CUSTOMER_ID, address(), "03001234567", null, List.of(item));

        assertThatThrownBy(() -> service.createForCustomer(CUSTOMER_ID, request, ADMIN_ID))
                .isInstanceOf(NotFoundException.class);
    }
}
