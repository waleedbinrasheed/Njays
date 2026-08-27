package com.menswear.cart.web;

import com.menswear.cart.dto.CartDtos;
import com.menswear.cart.service.CartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartDtos.CartResponse get() {
        return cartService.getOrCreate();
    }

    @PostMapping("/items")
    public CartDtos.CartResponse add(@Valid @RequestBody CartDtos.AddItemRequest request) {
        return cartService.addItem(request);
    }

    @DeleteMapping
    public CartDtos.CartResponse clear() {
        return cartService.clear();
    }
}
