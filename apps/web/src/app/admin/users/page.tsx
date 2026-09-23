"use client";

import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { useAuth } from "@/context/AuthContext";
import type { UserSummary } from "@/lib/types";

export default function AdminUsersPage() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    const data = await apiFetch<UserSummary[]>("/admin/users");
    setUsers(data);
  }

  useEffect(() => {
    load().catch(() => setError("Could not load users."));
  }, []);

  async function toggleEnabled(u: UserSummary) {
    setError(null);
    try {
      await apiFetch<UserSummary>(`/admin/users/${u.id}/enabled`, {
        method: "PATCH",
        body: JSON.stringify({ enabled: !u.enabled }),
      });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update user.");
    }
  }

  return (
    <div className="card">
      <h2>Users</h2>
      {error && <p className="error-text">{error}</p>}
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Email</th>
            <th>Role</th>
            <th>Branch</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id}>
              <td>{u.fullName}</td>
              <td>{u.email}</td>
              <td>{u.role}</td>
              <td>{u.branchId ?? "—"}</td>
              <td>{u.enabled ? "Enabled" : "Disabled"}</td>
              <td>
                <button
                  className="btn-secondary"
                  onClick={() => toggleEnabled(u)}
                  disabled={u.id === currentUser?.id && u.enabled}
                  title={u.id === currentUser?.id && u.enabled ? "You can't disable your own account" : undefined}
                >
                  {u.enabled ? "Disable" : "Enable"}
                </button>
              </td>
            </tr>
          ))}
          {users.length === 0 && (
            <tr>
              <td colSpan={6}>No users yet.</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
