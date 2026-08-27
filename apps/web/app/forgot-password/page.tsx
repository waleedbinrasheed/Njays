"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { api } from "@/lib/api";

export default function ForgotPasswordPage() {
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [resetLink, setResetLink] = useState("");
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setMessage("");
    setResetLink("");
    setLoading(true);
    const fd = new FormData(e.currentTarget);
    try {
      const res = await api<{ message: string; resetLink?: string | null }>("/api/v1/auth/forgot-password", {
        method: "POST",
        body: JSON.stringify({ email: String(fd.get("email")) }),
      });
      setMessage(res.message);
      if (res.resetLink) {
        setResetLink(res.resetLink);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Request failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="auth-shell">
      <div className="container-narrow">
        <div className="page-header">
          <span className="section-label">Account</span>
          <h2>Forgot password</h2>
          <p className="lead">Enter your account email and we&apos;ll create a reset link.</p>
        </div>
        <form className="panel form" onSubmit={onSubmit}>
          <label>
            Email
            <input name="email" type="email" required placeholder="you@example.com" autoComplete="email" />
          </label>
          {error && <div className="error">{error}</div>}
          {message && <p className="success">{message}</p>}
          {resetLink && (
            <div className="section-card">
              <strong>Dev reset link</strong>
              <p className="muted" style={{ marginBottom: "0.7rem" }}>
                Email is not configured yet, so use this link to reset now:
              </p>
              <Link href={resetLink.replace(/^https?:\/\/[^/]+/, "")} className="btn btn-primary">
                Open reset page
              </Link>
            </div>
          )}
          <button className="btn btn-primary" disabled={loading}>
            {loading ? "Sending…" : "Send reset link"}
          </button>
          <Link href="/login" className="btn btn-ghost">
            Back to sign in
          </Link>
        </form>
      </div>
    </section>
  );
}
