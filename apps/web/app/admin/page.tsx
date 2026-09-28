"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api, formatPkr, type DashboardSummary } from "@/lib/api";
import { AdminNav } from "@/components/AdminNav";

const QUICK_ACTIONS = [
  { href: "/admin/orders/new", label: "New Order" },
  { href: "/admin/customers", label: "Search Customer" },
  { href: "/admin/designs?new=1", label: "New Dress Design" },
  { href: "/admin/fabrics", label: "New Fabric" },
];

export default function AdminDashboardPage() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api<DashboardSummary>("/api/v1/admin/dashboard/summary")
      .then(setSummary)
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load dashboard"));
  }, []);

  return (
    <section className="container section">
      <AdminNav />
      <div className="page-header">
        <span className="section-label">Studio</span>
        <h2>Dashboard</h2>
        <p className="lead">What do you need to do today?</p>
      </div>

      {error && <div className="error">{error}</div>}

      <h3>Quick actions</h3>
      <div className="pos-measure-grid" style={{ marginBottom: "2rem" }}>
        {QUICK_ACTIONS.map((a) => (
          <Link key={a.href} href={a.href} className="btn btn-primary" style={{ justifyContent: "center" }}>
            {a.label}
          </Link>
        ))}
      </div>

      <h3>Summary</h3>
      {!summary && !error && <div className="skeleton" aria-busy="true" />}
      {summary && (
        <div className="assistant-highlights">
          <SummaryCard label="Today's Orders" value={summary.todaysOrders} href="/admin/orders" />
          <SummaryCard label="Orders In Progress" value={summary.ordersInProgress} href="/admin/orders" />
          <SummaryCard label="Ready for Delivery" value={summary.readyForDelivery} href="/admin/orders" />
          <SummaryCard label="Orders Due Today" value={summary.ordersDueToday} href="/admin/orders" />
          <SummaryCard
            label="Overdue Orders"
            value={summary.overdueOrders}
            href="/admin/orders"
            variant={summary.overdueOrders > 0 ? "critical" : "info"}
          />
          <SummaryCard label="Today's Payments" value={formatPkr(summary.todaysPaymentsPaisa)} href="/admin/payments" />
          <SummaryCard
            label="Outstanding Payments"
            value={summary.outstandingPayments}
            href="/admin/payments/outstanding"
            variant={summary.outstandingPayments > 0 ? "warn" : "info"}
          />
          <SummaryCard label="Awaiting Dispatch" value={summary.awaitingDispatch} href="/admin/orders" />
        </div>
      )}
    </section>
  );
}

function SummaryCard({
  label,
  value,
  href,
  variant = "info",
}: {
  label: string;
  value: number | string;
  href: string;
  variant?: "info" | "warn" | "critical";
}) {
  return (
    <Link href={href} className={`highlight-card highlight-card--${variant}`} style={{ display: "block" }}>
      <strong>{value}</strong>
      <p>{label}</p>
    </Link>
  );
}
