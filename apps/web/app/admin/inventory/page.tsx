"use client";

import { AdminNav } from "@/components/AdminNav";

export default function AdminInventoryPage() {
  return (
    <section className="container section">
      <AdminNav />
      <div className="page-header">
        <span className="section-label">Administration</span>
        <h2>Inventory</h2>
        <p className="lead">Stock tracking isn't built yet — this is a placeholder for a future requirement.</p>
      </div>
      <div className="panel">
        <p className="muted">
          Once a scope is defined for fabric/material stock levels, this section will list current inventory and
          low-stock alerts.
        </p>
      </div>
    </section>
  );
}
