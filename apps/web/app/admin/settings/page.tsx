"use client";

import { FormEvent, useEffect, useState } from "react";
import { api, formatPkr, type Branch, type DispatchCostSettings } from "@/lib/api";
import { AdminNav } from "@/components/AdminNav";

export default function AdminSettingsPage() {
  const [branches, setBranches] = useState<Branch[]>([]);
  const [settings, setSettings] = useState<DispatchCostSettings | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);

  function load() {
    Promise.all([
      api<Branch[]>("/api/v1/admin/branches"),
      api<DispatchCostSettings>("/api/v1/admin/dispatch-costs"),
    ])
      .then(([b, s]) => {
        setBranches(b);
        setSettings(s);
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load settings"));
  }

  useEffect(() => {
    load();
  }, []);

  async function onSaveRule(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setMessage("");
    setSubmitting(true);
    const fd = new FormData(e.currentTarget);
    try {
      await api("/api/v1/admin/dispatch-costs", {
        method: "POST",
        body: JSON.stringify({
          sourceBranchId: Number(fd.get("sourceBranchId")),
          destinationBranchId: Number(fd.get("destinationBranchId")),
          costPaisa: Math.round(Number(fd.get("costPkr")) * 100),
        }),
      });
      setMessage("Dispatch cost rule saved.");
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save rule");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="container section">
      <AdminNav />
      <div className="page-header">
        <span className="section-label">Administration</span>
        <h2>Settings</h2>
        <p className="lead">Configure branch-to-branch dispatch costs.</p>
      </div>

      {error && <div className="error">{error}</div>}
      {message && <p className="success">{message}</p>}

      {settings && (
        <p className="muted" style={{ marginBottom: "1rem" }}>
          Default dispatch cost (used when no specific rule matches):{" "}
          <strong>{formatPkr(settings.defaultCostPaisa)}</strong> — set via the{" "}
          <code>DEFAULT_DISPATCH_COST_PAISA</code> environment variable.
        </p>
      )}

      <div className="pos-grid">
        <div>
          <h3>Rules</h3>
          <table>
            <thead>
              <tr>
                <th>From</th>
                <th>To</th>
                <th className="num">Cost</th>
              </tr>
            </thead>
            <tbody>
              {settings?.rules.map((r) => (
                <tr key={r.id}>
                  <td>{r.sourceBranchName}</td>
                  <td>{r.destinationBranchName}</td>
                  <td className="num">{formatPkr(r.costPaisa)}</td>
                </tr>
              ))}
              {settings && settings.rules.length === 0 && (
                <tr>
                  <td colSpan={3} className="muted">
                    No branch-pair rules yet — the default applies everywhere.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div>
          <h3>Add / update a rule</h3>
          <form className="panel form" onSubmit={onSaveRule}>
            <label>
              From branch
              <select name="sourceBranchId" required>
                {branches.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              To branch
              <select name="destinationBranchId" required>
                {branches.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Cost (PKR)
              <input name="costPkr" type="number" min={0} step="1" required placeholder="300" />
            </label>
            <button className="btn btn-primary" disabled={submitting}>
              {submitting ? "Saving…" : "Save rule"}
            </button>
          </form>
        </div>
      </div>
    </section>
  );
}
