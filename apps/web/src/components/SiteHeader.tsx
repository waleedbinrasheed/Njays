"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";

export function SiteHeader() {
  const { user, loading, logout } = useAuth();
  const router = useRouter();

  async function handleLogout() {
    await logout();
    router.push("/");
  }

  return (
    <header className="site-header">
      <div className="container nav">
        <Link href="/" className="brand" aria-label="NJAY'S by S.A.R home">
          <img src="/logo.png" alt="NJAY'S by S.A.R" className="brand-logo" />
          <span className="brand-text">
            <strong>NJAY&apos;S</strong>
            <span>BY S.A.R</span>
          </span>
        </Link>

        <nav className="nav-links" aria-label="Primary">
          {!loading && user?.role === "ADMIN" && <Link href="/admin/branches">Admin</Link>}
          {!loading &&
            (user ? (
              <>
                <span className="header-user" title={user.fullName}>
                  Hi, {user.fullName}
                </span>
                <button type="button" className="btn btn-ghost" onClick={handleLogout}>
                  Sign out
                </button>
              </>
            ) : (
              <Link href="/login" className="btn btn-primary">
                Sign in
              </Link>
            ))}
        </nav>
      </div>
    </header>
  );
}
