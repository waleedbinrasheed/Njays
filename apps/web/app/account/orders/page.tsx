"use client";

import { useEffect, useState } from "react";
import { api, formatPkr } from "@/lib/api";

type Order = {
  id: number;
  publicCode: string;
  status: string;
  totalPaisa: number;
  orderType: string;
  timeline: { toStatus: string; note?: string; createdAt: string }[];
};

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api<Order[]>("/api/v1/orders")
      .then(setOrders)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  return (
    <section className="container section">
      <div className="page-header">
        <span className="section-label">Account</span>
        <h2>Your orders</h2>
        <p className="lead">Follow every stage from cutting to delivery.</p>
      </div>
      {error && <div className="error">{error}</div>}
      {loading && <div className="skeleton" aria-busy="true" aria-label="Loading orders" />}
      <div className="list-stack">
        {orders.map((o) => (
          <div key={o.id} className="panel">
            <div className="list-row" style={{ borderBottom: "none", paddingTop: 0 }}>
              <div>
                <strong>{o.publicCode}</strong>
                <div className="muted" style={{ marginTop: "0.25rem" }}>
                  {o.orderType} · <span className="status-pill">{o.status}</span>
                </div>
              </div>
              <div className="price">{formatPkr(o.totalPaisa)}</div>
            </div>
            <ul className="timeline">
              {o.timeline.map((t, i) => (
                <li key={i}>
                  <strong>{t.toStatus}</strong>
                  <span className="muted">{t.note}</span>
                </li>
              ))}
            </ul>
          </div>
        ))}
        {!loading && !error && orders.length === 0 && <p className="muted">No orders yet.</p>}
      </div>
    </section>
  );
}
