"use client";

import { useAuth } from "@/context/AuthContext";

export default function HomePage() {
  const { user, loading } = useAuth();

  return (
    <div className="card">
      <h1>Menswear tailoring</h1>
      {loading ? (
        <p>Loading…</p>
      ) : user ? (
        <p>Welcome back, {user.fullName}.</p>
      ) : (
        <p>Sign in or create an account to get started.</p>
      )}
    </div>
  );
}
