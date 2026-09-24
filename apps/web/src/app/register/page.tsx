"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { apiFetch, ApiError } from "@/lib/api";
import type { BranchResponse } from "@/lib/types";

export default function RegisterPage() {
  const { register } = useAuth();
  const router = useRouter();
  const [branches, setBranches] = useState<BranchResponse[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    apiFetch<BranchResponse[]>("/branches")
      .then((data) => setBranches(data.filter((b) => b.active)))
      .catch(() => setBranches([]));
  }, []);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setLoading(true);
    const fd = new FormData(e.currentTarget);
    const branchId = String(fd.get("branchId") || "");
    try {
      await register(String(fd.get("fullName")), String(fd.get("email")), String(fd.get("password")), branchId ? Number(branchId) : null);
      router.push("/");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="auth-shell">
      <div className="container-narrow">
        <div className="page-header">
          <span className="section-label">Account</span>
          <h2>Create your account</h2>
          <p className="lead">Register once — then measure, order, and track with ease.</p>
        </div>
        <form className="panel form" onSubmit={onSubmit}>
          <label>
            Full name
            <input name="fullName" required autoComplete="name" />
          </label>
          <label>
            Email
            <input name="email" type="email" required autoComplete="email" />
          </label>
          <label>
            Password
            <input name="password" type="password" required minLength={8} autoComplete="new-password" />
          </label>
          {branches.length > 0 && (
            <label>
              Home branch (optional)
              <select name="branchId" defaultValue="">
                <option value="">No preference</option>
                {branches.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          {error && <div className="error">{error}</div>}
          <button className="btn btn-primary" disabled={loading}>
            {loading ? "Please wait…" : "Register"}
          </button>
        </form>
      </div>
    </section>
  );
}
