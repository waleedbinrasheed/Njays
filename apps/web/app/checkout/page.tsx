"use client";

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, formatPkr } from "@/lib/api";

type Order = { id: number; publicCode: string; totalPaisa: number };
type Payment = {
  id: number;
  method: string;
  status: string;
  amountPaisa?: number;
  orderPublicCode?: string;
  failureReason?: string | null;
  expiresAt?: string | null;
  proofUrl?: string | null;
  bankInstructions?: {
    accountTitle: string;
    accountNumber: string;
    bankName: string;
    iban: string;
    reference: string;
    amountPaisa?: number;
    currency?: string;
  };
  jazzCashRedirect?: {
    actionUrl: string;
    fields: Record<string, string>;
    sandbox: boolean;
    expiresAt?: string;
  };
};

type Method = "COD" | "BANK_TRANSFER" | "JAZZCASH";

const METHODS: { id: Method; title: string; desc: string }[] = [
  { id: "COD", title: "Cash on delivery", desc: "Pay when your order arrives" },
  { id: "BANK_TRANSFER", title: "Bank transfer", desc: "Transfer & upload proof" },
  { id: "JAZZCASH", title: "JazzCash", desc: "Pay securely via JazzCash wallet" },
];

function idempotencyKey(orderId: number, method: Method) {
  return `checkout-${orderId}-${method}`;
}

export default function CheckoutPage() {
  const router = useRouter();
  const formRef = useRef<HTMLFormElement>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [payment, setPayment] = useState<Payment | null>(null);
  const [order, setOrder] = useState<Order | null>(null);
  const [proofUrl, setProofUrl] = useState("");
  const [method, setMethod] = useState<Method>("COD");
  const [copied, setCopied] = useState("");
  const [proofSaved, setProofSaved] = useState(false);

  const amountLabel = useMemo(() => {
    if (payment?.amountPaisa != null) return formatPkr(payment.amountPaisa);
    if (order) return formatPkr(order.totalPaisa);
    return null;
  }, [payment, order]);

  useEffect(() => {
    if (payment?.method === "JAZZCASH" && payment.jazzCashRedirect && !payment.jazzCashRedirect.sandbox) {
      const t = window.setTimeout(() => formRef.current?.submit(), 600);
      return () => window.clearTimeout(t);
    }
  }, [payment]);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setLoading(true);
    const fd = new FormData(e.currentTarget);
    try {
      const created = await api<Order>("/api/v1/orders", {
        method: "POST",
        body: JSON.stringify({
          shippingAddress: {
            line1: String(fd.get("line1")),
            line2: String(fd.get("line2") || ""),
            city: String(fd.get("city")),
            province: String(fd.get("province") || ""),
            postalCode: String(fd.get("postalCode") || ""),
            country: "PK",
          },
          whatsappPhone: String(fd.get("whatsappPhone")),
          customerNote: String(fd.get("customerNote") || ""),
        }),
      });
      setOrder(created);

      const pay = await api<Payment>(`/api/v1/orders/${created.id}/payments`, {
        method: "POST",
        body: JSON.stringify({
          method,
          idempotencyKey: idempotencyKey(created.id, method),
        }),
      });
      setPayment(pay);

      if (method === "COD") {
        // Stay on confirmation — do not leave before user sees order code
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Checkout failed");
    } finally {
      setLoading(false);
    }
  }

  async function simulateJazzCash() {
    if (!payment) return;
    setLoading(true);
    setError("");
    try {
      const updated = await api<Payment>(`/api/v1/payments/${payment.id}/simulate-jazzcash-success`, {
        method: "POST",
      });
      setPayment(updated);
      router.push(`/checkout/jazzcash/return?ok=true&status=COMPLETED&paymentId=${updated.id}&order=${updated.orderPublicCode || ""}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Simulation failed");
    } finally {
      setLoading(false);
    }
  }

  async function submitProof() {
    if (!payment || !proofUrl.trim()) return;
    setLoading(true);
    setError("");
    try {
      const updated = await api<Payment>(`/api/v1/payments/${payment.id}/proof`, {
        method: "POST",
        body: JSON.stringify({ proofUrl: proofUrl.trim() }),
      });
      setPayment(updated);
      setProofSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save proof");
    } finally {
      setLoading(false);
    }
  }

  function postToJazzCash() {
    if (!payment?.jazzCashRedirect || !formRef.current) return;
    formRef.current.submit();
  }

  async function copyText(label: string, value: string) {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(label);
      window.setTimeout(() => setCopied(""), 1600);
    } catch {
      setCopied("");
    }
  }

  return (
    <section className="container section">
      <div className="page-header" style={{ maxWidth: 720 }}>
        <span className="section-label">Checkout</span>
        <h2>Complete your order</h2>
        <p className="lead">Secure payment via COD, bank transfer, or JazzCash.</p>
      </div>

      {!payment && (
        <form className="panel form" onSubmit={onSubmit} style={{ maxWidth: 720 }}>
          <div className="form-row">
            <label>
              Address line 1
              <input name="line1" required autoComplete="address-line1" />
            </label>
            <label>
              Address line 2
              <input name="line2" autoComplete="address-line2" />
            </label>
          </div>
          <div className="form-row">
            <label>
              City
              <input name="city" required autoComplete="address-level2" />
            </label>
            <label>
              Province
              <input name="province" autoComplete="address-level1" />
            </label>
          </div>
          <label>
            WhatsApp phone
            <input name="whatsappPhone" placeholder="03001234567" required autoComplete="tel" />
          </label>
          <label>
            Note
            <textarea name="customerNote" rows={3} />
          </label>

          <fieldset style={{ border: "none", margin: 0, padding: 0 }}>
            <legend style={{ fontWeight: 600, marginBottom: "0.65rem", color: "var(--ink-soft)" }}>
              Payment method
            </legend>
            <div className="option-grid" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))" }}>
              {METHODS.map((m) => (
                <button
                  key={m.id}
                  type="button"
                  className={`option-chip${method === m.id ? " active" : ""}`}
                  onClick={() => setMethod(m.id)}
                  aria-pressed={method === m.id}
                >
                  {m.title}
                  <small>{m.desc}</small>
                </button>
              ))}
            </div>
          </fieldset>

          {error && <div className="error">{error}</div>}
          <button className="btn btn-primary" disabled={loading}>
            {loading ? "Placing order…" : "Place order & continue"}
          </button>
        </form>
      )}

      {payment && method === "COD" && (
        <div className="panel form" style={{ maxWidth: 720 }}>
          <span className="status-pill">Cash on delivery</span>
          <h3 style={{ marginBottom: 0 }}>Order placed</h3>
          <p>
            Order <strong>{order?.publicCode || payment.orderPublicCode}</strong>
            {amountLabel ? <> · {amountLabel}</> : null}
          </p>
          <p className="muted">
            Pay in cash when your parcel arrives. Our team will confirm the order and begin stitching.
          </p>
          <div className="form-actions">
            <Link href="/account/orders" className="btn btn-primary">
              View orders
            </Link>
            <Link href="/track" className="btn btn-ghost">
              Track suit
            </Link>
          </div>
        </div>
      )}

      {payment && method === "BANK_TRANSFER" && payment.bankInstructions && (
        <div className="panel form" style={{ maxWidth: 720 }}>
          <span className="status-pill">{payment.status.replaceAll("_", " ")}</span>
          <h3 style={{ marginBottom: 0 }}>Transfer instructions</h3>
          <p>
            Order <strong>{order?.publicCode || payment.orderPublicCode}</strong>
            {amountLabel ? <> · Pay exactly {amountLabel}</> : null}
          </p>
          <div className="section-card">
            {(
              [
                ["Bank", payment.bankInstructions.bankName],
                ["Title", payment.bankInstructions.accountTitle],
                ["Account", payment.bankInstructions.accountNumber],
                ["IBAN", payment.bankInstructions.iban],
                ["Reference", payment.bankInstructions.reference],
              ] as const
            ).map(([label, value]) => (
              <div key={label} className="detail-row">
                <div className="detail-row-value">
                  <div className="muted" style={{ fontSize: "0.78rem" }}>
                    {label}
                  </div>
                  <strong>{value}</strong>
                </div>
                <button type="button" className="btn btn-ghost" style={{ padding: "0.35rem 0.7rem" }} onClick={() => copyText(label, value)}>
                  {copied === label ? "Copied" : "Copy"}
                </button>
              </div>
            ))}
          </div>
          <p className="muted">Use your order code as the transfer reference so we can match payment quickly.</p>
          <label>
            Transfer proof URL (screenshot / receipt link)
            <input
              value={proofUrl}
              onChange={(e) => setProofUrl(e.target.value)}
              placeholder="https://…"
              inputMode="url"
            />
          </label>
          {proofSaved && <p className="success">Proof submitted. Waiting for admin confirmation.</p>}
          {error && <div className="error">{error}</div>}
          <div className="form-actions">
            <button type="button" className="btn btn-primary" disabled={loading || !proofUrl.trim()} onClick={submitProof}>
              {loading ? "Saving…" : "Submit proof"}
            </button>
            <Link href="/account/orders" className="btn btn-ghost">
              View orders
            </Link>
          </div>
        </div>
      )}

      {payment && method === "JAZZCASH" && (
        <div className="panel form" style={{ maxWidth: 720 }}>
          <span className="status-pill">JazzCash</span>
          <h3 style={{ marginBottom: 0 }}>Complete JazzCash payment</h3>
          <p>
            Order <strong>{order?.publicCode || payment.orderPublicCode}</strong>
            {amountLabel ? <> · {amountLabel}</> : null}
          </p>
          {payment.status === "COMPLETED" ? (
            <p className="success">Payment confirmed.</p>
          ) : payment.jazzCashRedirect ? (
            <>
              <p className="muted">
                {payment.jazzCashRedirect.sandbox
                  ? "Sandbox mode is on — simulate locally, or open the JazzCash merchant form with your test credentials."
                  : "You will be redirected to JazzCash to authorize payment. Do not close the window until you return here."}
              </p>
              {error && <div className="error">{error}</div>}
              <div className="form-actions">
                <button type="button" className="btn btn-primary" disabled={loading} onClick={postToJazzCash}>
                  Pay with JazzCash
                </button>
                {payment.jazzCashRedirect.sandbox && (
                  <button type="button" className="btn btn-ghost" disabled={loading} onClick={simulateJazzCash}>
                    Simulate success
                  </button>
                )}
              </div>
              <form ref={formRef} method="POST" action={payment.jazzCashRedirect.actionUrl} style={{ display: "none" }}>
                {Object.entries(payment.jazzCashRedirect.fields).map(([k, v]) => (
                  <input key={k} type="hidden" name={k} value={v} />
                ))}
              </form>
            </>
          ) : (
            <p className="muted">{payment.failureReason || "This JazzCash session is no longer available. Place the order again."}</p>
          )}
          <div className="form-actions">
            <Link href="/account/orders" className="btn btn-ghost">
              View orders
            </Link>
          </div>
        </div>
      )}
    </section>
  );
}
