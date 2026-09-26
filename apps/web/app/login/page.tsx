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
  const [showPassword, setShowPassword] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setLoading(true);
    const fd = new FormData(e.currentTarget);
    const body =
      mode === "login"
        ? {
            identifier: String(fd.get("identifier")),
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
          {mode === "login" ? (
            <label>
              Email or Mobile Number
              <input name="identifier" type="text" required autoComplete="username" placeholder="you@example.com or 03001234567" />
            </label>
          ) : (
            <label>
              Email
              <input name="email" type="email" required autoComplete="email" />
            </label>
          )}
          <label>
            Password
            <div className="password-field">
              <input
                id="password-input"
                name="password"
                type={showPassword ? "text" : "password"}
                minLength={8}
                required
                autoComplete={mode === "login" ? "current-password" : "new-password"}
              />
              <button
                type="button"
                className="password-toggle"
                aria-label={showPassword ? "Hide password" : "Show password"}
                aria-pressed={showPassword}
                aria-controls="password-input"
                title={showPassword ? "Hide password" : "Show password"}
                onClick={() => setShowPassword((v) => !v)}
              >
                {showPassword ? (
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M12 6c-5 0-9.27 3.11-11 7.5 1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5C21.27 9.11 17 6 12 6zm0 12.5a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-8a3 3 0 1 0 0 6 3 3 0 0 0 0-6z" />
                  </svg>
                ) : (
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M2 4.27 3.28 3 21 20.72 19.73 22l-3.08-3.08c-1.44.53-3 .83-4.65.83-5 0-9.27-3.11-11-7.5a11.83 11.83 0 0 1 4.36-5.44L2 4.27zM12 8a5.99 5.99 0 0 1 6 6c0 .78-.16 1.51-.44 2.19l-1.5-1.5c.1-.22.15-.45.15-.69a3.7 3.7 0 0 0-.06-.4l-3.2-3.2a3.7 3.7 0 0 0-.4-.06 2.98 2.98 0 0 0-.69.15l-1.5-1.5A5.94 5.94 0 0 1 12 8zm-.28-4c5 0 9.27 3.11 11 7.5a11.8 11.8 0 0 1-2.44 3.8l-1.44-1.44a9.8 9.8 0 0 0 1.85-2.36c-1.4-3.19-4.94-5.5-8.97-5.5-1.06 0-2.08.16-3.03.46L7.24 4.55A11.9 11.9 0 0 1 11.72 4zM4.51 6.34l1.5 1.5A9.79 9.79 0 0 0 3.9 10a9.66 9.66 0 0 0 8.1 5.5c.5 0 .98-.04 1.45-.1l1.62 1.62c-1 .3-2.05.48-3.17.48-5 0-9.27-3.11-11-7.5a11.87 11.87 0 0 1 3.6-3.66z" />
                  </svg>
                )}
              </button>
            </div>
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
