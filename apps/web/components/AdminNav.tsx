"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const DESTINATIONS = [
  { href: "/admin", label: "Orders" },
  { href: "/admin/orders/new", label: "Create Order" },
  { href: "/admin/fabrics", label: "Fabrics" },
  { href: "/admin/products", label: "Products" },
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
