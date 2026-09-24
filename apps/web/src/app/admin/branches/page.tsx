"use client";

import { useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import type { BranchResponse } from "@/lib/types";

export default function AdminBranchesPage() {
  const [branches, setBranches] = useState<BranchResponse[]>([]);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function load() {
    const data = await apiFetch<BranchResponse[]>("/branches");
    setBranches(data);
  }

  useEffect(() => {
    load().catch(() => setError("Could not load branches."));
  }, []);

  async function handleCreate(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setSubmitting(true);
    const form = e.currentTarget;
    const fd = new FormData(form);
    try {
      await apiFetch<BranchResponse>("/admin/branches", {
        method: "POST",
        body: JSON.stringify({
          name: fd.get("name"),
          address: fd.get("address") || null,
          phone: fd.get("phone") || null,
        }),
      });
      form.reset();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not create branch.");
    } finally {
      setSubmitting(false);
    }
  }

  async function toggleActive(branch: BranchResponse) {
    try {
      await apiFetch<BranchResponse>(`/admin/branches/${branch.id}`, {
        method: "PUT",
        body: JSON.stringify({
          name: branch.name,
          address: branch.address,
          phone: branch.phone,
          active: !branch.active,
        }),
      });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update branch.");
    }
  }

  return (
    <div style={{ display: "grid", gap: "1.5rem", gridTemplateColumns: "1.4fr 1fr" }}>
      <div className="panel">
        <h2 style={{ marginTop: 0 }}>Branches</h2>
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Address</th>
              <th>Phone</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {branches.map((b) => (
              <tr key={b.id}>
                <td>{b.name}</td>
                <td>{b.address ?? "—"}</td>
                <td>{b.phone ?? "—"}</td>
                <td>{b.active ? "Active" : "Inactive"}</td>
                <td>
                  <button className="btn btn-ghost" onClick={() => toggleActive(b)}>
                    {b.active ? "Deactivate" : "Activate"}
                  </button>
                </td>
              </tr>
            ))}
            {branches.length === 0 && (
              <tr>
                <td colSpan={5} className="muted">
                  No branches yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="panel">
        <h2 style={{ marginTop: 0 }}>Add branch</h2>
        <form className="form" onSubmit={handleCreate}>
          <label>
            Name
            <input name="name" required />
          </label>
          <label>
            Address
            <input name="address" />
          </label>
          <label>
            Phone
            <input name="phone" />
          </label>
          {error && <div className="error">{error}</div>}
          <button className="btn btn-primary" type="submit" disabled={submitting}>
            {submitting ? "Adding…" : "Add branch"}
          </button>
        </form>
      </div>
    </div>
  );
}
