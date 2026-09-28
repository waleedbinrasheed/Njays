"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api, formatPkr } from "@/lib/api";
import { AdminNav } from "@/components/AdminNav";

type Payment = {
  id: number;
  orderId: number;
  orderPublicCode?: string;
  method: string;
  status: string;
  amountPaisa: number;
  proofUrl?: string;
};

export default function AdminPaymentsPage() {
  const [payments, setPayments] = useState<Payment[]>([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  function load() {
    api<Payment[]>("/api/v1/admin/payments/pending")
      .then(setPayments)
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load payments"));
  }

  useEffect(() => {
    load();
  }, []);

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
      <AdminNav />
      <div className="page-header">
        <span className="section-label">Payments</span>
        <h2>Pending payments</h2>
        <p className="lead">
          Bank transfer and COD payments awaiting confirmation. For orders with an outstanding balance, see{" "}
          <Link href="/admin/payments/outstanding" className="link-subtle">
            Outstanding
          </Link>
          .
        </p>
      </div>

      {error && <div className="error">{error}</div>}
      {message && <p className="success">{message}</p>}

      <div className="list-stack">
        {payments.length === 0 && <p className="muted">No pending payments.</p>}
        {payments.map((p) => (
          <div key={p.id} className="panel form">
            <div className="list-row" style={{ borderBottom: "none", paddingTop: 0 }}>
              <div>
                <strong>
                  {p.orderPublicCode || `Order #${p.orderId}`} - {p.method}
                </strong>
                <div className="muted">
                  <span className="status-pill">{p.status}</span>
                </div>
                {p.proofUrl && (
                  <a href={p.proofUrl} target="_blank" rel="noreferrer" className="link-subtle">
                    View proof
                  </a>
                )}
                {p.method === "BANK_TRANSFER" && !p.proofUrl && <div className="muted">No proof uploaded yet</div>}
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
    </section>
  );
}
