package com.menswear.messaging.web;

import com.menswear.config.MenswearProperties;
import com.menswear.identity.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/whatsapp")
public class WhatsAppController {

    private final MenswearProperties properties;

    public WhatsAppController(MenswearProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/link")
    public Map<String, String> link(
            @RequestParam(defaultValue = "GENERAL") String context,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String orderCode,
            @RequestParam(required = false) String phone
    ) {
        String number = properties.whatsapp().number();
        StringBuilder text = new StringBuilder("Hi Menswear, ");
        switch (context.toUpperCase()) {
            case "PRODUCT" -> text.append("I want to order: ").append(nullToEmpty(productName));
            case "ORDER" -> text.append("I need help with order ").append(nullToEmpty(orderCode));
            case "MEASUREMENT" -> text.append("I need help submitting my measurements.");
            default -> text.append("I have a question about your suits.");
        }
        if (phone != null && !phone.isBlank()) {
            text.append(" My phone: ").append(AuthService.normalizePhone(phone));
        }
        String url = "https://wa.me/" + number + "?text=" + URLEncoder.encode(text.toString(), StandardCharsets.UTF_8);
        return Map.of("url", url, "number", number, "message", text.toString());
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
