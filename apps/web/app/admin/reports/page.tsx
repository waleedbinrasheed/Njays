"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { api, formatPkr, type Order } from "@/lib/api";
import { AdminNav } from "@/components/AdminNav";

export default function AdminReportsPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    api<Order[]>("/api/v1/admin/orders")
      .then(setOrders)
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load report data"));
  }, []);

  const byStatus = useMemo(() => {
    const map = new Map<string, { count: number; totalPaisa: number }>();
    for (const o of orders) {
      const entry = map.get(o.status) || { count: 0, totalPaisa: 0 };
      entry.count += 1;
      entry.totalPaisa += o.totalPaisa;
      map.set(o.status, entry);
    }
    return Array.from(map.entries()).sort((a, b) => b[1].count - a[1].count);
  }, [orders]);

  const salesByDay = useMemo(() => {
    const map = new Map<string, number>();
    for (const o of orders) {
      const day = o.createdAt.slice(0, 10);
      map.set(day, (map.get(day) || 0) + o.amountPaidPaisa);
    }
    return Array.from(map.entries())
        .sort((a, b) => (a[0] < b[0] ? 1 : -1))
        .slice(0, 14);
  }, [orders]);

  const totals = useMemo(() => {
    const revenue = orders.reduce((sum, o) => sum + o.amountPaidPaisa, 0);
    const outstanding = orders.reduce((sum, o) => sum + o.balanceDuePaisa, 0);
    return { revenue, outstanding };
  }, [orders]);

  return (
    <section className="container section">
      <AdminNav />
      <div className="page-header">
        <span className="section-label">Reports</span>
        <h2>Reports</h2>
        <p className="lead">
          For a specific customer's order history, use{" "}
          <Link href="/admin/customers" className="link-subtle">
            Search Customer
          </Link>
          .
        </p>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="pos-grid">
        <div>
          <h3>Orders by status</h3>
          <table>
            <thead>
              <tr>
                <th>Status</th>
                <th className="num">Orders</th>
                <th className="num">Value</th>
              </tr>
            </thead>
            <tbody>
              {byStatus.map(([status, entry]) => (
                <tr key={status}>
                  <td>{status}</td>
                  <td className="num">{entry.count}</td>
                  <td className="num">{formatPkr(entry.totalPaisa)}</td>
                </tr>
              ))}
              {byStatus.length === 0 && (
                <tr>
                  <td colSpan={3} className="muted">
                    No orders yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div>
          <h3>Sales / Payments (last 14 days with activity)</h3>
          <p className="muted">
            Total collected: <strong>{formatPkr(totals.revenue)}</strong> - Outstanding:{" "}
            <strong>{formatPkr(totals.outstanding)}</strong>
          </p>
          <table>
            <thead>
              <tr>
                <th>Day</th>
                <th className="num">Collected</th>
              </tr>
            </thead>
            <tbody>
              {salesByDay.map(([day, paisa]) => (
                <tr key={day}>
                  <td>{day}</td>
                  <td className="num">{formatPkr(paisa)}</td>
                </tr>
              ))}
              {salesByDay.length === 0 && (
                <tr>
                  <td colSpan={2} className="muted">
                    No payments yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}
