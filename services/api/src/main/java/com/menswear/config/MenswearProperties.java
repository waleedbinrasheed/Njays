package com.menswear.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "menswear")
public record MenswearProperties(
        Jwt jwt,
        Cors cors,
        Whatsapp whatsapp,
        Payments payments,
        Frontend frontend,
        Business business,
        String currency
) {
    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays) {}
    public record Cors(List<String> allowedOrigins) {}
    public record Whatsapp(String number) {}
    public record Business(String name, String address, String phone, String email) {}
    public record Payments(Bank bank, Jazzcash jazzcash) {}
    public record Bank(String accountTitle, String accountNumber, String bankName, String iban) {}
    public record Jazzcash(
            String merchantId,
            String password,
            String integritySalt,
            String returnUrl,
            String frontendReturnUrl,
            boolean sandbox,
            boolean requireSecureHash
    ) {}
    public record Frontend(String appUrl, boolean exposeResetLink) {}
}
