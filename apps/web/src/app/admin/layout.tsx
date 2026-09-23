"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/context/AuthContext";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!loading && (!user || user.role !== "ADMIN")) {
      router.replace("/login");
    }
  }, [loading, user, router]);

  if (loading || !user || user.role !== "ADMIN") {
    return <p>Loading…</p>;
  }

  return (
    <div>
      <div className="tabs">
        <Link href="/admin/branches" className={`tab ${pathname?.startsWith("/admin/branches") ? "active" : ""}`}>
          Branches
        </Link>
        <Link href="/admin/users" className={`tab ${pathname?.startsWith("/admin/users") ? "active" : ""}`}>
          Users
        </Link>
      </div>
      {children}
    </div>
  );
}
