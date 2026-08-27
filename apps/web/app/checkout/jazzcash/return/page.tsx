"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { api } from "@/lib/api";

function JazzCashReturnInner() {
  const params = useSearchParams();
  const okParam = params.get("ok");
  const status = params.get("status") || "";
  const paymentId = params.get("paymentId");
  const order = params.get("order") || "";
  const message = params.get("message") || "";

  const [ok, setOk] = useState(okParam === "true" || status === "COMPLETED");
  const [text, setText] = useState(message || (ok ? "Payment successful." : "Confirming payment…"));
  const [checking, setChecking] = useState(false);

  useEffect(() => {
    // Legacy path: if JazzCash still posts query params to the frontend, forward to API webhook.
    const hasJazzFields = params.get("pp_TxnRefNo") || params.get("pp_BillReference");
    if (hasJazzFields && !paymentId) {
      const payload = new URLSearchParams();
      params.forEach((value, key) => payload.set(key, value));
      fetch(`${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080"}/api/v1/payments/webhooks/jazzcash`, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          Accept: "application/json",
        },
        body: payload,
        redirect: "manual",
      })
        .then(async (res) => {
          if (res.status >= 300 && res.status < 400) {
            const loc = res.headers.get("Location");
            if (loc) {
              window.location.href = loc;
              return;
            }
          }
          if (!res.ok) throw new Error("confirm failed");
          const data = (await res.json()) as { ok?: boolean; status?: string; message?: string; orderPublicCode?: string };
          setOk(!!data.ok);
          setText(data.message || (data.ok ? "Payment recorded." : "Payment was not completed."));
        })
        .catch(() => {
          setOk(false);
          setText("Could not confirm automatically. Contact support with your order code.");
        });
      return;
    }

    if (!paymentId || !localStorage.getItem("accessToken")) {
      if (!message) {
        setText(ok ? "Payment recorded. You can view your order status." : "Payment was not completed.");
      }
      return;
    }

    setChecking(true);
    api<{ status: string; failureReason?: string; orderPublicCode?: string }>(`/api/v1/payments/${paymentId}`)
      .then((p) => {
        const success = p.status === "COMPLETED";
        setOk(success);
        setText(
          success
            ? `Payment confirmed${p.orderPublicCode ? ` for ${p.orderPublicCode}` : ""}.`
            : p.failureReason || `Payment status: ${p.status}`
        );
      })
      .catch(() => {
        /* keep redirect message */
      })
      .finally(() => setChecking(false));
  }, [params, paymentId, message, ok]);

  return (
    <section className="container section">
      <div className="container-mid" style={{ width: "100%", padding: 0 }}>
        <div className="page-header">
          <span className="section-label">JazzCash</span>
          <h2>{ok ? "Payment received" : "Payment update"}</h2>
        </div>
        <div className="panel">
          <span className="status-pill">{status || (ok ? "COMPLETED" : "PENDING")}</span>
          <p className={ok ? "success" : "muted"} style={{ marginTop: "0.85rem" }}>
            {checking ? "Verifying with our servers…" : text}
          </p>
          {order && (
            <p>
              Order code: <strong>{order}</strong>
            </p>
          )}
          <div className="form-actions" style={{ marginTop: "1rem" }}>
            <Link href="/account/orders" className="btn btn-primary">
              My orders
            </Link>
            <Link href="/track" className="btn btn-ghost">
              Track suit
            </Link>
            {!ok && (
              <Link href="/checkout" className="btn btn-ghost">
                Back to checkout
              </Link>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}

export default function JazzCashReturnPage() {
  return (
    <Suspense
      fallback={
        <div className="container section">
          <div className="skeleton" aria-busy="true" aria-label="Loading" />
        </div>
      }
    >
      <JazzCashReturnInner />
    </Suspense>
  );
}
