"use client";

import { useEffect, useState, type FormEvent } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import type { BranchResponse } from "@/lib/types";

export default function AdminBranchesPage() {
  const [branches, setBranches] = useState<BranchResponse[]>([]);
  const [name, setName] = useState("");
  const [address, setAddress] = useState("");
  const [phone, setPhone] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function load() {
    const data = await apiFetch<BranchResponse[]>("/branches");
    setBranches(data);
  }

  useEffect(() => {
    load().catch(() => setError("Could not load branches."));
  }, []);

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await apiFetch<BranchResponse>("/admin/branches", {
        method: "POST",
        body: JSON.stringify({ name, address: address || null, phone: phone || null }),
      });
      setName("");
      setAddress("");
      setPhone("");
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
    <div style={{ display: "grid", gap: 24, gridTemplateColumns: "1fr 320px" }}>
      <div className="card">
        <h2>Branches</h2>
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
                  <button className="btn-secondary" onClick={() => toggleActive(b)}>
                    {b.active ? "Deactivate" : "Activate"}
                  </button>
                </td>
              </tr>
            ))}
            {branches.length === 0 && (
              <tr>
                <td colSpan={5}>No branches yet.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="card">
        <h2>Add branch</h2>
        {error && <p className="error-text">{error}</p>}
        <form onSubmit={handleCreate}>
          <div className="form-field">
            <label htmlFor="name">Name</label>
            <input id="name" required value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="form-field">
            <label htmlFor="address">Address</label>
            <input id="address" value={address} onChange={(e) => setAddress(e.target.value)} />
          </div>
          <div className="form-field">
            <label htmlFor="phone">Phone</label>
            <input id="phone" value={phone} onChange={(e) => setPhone(e.target.value)} />
          </div>
          <button className="btn-primary" type="submit" disabled={submitting} style={{ width: "100%" }}>
            {submitting ? "Adding…" : "Add branch"}
          </button>
        </form>
      </div>
    </div>
  );
}
