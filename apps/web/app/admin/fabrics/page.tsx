"use client";

import { FormEvent, useEffect, useState } from "react";
import { api, formatPkr, type FabricTier } from "@/lib/api";
import { AdminNav } from "@/components/AdminNav";

export default function AdminFabricsPage() {
  const [tiers, setTiers] = useState<FabricTier[]>([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const [colorFormOpenFor, setColorFormOpenFor] = useState<number | null>(null);

  function load() {
    api<FabricTier[]>("/api/v1/fabrics")
      .then(setTiers)
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load fabrics"));
  }

  useEffect(() => {
    load();
  }, []);

  async function onCreateTier(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setMessage("");
    setLoading(true);
    const fd = new FormData(e.currentTarget);
    try {
      await api("/api/v1/admin/fabrics/tiers", {
        method: "POST",
        body: JSON.stringify({
          code: String(fd.get("code")).trim().toUpperCase(),
          name: String(fd.get("name")).trim(),
          surchargePaisa: Math.round(Number(fd.get("surchargePkr")) * 100),
          sortOrder: Number(fd.get("sortOrder") || 0),
        }),
      });
      setMessage("Fabric tier added.");
      e.currentTarget.reset();
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not add fabric tier");
    } finally {
      setLoading(false);
    }
  }

  async function onCreateColor(e: FormEvent<HTMLFormElement>, tierId: number) {
    e.preventDefault();
    setError("");
    setMessage("");
    setLoading(true);
    const fd = new FormData(e.currentTarget);
    try {
      await api(`/api/v1/admin/fabrics/tiers/${tierId}/colors`, {
        method: "POST",
        body: JSON.stringify({
          code: String(fd.get("code")).trim().toUpperCase(),
          name: String(fd.get("name")).trim(),
          hexColor: String(fd.get("hexColor") || ""),
        }),
      });
      setMessage("Color added.");
      setColorFormOpenFor(null);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not add color");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="container section">
      <AdminNav />
      <div className="page-header">
        <span className="section-label">Studio</span>
        <h2>Fabrics</h2>
        <p className="lead">Add fabric tiers and their colors — used for made-to-measure surcharges and swatches.</p>
      </div>

      {error && <div className="error">{error}</div>}
      {message && <p className="success">{message}</p>}

      <div className="pos-grid">
        <div>
          <h3>New fabric tier</h3>
          <form className="panel form" onSubmit={onCreateTier}>
            <div className="form-row">
              <label>
                Code
                <input name="code" required placeholder="PREMIUM" />
              </label>
              <label>
                Name
                <input name="name" required placeholder="Premium Cotton" />
              </label>
            </div>
            <div className="form-row">
              <label>
                Surcharge (PKR)
                <input name="surchargePkr" type="number" min={0} step="1" required placeholder="500" />
              </label>
              <label>
                Sort order
                <input name="sortOrder" type="number" min={0} step="1" placeholder="0" />
              </label>
            </div>
            <button className="btn btn-primary" disabled={loading}>
              {loading ? "Saving…" : "Add fabric tier"}
            </button>
          </form>
        </div>

        <div>
          <h3>Existing tiers</h3>
          <div className="list-stack">
            {tiers.map((tier) => (
              <div key={tier.id} className="panel">
                <div className="list-row" style={{ borderBottom: "none", paddingTop: 0 }}>
                  <div>
                    <strong>{tier.name}</strong>
                    <div className="muted">
                      Code {tier.code} · +{formatPkr(tier.surchargePaisa)}
                    </div>
                  </div>
                </div>

                <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem", margin: "0.5rem 0" }}>
                  {tier.colors.map((c) => (
                    <span key={c.id} className="fabric-color-card">
                      <span className="swatch" style={{ background: c.hexColor || "#ccc" }} />
                      {c.name} ({c.code})
                    </span>
                  ))}
                  {tier.colors.length === 0 && <span className="muted">No colors yet.</span>}
                </div>

                {colorFormOpenFor === tier.id ? (
                  <form className="form" onSubmit={(e) => onCreateColor(e, tier.id)}>
                    <div className="form-row">
                      <label>
                        Color code
                        <input name="code" required placeholder="NV1" />
                      </label>
                      <label>
                        Color name
                        <input name="name" required placeholder="Navy" />
                      </label>
                    </div>
                    <label>
                      Hex color
                      <input name="hexColor" type="color" defaultValue="#1a2b3c" style={{ height: 44, padding: 4 }} />
                    </label>
                    <div className="form-actions">
                      <button className="btn btn-primary" disabled={loading}>
                        {loading ? "Saving…" : "Add color"}
                      </button>
                      <button type="button" className="btn btn-ghost" onClick={() => setColorFormOpenFor(null)}>
                        Cancel
                      </button>
                    </div>
                  </form>
                ) : (
                  <button type="button" className="btn btn-ghost" onClick={() => setColorFormOpenFor(tier.id)}>
                    + Add color
                  </button>
                )}
              </div>
            ))}
            {tiers.length === 0 && <p className="muted">No fabric tiers yet.</p>}
          </div>
        </div>
      </div>
    </section>
  );
}
