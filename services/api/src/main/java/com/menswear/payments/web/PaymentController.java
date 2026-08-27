package com.menswear.payments.web;

import com.menswear.common.exception.BadRequestException;
import com.menswear.common.exception.NotFoundException;
import com.menswear.payments.dto.PaymentDtos;
import com.menswear.payments.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/orders/{orderId}/payments")
    public PaymentDtos.PaymentResponse create(
            @PathVariable Long orderId,
            @Valid @RequestBody PaymentDtos.CreatePaymentRequest request
    ) {
        return paymentService.create(orderId, request);
    }

    @GetMapping("/orders/{orderId}/payments")
    public List<PaymentDtos.PaymentResponse> listForOrder(@PathVariable Long orderId) {
        return paymentService.listForOrder(orderId);
    }

    @GetMapping("/payments/{paymentId}")
    public PaymentDtos.PaymentResponse getPayment(@PathVariable Long paymentId) {
        return paymentService.getOwned(paymentId);
    }

    @PostMapping("/payments/{paymentId}/proof")
    public PaymentDtos.PaymentResponse attachProof(
            @PathVariable Long paymentId,
            @Valid @RequestBody PaymentDtos.ProofRequest request
    ) {
        return paymentService.attachProof(paymentId, request);
    }

    @GetMapping("/admin/payments/pending")
    public List<PaymentDtos.PaymentResponse> adminPending() {
        return paymentService.adminPending();
    }

    @PostMapping("/admin/payments/{paymentId}/confirm")
    public PaymentDtos.PaymentResponse confirm(@PathVariable Long paymentId) {
        return paymentService.confirmAdmin(paymentId);
    }

    /**
     * JazzCash return URL / IPN. JazzCash POSTs form fields here after payment.
     * Browser clients are redirected to the storefront; JSON clients get an ack.
     */
    @RequestMapping(
            value = "/payments/webhooks/jazzcash",
            method = {RequestMethod.GET, RequestMethod.POST}
    )
    public Object jazzcashWebhook(
            HttpServletRequest request,
            @RequestParam Map<String, String> params
    ) {
        Map<String, String> payload = mergeParams(request, params);
        boolean wantsJson = wantsJson(request);

        try {
            PaymentDtos.WebhookAck ack = paymentService.handleJazzCashCallback(payload);
            if (wantsJson) {
                return ResponseEntity.ok(ack);
            }
            return redirect(paymentService.frontendReturnUrl(ack));
        } catch (BadRequestException | NotFoundException ex) {
            PaymentDtos.WebhookAck fail =
                    new PaymentDtos.WebhookAck(false, "FAILED", null, null, ex.getMessage());
            if (wantsJson) {
                return ResponseEntity.badRequest().body(fail);
            }
            return redirect(paymentService.frontendReturnUrl(fail));
        }
    }

    @PostMapping("/payments/{paymentId}/simulate-jazzcash-success")
    public PaymentDtos.PaymentResponse simulate(@PathVariable Long paymentId) {
        return paymentService.simulateJazzCashSuccess(paymentId);
    }

    private static boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null
                && accept.contains(MediaType.APPLICATION_JSON_VALUE)
                && !accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    private static RedirectView redirect(String url) {
        RedirectView view = new RedirectView(url);
        view.setStatusCode(HttpStatus.SEE_OTHER);
        return view;
    }

    private static Map<String, String> mergeParams(HttpServletRequest request, Map<String, String> params) {
        Map<String, String> payload = new LinkedHashMap<>();
        if (params != null) {
            payload.putAll(params);
        }
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            payload.putIfAbsent(name, request.getParameter(name));
        }
        return payload;
    }
}
