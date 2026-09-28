"use client";

import { FormEvent, useEffect, useState } from "react";
import { api, type Branch } from "@/lib/api";
import { AdminNav } from "@/components/AdminNav";

export default function AdminBranchesPage() {
  const [branches, setBranches] = useState<Branch[]>([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);

  function load() {
    api<Branch[]>("/api/v1/admin/branches")
      .then(setBranches)
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load branches"));
  }

  useEffect(() => {
    load();
  }, []);

  async function onCreate(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setMessage("");
    setSubmitting(true);
    const fd = new FormData(e.currentTarget);
    try {
      await api("/api/v1/admin/branches", {
        method: "POST",
        body: JSON.stringify({
          name: fd.get("name"),
          address: fd.get("address") || null,
          phone: fd.get("phone") || null,
        }),
      });
      setMessage("Branch added.");
      e.currentTarget.reset();
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not add branch");
    } finally {
      setSubmitting(false);
    }
  }

  async function toggleActive(branch: Branch) {
    setError("");
    try {
      await api(`/api/v1/admin/branches/${branch.id}`, {
        method: "PUT",
        body: JSON.stringify({
          name: branch.name,
          address: branch.address,
          phone: branch.phone,
          active: !branch.active,
        }),
      });
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update branch");
    }
  }

  return (
    <section className="container section">
      <AdminNav />
      <div className="page-header">
        <span className="section-label">Administration</span>
        <h2>Branches</h2>
        <p className="lead">Manage shop locations used for order creation and dispatch.</p>
      </div>

      {error && <div className="error">{error}</div>}
      {message && <p className="success">{message}</p>}

      <div className="pos-grid">
        <div>
          <h3>Locations</h3>
          <div className="list-stack">
            {branches.map((b) => (
              <div key={b.id} className="panel">
                <div className="list-row" style={{ borderBottom: "none", paddingTop: 0 }}>
                  <div>
                    <strong>{b.name}</strong>
                    <div className="muted">
                      {b.address || "No address"} - {b.phone || "No phone"}
                    </div>
                  </div>
                  <span className="status-pill">{b.active ? "Active" : "Inactive"}</span>
                </div>
                <button type="button" className="btn btn-ghost" onClick={() => toggleActive(b)}>
                  {b.active ? "Deactivate" : "Activate"}
                </button>
              </div>
            ))}
            {branches.length === 0 && <p className="muted">No branches yet.</p>}
          </div>
        </div>

        <div>
          <h3>Add branch</h3>
          <form className="panel form" onSubmit={onCreate}>
            <label>
              Name
              <input name="name" required placeholder="Main Branch" />
            </label>
            <label>
              Address
              <input name="address" placeholder="Shahrah-e-Faisal, Karachi" />
            </label>
            <label>
              Phone
              <input name="phone" placeholder="021-1234567" />
            </label>
            <button className="btn btn-primary" disabled={submitting}>
              {submitting ? "Saving…" : "Add branch"}
            </button>
          </form>
        </div>
      </div>
    </section>
  );
}
