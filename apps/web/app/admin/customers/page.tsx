"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { api, formatPkr, type CustomerDetail } from "@/lib/api";
import { AdminNav } from "@/components/AdminNav";

type CustomerSummary = { id: number; fullName: string; phone?: string; email?: string };

export default function AdminSearchCustomerPage() {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<CustomerSummary[]>([]);
  const [detail, setDetail] = useState<CustomerDetail | null>(null);
  const [error, setError] = useState("");

  async function search(e: FormEvent) {
    e.preventDefault();
    setError("");
    setDetail(null);
    try {
      const found = await api<CustomerSummary[]>(`/api/v1/admin/customers?query=${encodeURIComponent(query)}`);
      setResults(found);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Search failed");
    }
  }

  async function loadDetail(id: number) {
    setError("");
    try {
      const d = await api<CustomerDetail>(`/api/v1/admin/customers/${id}/detail`);
      setDetail(d);
      setResults([]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load customer");
    }
  }

  return (
    <section className="container section">
      <AdminNav />
      <div className="page-header">
        <span className="section-label">Customers</span>
        <h2>Search Customer</h2>
        <p className="lead">Search by mobile number (e.g. 03001234567) or name.</p>
      </div>

      {error && <div className="error">{error}</div>}

      <form className="form-row panel" onSubmit={search} style={{ alignItems: "end", marginBottom: "1.5rem" }}>
        <label>
          Mobile number or name
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="03001234567" />
        </label>
        <button className="btn btn-primary">Search</button>
      </form>

      {results.length > 0 && (
        <div className="list-stack" style={{ marginBottom: "1.5rem" }}>
          {results.map((c) => (
            <button key={c.id} type="button" className="pos-customer-result" onClick={() => loadDetail(c.id)}>
              <div>
                <strong>{c.fullName}</strong>
                <div className="muted">{c.phone || c.email}</div>
              </div>
              <span className="link-subtle">View</span>
            </button>
          ))}
        </div>
      )}

      {detail && (
        <div style={{ display: "grid", gap: "1.5rem" }}>
          <div className="panel">
            <h3 style={{ marginTop: 0 }}>{detail.fullName}</h3>
            <p className="muted">
              {detail.phone} {detail.email ? `- ${detail.email}` : ""}
            </p>
            {detail.outstandingBalancePaisa > 0 && (
              <p>
                <span className="status-pill">Outstanding: {formatPkr(detail.outstandingBalancePaisa)}</span>
              </p>
            )}
            <Link
              href={`/admin/orders/new?customerId=${detail.id}`}
              className="btn btn-primary"
              style={{ marginTop: "0.5rem" }}
            >
              New order for this customer
            </Link>
          </div>

          <div className="panel">
            <h3 style={{ marginTop: 0 }}>Latest measurements</h3>
            {detail.measurements.length === 0 && <p className="muted">No measurement profiles saved.</p>}
            <div className="list-stack">
              {detail.measurements.map((m) => (
                <div key={m.id} className="list-row">
                  <div>
                    <strong>{m.name}</strong>
                    <div className="muted">
                      Chest {m.chest ?? "—"} - Kameez L {m.kameezLength ?? "—"} - Shalwar L {m.shalwarLength ?? "—"}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="panel">
            <h3 style={{ marginTop: 0 }}>Orders</h3>
            {detail.orders.length === 0 && <p className="muted">No orders yet.</p>}
            <table>
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Garment</th>
                  <th>Status</th>
                  <th className="num">Total</th>
                  <th className="num">Paid</th>
                  <th className="num">Balance</th>
                  <th>Delivery</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {detail.orders.map((o) => (
                  <tr key={o.id}>
                    <td>{o.publicCode}</td>
                    <td>{o.items[0]?.productName || "—"}</td>
                    <td>
                      <span className="status-pill">{o.status}</span>
                    </td>
                    <td className="num">{formatPkr(o.totalPaisa)}</td>
                    <td className="num">{formatPkr(o.amountPaidPaisa)}</td>
                    <td className="num">{formatPkr(o.balanceDuePaisa)}</td>
                    <td>{o.expectedDeliveryDate || "—"}</td>
                    <td>
                      <Link href={`/invoice/${o.id}`} className="link-subtle">
                        Open
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}
