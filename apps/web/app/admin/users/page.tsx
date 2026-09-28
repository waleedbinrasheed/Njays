"use client";

import { AdminNav } from "@/components/AdminNav";

export default function AdminUsersPage() {
  return (
    <section className="container section">
      <AdminNav />
      <div className="page-header">
        <span className="section-label">Administration</span>
        <h2>Users</h2>
        <p className="lead">Staff account management isn't built yet — this is a placeholder for a future requirement.</p>
      </div>
      <div className="panel">
        <p className="muted">
          Once a scope is defined, this section will list staff accounts, their roles, and let you assign each one to
          a branch (so their in-shop orders auto-fill the created-at branch).
        </p>
      </div>
    </section>
  );
}
