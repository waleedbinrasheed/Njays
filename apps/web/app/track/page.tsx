"use client";

import { FormEvent, useState } from "react";
import { api } from "@/lib/api";

type TrackResponse = {
  publicCode: string;
  status: string;
  timeline: { fromStatus?: string; toStatus: string; note?: string; createdAt: string }[];
};

export default function TrackPage() {
  const [result, setResult] = useState<TrackResponse | null>(null);
  const [error, setError] = useState("");
  const [waUrl, setWaUrl] = useState("#");

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    const fd = new FormData(e.currentTarget);
    const orderId = String(fd.get("orderId"));
    const phone = String(fd.get("phone"));
    try {
      const data = await api<TrackResponse>(
        `/api/v1/track?orderId=${encodeURIComponent(orderId)}&phone=${encodeURIComponent(phone)}`
      );
      setResult(data);
      const wa = await api<{ url: string }>(
        `/api/v1/whatsapp/link?context=ORDER&orderCode=${encodeURIComponent(data.publicCode)}&phone=${encodeURIComponent(phone)}`
      );
      setWaUrl(wa.url);
    } catch (err) {
      setResult(null);
      setError(err instanceof Error ? err.message : "Not found");
    }
  }

  return (
    <section className="container section">
      <div className="container-mid" style={{ width: "100%", padding: 0 }}>
        <div className="page-header">
          <span className="section-label">Orders</span>
          <h2>Track your suit</h2>
          <p className="lead">Enter your order code and WhatsApp phone number.</p>
        </div>
        <form className="panel form" onSubmit={onSubmit} style={{ maxWidth: 720 }}>
          <label>
            Order code
            <input name="orderId" placeholder="JH-2026-12345" required autoComplete="off" />
          </label>
          <label>
            Phone
            <input name="phone" placeholder="03001234567" required autoComplete="tel" />
          </label>
          {error && <div className="error">{error}</div>}
          <button className="btn btn-primary">Track</button>
        </form>

        {result && (
          <div className="panel" style={{ marginTop: "1.25rem", maxWidth: 720 }}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem", flexWrap: "wrap" }}>
              <h3 style={{ margin: 0 }}>{result.publicCode}</h3>
              <span className="status-pill">{result.status}</span>
            </div>
            <ul className="timeline" style={{ marginTop: "1rem" }}>
              {result.timeline.map((t, idx) => (
                <li key={idx}>
                  <strong>{t.toStatus}</strong>
                  <span className="muted">{t.note}</span>
                  <span className="muted">{new Date(t.createdAt).toLocaleString()}</span>
                </li>
              ))}
            </ul>
            <a className="btn btn-wa" href={waUrl} target="_blank" rel="noreferrer" style={{ marginTop: "1rem" }}>
              WhatsApp support
            </a>
          </div>
        )}
      </div>
    </section>
  );
}
