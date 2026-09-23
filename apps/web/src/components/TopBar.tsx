"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";

export function TopBar() {
  const { user, loading, logout } = useAuth();
  const router = useRouter();

  async function handleLogout() {
    await logout();
    router.push("/");
  }

  return (
    <div className="container">
      <div className="topbar">
        <Link href="/" className="brand">
          Menswear
        </Link>
        <div className="nav-links">
          {loading ? null : user ? (
            <>
              {user.role === "ADMIN" && <Link href="/admin/branches">Admin</Link>}
              <span style={{ color: "var(--color-muted)" }}>{user.fullName}</span>
              <button className="btn-secondary" onClick={handleLogout}>
                Sign out
              </button>
            </>
          ) : (
            <>
              <Link href="/login">Sign in</Link>
              <Link href="/register">
                <button className="btn-primary">Create account</button>
              </Link>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
