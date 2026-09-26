package com.menswear.cart.service;

import com.menswear.cart.dto.CartDtos;
import com.menswear.cart.entity.Cart;
import com.menswear.cart.entity.CartItem;
import com.menswear.cart.repo.CartRepository;
import com.menswear.catalog.entity.FabricColor;
import com.menswear.catalog.entity.Product;
import com.menswear.catalog.repo.FabricColorRepository;
import com.menswear.catalog.repo.ProductRepository;
import com.menswear.common.exception.BadRequestException;
import com.menswear.common.exception.NotFoundException;
import com.menswear.identity.security.SecurityUtils;
import com.menswear.measurements.repo.MeasurementProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final FabricColorRepository fabricColorRepository;
    private final MeasurementProfileRepository measurementProfileRepository;

    public CartService(
            CartRepository cartRepository,
            ProductRepository productRepository,
            FabricColorRepository fabricColorRepository,
            MeasurementProfileRepository measurementProfileRepository
    ) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.fabricColorRepository = fabricColorRepository;
        this.measurementProfileRepository = measurementProfileRepository;
    }

    @Transactional
    public CartDtos.CartResponse getOrCreate() {
        return toDto(getCartEntity());
    }

    @Transactional
    public CartDtos.CartResponse addItem(CartDtos.AddItemRequest request) {
        Cart cart = getCartEntity();
        Product product = productRepository.findById(request.productId())
                .filter(Product::isActive)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        long unitPrice = product.getBasePricePaisa();
        if (request.custom()) {
            if (!product.isSupportsCustom()) {
                throw new BadRequestException("Product does not support custom measure");
            }
            if (request.fabricColorId() == null || request.measurementProfileId() == null) {
                throw new BadRequestException("Custom items require fabricColorId and measurementProfileId");
            }
            FabricColor color = fabricColorRepository.findById(request.fabricColorId())
                    .orElseThrow(() -> new NotFoundException("Fabric color not found"));
            unitPrice += color.getFabricTier().getSurchargePaisa();
            measurementProfileRepository.findByIdAndUserId(request.measurementProfileId(), SecurityUtils.currentUserId())
                    .orElseThrow(() -> new NotFoundException("Measurement profile not found"));
        }

        CartItem item = CartItem.builder()
                .cart(cart)
                .productId(product.getId())
                .quantity(request.quantity())
                .custom(request.custom())
                .fabricColorId(request.fabricColorId())
                .measurementProfileId(request.measurementProfileId())
                .unitPricePaisa(unitPrice)
                .build();
        cart.getItems().add(item);
        return toDto(cartRepository.save(cart));
    }

    @Transactional
    public CartDtos.CartResponse clear() {
        Cart cart = getCartEntity();
        cart.getItems().clear();
        return toDto(cartRepository.save(cart));
    }

    public Cart getCartEntity() {
        Long userId = SecurityUtils.currentUserId();
        return cartRepository.findByUserId(userId).orElseGet(() ->
                cartRepository.save(Cart.builder().userId(userId).build())
        );
    }

    private CartDtos.CartResponse toDto(Cart cart) {
        var items = cart.getItems().stream().map(i -> {
            String name = productRepository.findById(i.getProductId()).map(Product::getName).orElse("Product");
            long line = i.getUnitPricePaisa() * i.getQuantity();
            return new CartDtos.CartItemResponse(
                    i.getId(), i.getProductId(), name, i.getQuantity(), i.isCustom(),
                    i.getFabricColorId(), i.getMeasurementProfileId(),
                    i.getUnitPricePaisa(), line
            );
        }).toList();
        long subtotal = items.stream().mapToLong(CartDtos.CartItemResponse::lineTotalPaisa).sum();
        return new CartDtos.CartResponse(cart.getId(), items, subtotal);
    }
}
