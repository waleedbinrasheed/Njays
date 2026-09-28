"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { api, formatPkr, type Order } from "@/lib/api";
import { AdminNav } from "@/components/AdminNav";

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

type Filter = "ALL" | "DUE_OR_OVERDUE" | "OUTSTANDING";

export default function AdminAllOrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [filter, setFilter] = useState<Filter>("ALL");
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

  const today = new Date().toISOString().slice(0, 10);

  const filtered = useMemo(() => {
    return orders.filter((o) => {
      if (statusFilter !== "ALL" && o.status !== statusFilter) return false;
      if (filter === "DUE_OR_OVERDUE" && !(o.expectedDeliveryDate && o.expectedDeliveryDate <= today)) return false;
      if (filter === "OUTSTANDING" && o.balanceDuePaisa <= 0) return false;
      return true;
    });
  }, [orders, statusFilter, filter, today]);

  async function updateStatus(id: number, status: string) {
    setMessage("");
    setError("");
    try {
      await api(`/api/v1/admin/orders/${id}/status`, {
        method: "PATCH",
        body: JSON.stringify({ status, note: `Moved to ${status}` }),
      });
      setMessage(`Order #${id} -> ${status}`);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Update failed");
    }
  }

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
        <span className="section-label">Orders</span>
        <h2>All Orders</h2>
        <p className="lead">Every order across branches.</p>
      </div>

      <div className="form-row" style={{ marginBottom: "1rem", alignItems: "end" }}>
        <label>
          Status
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="ALL">All statuses</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
        <label>
          View
          <select value={filter} onChange={(e) => setFilter(e.target.value as Filter)}>
            <option value="ALL">All orders</option>
            <option value="DUE_OR_OVERDUE">Due / overdue</option>
            <option value="OUTSTANDING">Outstanding balance</option>
          </select>
        </label>
      </div>

      {error && <div className="error">{error}</div>}
      {message && <p className="success">{message}</p>}

      <div className="list-stack">
        {filtered.map((o) => (
          <div key={o.id} className="panel form">
            <div className="list-row" style={{ borderBottom: "none", paddingTop: 0 }}>
              <div>
                <strong>
                  {o.publicCode} - #{o.id}
                </strong>
                <div className="muted">
                  <span className="status-pill">{o.status}</span> - {o.whatsappPhone || "no phone"}
                </div>
                <div className="muted">
                  {o.createdBranchName || "—"} -&gt; {o.dispatchBranchName || "—"}
                  {o.expectedDeliveryDate && ` - Due ${o.expectedDeliveryDate}`}
                </div>
              </div>
              <div style={{ textAlign: "right" }}>
                <div className="price">{formatPkr(o.totalPaisa)}</div>
                {o.balanceDuePaisa > 0 && (
                  <div className="muted">Balance: {formatPkr(o.balanceDuePaisa)}</div>
                )}
              </div>
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
            <div className="form-actions">
              <Link href={`/invoice/${o.id}`} className="link-subtle">
                Print Invoice
              </Link>
              {o.balanceDuePaisa > 0 &&
                (payingOrderId === o.id ? (
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
                ))}
            </div>
          </div>
        ))}
        {filtered.length === 0 && <p className="muted">No orders match.</p>}
      </div>
    </section>
  );
}
