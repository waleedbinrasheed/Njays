"use client";

import Link from "next/link";
import { useEffect, useState, useCallback } from "react";
import { usePathname, useRouter } from "next/navigation";

type HeaderUser = {
  fullName?: string;
  role?: string;
};

export function SiteHeader() {
  const router = useRouter();
  const pathname = usePathname();
  const [ready, setReady] = useState(false);
  const [loggedIn, setLoggedIn] = useState(false);
  const [name, setName] = useState("");
  const [role, setRole] = useState("");
  const [menuOpen, setMenuOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  const refreshAuth = useCallback(() => {
    const token = localStorage.getItem("accessToken");
    setLoggedIn(!!token);
    try {
      const user = JSON.parse(localStorage.getItem("user") || "{}") as HeaderUser;
      setName(user.fullName || "");
      setRole(user.role || "");
    } catch {
      setName("");
      setRole("");
    }
  }, []);

  useEffect(() => {
    refreshAuth();
    setReady(true);
    setMenuOpen(false);
    const onStorage = () => refreshAuth();
    const onAuth = () => refreshAuth();
    window.addEventListener("storage", onStorage);
    window.addEventListener("menswear-auth", onAuth);
    return () => {
      window.removeEventListener("storage", onStorage);
      window.removeEventListener("menswear-auth", onAuth);
    };
  }, [pathname, refreshAuth]);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setMenuOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [menuOpen]);

  useEffect(() => {
    document.body.style.overflow = menuOpen ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [menuOpen]);

  function logout() {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("refreshToken");
    localStorage.removeItem("user");
    setLoggedIn(false);
    setName("");
    setRole("");
    setMenuOpen(false);
    window.dispatchEvent(new Event("menswear-auth"));
    router.push("/");
  }

  const isAdmin = ready && (role === "ADMIN" || role === "STAFF");
  const showLoggedIn = ready && loggedIn;

  function navClass(href: string) {
    const active = href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
    return active ? "active" : undefined;
  }

  return (
    <header className={`site-header${scrolled ? " is-scrolled" : ""}`}>
      <div className="container nav">
        <Link href="/" className="brand" aria-label="NJAY'S by S.A.R home" onClick={() => setMenuOpen(false)}>
          <img src="/logo.png" alt="NJAY'S by S.A.R" className="brand-logo" />
          <span className="brand-text">
            <strong>NJAY&apos;S</strong>
            <span>BY S.A.R</span>
          </span>
        </Link>

        <button
          type="button"
          className="nav-toggle"
          aria-expanded={menuOpen}
          aria-controls="primary-nav"
          aria-label={menuOpen ? "Close menu" : "Open menu"}
          onClick={() => setMenuOpen((o) => !o)}
        >
          <span className="nav-toggle-bars" aria-hidden>
            <span />
            <span />
            <span />
          </span>
        </button>

        <nav id="primary-nav" className={`nav-links${menuOpen ? " is-open" : ""}`} aria-label="Primary">
          <Link href="/shop" className={navClass("/shop")} onClick={() => setMenuOpen(false)}>
            Shop
          </Link>
          <Link href="/track" className={navClass("/track")} onClick={() => setMenuOpen(false)}>
            Track
          </Link>
          <Link
            href="/account/measurements"
            className={navClass("/account/measurements")}
            onClick={() => setMenuOpen(false)}
          >
            Measure
          </Link>
          <Link href="/cart" className={navClass("/cart")} onClick={() => setMenuOpen(false)}>
            Cart
          </Link>
          {showLoggedIn && (
            <Link href="/account/orders" className={navClass("/account/orders")} onClick={() => setMenuOpen(false)}>
              Orders
            </Link>
          )}
          {isAdmin && (
            <>
              <Link href="/admin" className={navClass("/admin")} onClick={() => setMenuOpen(false)}>
                Admin
              </Link>
              <Link href="/admin/products" className={navClass("/admin/products")} onClick={() => setMenuOpen(false)}>
                Add dress
              </Link>
            </>
          )}
          {showLoggedIn ? (
            <>
              <span className="header-user" title={name}>
                Hi, {name || "User"}
              </span>
              <button type="button" className="btn btn-ghost" onClick={logout}>
                Sign out
              </button>
            </>
          ) : (
            <Link href="/login" className="btn btn-primary" onClick={() => setMenuOpen(false)}>
              Sign in
            </Link>
          )}
        </nav>
      </div>
      {menuOpen && (
        <button
          type="button"
          className="nav-backdrop"
          aria-label="Close menu"
          onClick={() => setMenuOpen(false)}
        />
      )}
    </header>
  );
}
