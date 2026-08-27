"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, type User } from "@/lib/api";

type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  user: User;
};

export default function LoginPage() {
  const router = useRouter();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setLoading(true);
    const fd = new FormData(e.currentTarget);
    const body =
      mode === "login"
        ? {
            email: String(fd.get("email")),
            password: String(fd.get("password")),
          }
        : {
            email: String(fd.get("email")),
            password: String(fd.get("password")),
            fullName: String(fd.get("fullName")),
            phone: String(fd.get("phone")),
          };

    try {
      const res = await api<AuthResponse>(`/api/v1/auth/${mode}`, {
        method: "POST",
        body: JSON.stringify(body),
      });
      localStorage.setItem("accessToken", res.accessToken);
      localStorage.setItem("refreshToken", res.refreshToken);
      localStorage.setItem("user", JSON.stringify(res.user));
      window.dispatchEvent(new Event("menswear-auth"));
      if (res.user.role === "ADMIN" || res.user.role === "STAFF") {
        router.push("/admin");
      } else {
        router.push("/shop");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Auth failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="auth-shell">
      <div className="container-narrow">
        <div className="page-header">
          <span className="section-label">Account</span>
          <h2>{mode === "login" ? "Welcome back" : "Create your account"}</h2>
          <p className="lead">
            {mode === "login"
              ? "Sign in for measurements, checkout, and order history."
              : "Register once — then measure, order, and track with ease."}
          </p>
        </div>
        <form className="panel form" onSubmit={onSubmit}>
          {mode === "register" && (
            <>
              <label>
                Full name
                <input name="fullName" required autoComplete="name" />
              </label>
              <label>
                Phone (03xx…)
                <input name="phone" placeholder="03001234567" autoComplete="tel" />
              </label>
            </>
          )}
          <label>
            Email
            <input name="email" type="email" required autoComplete="email" />
          </label>
          <label>
            Password
            <input name="password" type="password" minLength={8} required autoComplete={mode === "login" ? "current-password" : "new-password"} />
          </label>
          {mode === "login" && (
            <Link href="/forgot-password" className="link-subtle">
              Forgot password?
            </Link>
          )}
          {error && <div className="error">{error}</div>}
          <button className="btn btn-primary" disabled={loading}>
            {loading ? "Please wait…" : mode === "login" ? "Sign in" : "Register"}
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => setMode(mode === "login" ? "register" : "login")}
          >
            {mode === "login" ? "Need an account? Register" : "Have an account? Sign in"}
          </button>
        </form>
      </div>
    </section>
  );
}
