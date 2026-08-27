# Menswear — Made-to-Measure Web App

Spring Boot 3 + Next.js 15 for men's traditional wear (NJAY'S by S.A.R).

**Payments:** COD · Bank transfer · JazzCash (HMAC-verified, production-ready)  
**Features:** Custom measurements · Order tracking · WhatsApp deep links

## Layout

```text
menswear/
  services/api/   Spring Boot API
  apps/web/       Next.js storefront
  docker-compose.yml
```

## Run (you install tools)

### 1. Postgres (or Docker)

```bash
docker compose up -d postgres
```

Or point `DB_URL` at your own Postgres and create DB `menswear`.

### 2. API

```bash
cd services/api
mvn spring-boot:run
```

- API: http://localhost:8080  
- Swagger: http://localhost:8080/swagger-ui.html  

**H2-only local (no Postgres):**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 3. Web

```bash
cd apps/web
npm install
npm run dev
```

- Storefront: http://localhost:3000

## Demo accounts

| Role | Email | Password |
|------|-------|----------|
| Admin | admin@menswear.local | Admin@12345 |

Register any customer from `/login`.

## Happy path

1. Register / login  
2. Save measurements at `/account/measurements`  
3. Open a product → Made to measure → Add to cart  
4. Checkout with COD, Bank transfer, or JazzCash  
5. Track at `/track` with order code + phone  
6. Admin updates status at `/admin`

## Payments (production)

### Methods

| Method | Customer flow | Confirmation |
|--------|---------------|--------------|
| **COD** | Order placed, pay on delivery | Admin confirms payment |
| **Bank transfer** | Transfer to configured account + submit proof URL | Admin confirms after verifying proof |
| **JazzCash** | Hosted merchant form (HMAC-SHA256 signed) | JazzCash POSTs to API return URL; hash + amount verified |

### JazzCash production checklist

1. Get **Merchant ID**, **Password**, and **Integrity Salt** from JazzCash.
2. Set environment variables (do **not** commit secrets):

```bash
JAZZCASH_MERCHANT_ID=...
JAZZCASH_PASSWORD=...
JAZZCASH_INTEGRITY_SALT=...
JAZZCASH_SANDBOX=false
JAZZCASH_REQUIRE_HASH=true
JAZZCASH_RETURN_URL=https://api.yourdomain.com/api/v1/payments/webhooks/jazzcash
JAZZCASH_FRONTEND_RETURN_URL=https://yourdomain.com/checkout/jazzcash/return
```

3. In the JazzCash merchant portal, register the same **Return URL** (API webhook).
4. Ensure the API URL is publicly reachable over HTTPS (JazzCash must POST back).
5. Configure bank details:

```bash
BANK_ACCOUNT_TITLE=...
BANK_ACCOUNT_NUMBER=...
BANK_NAME=...
BANK_IBAN=...
```

### Security built in

- JazzCash request/response integrity via **HMAC-SHA256** (`pp_SecureHash`)
- Amount must match `payment.amount_paisa`
- Unique `provider_ref` per attempt; optimistic locking (`version`)
- Idempotent payment create keys; open duplicates cancelled/superseded
- Simulate endpoint only when `JAZZCASH_SANDBOX=true` and caller owns the payment
- Already-paid orders cannot create another completed payment

### Sandbox

With `JAZZCASH_SANDBOX=true` (default for local), checkout shows **Simulate success**.  
Set `JAZZCASH_SANDBOX=false` before go-live.

## Key APIs

- `POST /api/v1/auth/register|login`
- `GET /api/v1/products`
- `POST /api/v1/me/measurements`
- `POST /api/v1/cart/items`
- `POST /api/v1/orders`
- `POST /api/v1/orders/{id}/payments`
- `GET /api/v1/payments/{id}`
- `POST /api/v1/payments/{id}/proof`
- `POST|GET /api/v1/payments/webhooks/jazzcash`
- `GET /api/v1/track?orderId=&phone=`
- `GET /api/v1/whatsapp/link`
- `PATCH /api/v1/admin/orders/{id}/status`
- `POST /api/v1/admin/payments/{id}/confirm`
