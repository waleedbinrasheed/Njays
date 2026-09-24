"use client";

import Link from "next/link";
import { useAuth } from "@/context/AuthContext";

export default function HomePage() {
  const { user, loading } = useAuth();

  return (
    <section className="hero-bleed" aria-label="NJAY'S made to measure">
      <div className="container hero-bleed__content">
        <img src="/logo.png" alt="NJAY'S by S.A.R" className="hero-brand" />
        <h1>Tailored with care</h1>
        {!loading && user ? (
          <p className="hero-copy">Welcome back, {user.fullName}.</p>
        ) : (
          <>
            <p className="hero-copy">
              Made-to-measure menswear from NJAY&apos;S — select your fabric, choose your stitching style, and
              we&apos;ll take care of the rest.
            </p>
            <div className="hero-actions">
              <Link href="/register" className="btn btn-primary">
                Create account
              </Link>
              <Link href="/login" className="btn btn-ghost">
                Sign in
              </Link>
            </div>
          </>
        )}
      </div>
    </section>
  );
}
