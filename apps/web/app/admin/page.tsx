"use client";

import { useEffect, useState } from "react";
import { api, formatPkr } from "@/lib/api";
import { AdminAssistant } from "@/components/AdminAssistant";

type Order = {
  id: number;
  publicCode: string;
  status: string;
  totalPaisa: number;
  whatsappPhone?: string;
};

type Payment = {
  id: number;
  orderId: number;
  orderPublicCode?: string;
  method: string;
  status: string;
  amountPaisa: number;
  proofUrl?: string;
};

const STATUSES = [
  "PAYMENT_PENDING",
  "PAYMENT_CONFIRMED",
  "IN_CUTTING",
  "IN_STITCHING",
  "QUALITY_CHECK",
  "READY_TO_DISPATCH",
  "DISPATCHED",
  "DELIVERED",
  "CANCELLED",
];

export default function AdminPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  function load() {
    Promise.all([
      api<Order[]>("/api/v1/admin/orders"),
      api<Payment[]>("/api/v1/admin/payments/pending"),
    ])
      .then(([o, p]) => {
        setOrders(o);
        setPayments(p);
      })
      .catch((e) => setError(e.message));
  }

  useEffect(() => {
    load();
  }, []);

  async function updateStatus(id: number, status: string) {
    setMessage("");
    setError("");
    try {
      await api(`/api/v1/admin/orders/${id}/status`, {
        method: "PATCH",
        body: JSON.stringify({ status, note: `Moved to ${status}` }),
      });
      setMessage(`Order #${id} → ${status}`);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Update failed");
    }
  }

  async function confirmPayment(id: number) {
    setMessage("");
    setError("");
    try {
      await api(`/api/v1/admin/payments/${id}/confirm`, { method: "POST" });
      setMessage(`Payment #${id} confirmed`);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Confirm failed");
    }
  }

  return (
    <section className="container section">
      <div className="page-header">
        <span className="section-label">Studio</span>
        <h2>Admin</h2>
        <p className="lead">
          Sign in as admin@menswear.local / Admin@12345 — manage orders or{" "}
          <a href="/admin/products" className="link-subtle">
            add a new dress
          </a>
          .
        </p>
      </div>

      <AdminAssistant />

      {error && <div className="error">{error}</div>}
      {message && <p className="success">{message}</p>}

      <h3>Pending payments</h3>
      <div className="list-stack" style={{ marginBottom: "2rem" }}>
        {payments.length === 0 && <p className="muted">No pending payments.</p>}
        {payments.map((p) => (
          <div key={p.id} className="panel form">
            <div className="list-row" style={{ borderBottom: "none", paddingTop: 0 }}>
              <div>
                <strong>
                  {p.orderPublicCode || `Order #${p.orderId}`} · {p.method}
                </strong>
                <div className="muted">
                  <span className="status-pill">{p.status}</span>
                </div>
                {p.proofUrl && (
                  <a href={p.proofUrl} target="_blank" rel="noreferrer" className="link-subtle">
                    View proof
                  </a>
                )}
                {p.method === "BANK_TRANSFER" && !p.proofUrl && (
                  <div className="muted">No proof uploaded yet</div>
                )}
              </div>
              <div className="price">{formatPkr(p.amountPaisa)}</div>
            </div>
            {(p.method === "BANK_TRANSFER" || p.method === "COD") && (
              <button className="btn btn-primary" onClick={() => confirmPayment(p.id)}>
                Confirm payment
              </button>
            )}
          </div>
        ))}
      </div>

      <h3>Orders</h3>
      <div className="list-stack">
        {orders.map((o) => (
          <div key={o.id} className="panel form">
            <div className="list-row" style={{ borderBottom: "none", paddingTop: 0 }}>
              <div>
                <strong>
                  {o.publicCode} · #{o.id}
                </strong>
                <div className="muted">
                  <span className="status-pill">{o.status}</span> · {o.whatsappPhone || "no phone"}
                </div>
              </div>
              <div className="price">{formatPkr(o.totalPaisa)}</div>
            </div>
            <label>
              Update status
              <select defaultValue={o.status} onChange={(e) => updateStatus(o.id, e.target.value)}>
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </label>
          </div>
        ))}
      </div>
    </section>
  );
}
