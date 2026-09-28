"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const DESTINATIONS = [
  { href: "/admin", label: "Dashboard" },
  { href: "/admin/orders/new", label: "New Order" },
  { href: "/admin/orders", label: "All Orders" },
  { href: "/admin/customers", label: "Search Customer" },
  { href: "/admin/designs", label: "Designs" },
  { href: "/admin/fabrics", label: "Fabrics" },
  { href: "/admin/payments", label: "Payments" },
  { href: "/admin/reports", label: "Reports" },
  { href: "/admin/branches", label: "Branches" },
  { href: "/admin/users", label: "Users" },
  { href: "/admin/inventory", label: "Inventory" },
  { href: "/admin/settings", label: "Settings" },
];

export function AdminNav() {
  const pathname = usePathname();

  return (
    <nav className="admin-nav no-print" aria-label="Admin sections">
      {DESTINATIONS.map((d) => {
        const active = d.href === "/admin" ? pathname === "/admin" : pathname.startsWith(d.href);
        return (
          <Link key={d.href} href={d.href} className={`admin-nav-tab${active ? " active" : ""}`}>
            {d.label}
          </Link>
        );
      })}
    </nav>
  );
}
