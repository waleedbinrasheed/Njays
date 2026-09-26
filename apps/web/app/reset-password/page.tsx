"use client";

import Link from "next/link";
import { FormEvent, Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { api } from "@/lib/api";

function ResetPasswordForm() {
  const router = useRouter();
  const params = useSearchParams();
  const tokenFromUrl = params.get("token") || "";
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setMessage("");
    setLoading(true);
    const fd = new FormData(e.currentTarget);
    const newPassword = String(fd.get("newPassword"));
    const confirm = String(fd.get("confirmPassword"));
    const token = String(fd.get("token") || tokenFromUrl).trim();

    if (newPassword !== confirm) {
      setError("Passwords do not match");
      setLoading(false);
      return;
    }
    if (!token) {
      setError("Reset token is missing. Request a new link.");
      setLoading(false);
      return;
    }

    try {
      const res = await api<{ message: string }>("/api/v1/auth/reset-password", {
        method: "POST",
        body: JSON.stringify({ token, newPassword }),
      });
      setMessage(res.message);
      setTimeout(() => router.push("/login"), 1500);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Reset failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="auth-shell">
      <div className="container-narrow">
        <div className="page-header">
          <span className="section-label">Account</span>
          <h2>Reset password</h2>
          <p className="lead">Choose a new password for your NJAY&apos;S account.</p>
        </div>
        <form className="panel form" onSubmit={onSubmit}>
          {tokenFromUrl ? (
            <input type="hidden" name="token" value={tokenFromUrl} />
          ) : (
            <label>
              Reset token
              <input name="token" required placeholder="Paste token from email/link" />
            </label>
          )}
          <label>
            New password
            <input name="newPassword" type="password" minLength={8} required autoComplete="new-password" />
          </label>
          <label>
            Confirm password
            <input name="confirmPassword" type="password" minLength={8} required autoComplete="new-password" />
          </label>
          {error && <div className="error">{error}</div>}
          {message && <p className="success">{message}</p>}
          <button className="btn btn-primary" disabled={loading}>
            {loading ? "Updating…" : "Update password"}
          </button>
          <Link href="/login" className="btn btn-ghost">
            Back to sign in
          </Link>
        </form>
      </div>
    </section>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense
      fallback={
        <div className="container section">
          <div className="skeleton" aria-busy="true" aria-label="Loading" />
        </div>
      }
    >
      <ResetPasswordForm />
    </Suspense>
  );
}
