"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { api, formatPkr, type Order } from "@/lib/api";
import { AdminNav } from "@/components/AdminNav";

export default function AdminOutstandingPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [payingOrderId, setPayingOrderId] = useState<number | null>(null);
  const [payAmount, setPayAmount] = useState("");

  function load() {
    api<Order[]>("/api/v1/admin/orders")
      .then(setOrders)
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load orders"));
  }

  useEffect(() => {
    load();
  }, []);

  const outstanding = useMemo(
    () => orders.filter((o) => o.balanceDuePaisa > 0 && o.status !== "CANCELLED"),
    [orders]
  );
  const totalOutstanding = outstanding.reduce((sum, o) => sum + o.balanceDuePaisa, 0);

  async function collectPayment(id: number) {
    setError("");
    const amountPaisa = Math.round(Number(payAmount) * 100);
    if (!amountPaisa || amountPaisa <= 0) {
      setError("Enter a valid amount");
      return;
    }
    try {
      await api(`/api/v1/admin/orders/${id}/payments`, {
        method: "POST",
        body: JSON.stringify({ method: "CASH", amountPaisa }),
      });
      setMessage(`Payment recorded for order #${id}`);
      setPayingOrderId(null);
      setPayAmount("");
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not record payment");
    }
  }

  return (
    <section className="container section">
      <AdminNav />
      <div className="page-header">
        <span className="section-label">Payments</span>
        <h2>Outstanding</h2>
        <p className="lead">
          Orders with a remaining balance{outstanding.length > 0 && ` — ${formatPkr(totalOutstanding)} total`}.
        </p>
      </div>

      {error && <div className="error">{error}</div>}
      {message && <p className="success">{message}</p>}

      <div className="list-stack">
        {outstanding.map((o) => (
          <div key={o.id} className="panel form">
            <div className="list-row" style={{ borderBottom: "none", paddingTop: 0 }}>
              <div>
                <strong>{o.publicCode}</strong>
                <div className="muted">
                  <span className="status-pill">{o.status}</span> - {o.whatsappPhone || "no phone"}
                </div>
              </div>
              <div style={{ textAlign: "right" }}>
                <div className="muted">Total {formatPkr(o.totalPaisa)}</div>
                <div className="price">{formatPkr(o.balanceDuePaisa)}</div>
              </div>
            </div>
            <div className="form-actions">
              <Link href={`/invoice/${o.id}`} className="link-subtle">
                Print Invoice
              </Link>
              {payingOrderId === o.id ? (
                <>
                  <input
                    type="number"
                    min={1}
                    max={o.balanceDuePaisa / 100}
                    placeholder={`Up to ${formatPkr(o.balanceDuePaisa)}`}
                    value={payAmount}
                    onChange={(e) => setPayAmount(e.target.value)}
                    style={{ maxWidth: 140 }}
                  />
                  <button type="button" className="btn btn-primary" onClick={() => collectPayment(o.id)}>
                    Record payment
                  </button>
                  <button type="button" className="btn btn-ghost" onClick={() => setPayingOrderId(null)}>
                    Cancel
                  </button>
                </>
              ) : (
                <button type="button" className="btn btn-ghost" onClick={() => setPayingOrderId(o.id)}>
                  Collect payment
                </button>
              )}
            </div>
          </div>
        ))}
        {outstanding.length === 0 && <p className="muted">Nothing outstanding.</p>}
      </div>
    </section>
  );
}
