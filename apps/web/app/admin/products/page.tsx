"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, apiForm, formatPkr } from "@/lib/api";

type Category = { id: number; name: string; slug: string };

export default function AdminProductsPage() {
  const router = useRouter();
  const [categories, setCategories] = useState<Category[]>([]);
  const [files, setFiles] = useState<File[]>([]);
  const [previews, setPreviews] = useState<string[]>([]);
  const [extraUrls, setExtraUrls] = useState<string[]>([""]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const [createdSlug, setCreatedSlug] = useState("");

  useEffect(() => {
    const user = JSON.parse(localStorage.getItem("user") || "{}");
    if (!localStorage.getItem("accessToken") || (user.role !== "ADMIN" && user.role !== "STAFF")) {
      router.push("/login");
      return;
    }
    api<Category[]>("/api/v1/categories")
      .then(setCategories)
      .catch((e) => setError(e.message));
  }, [router]);

  useEffect(() => {
    const urls = files.map((f) => URL.createObjectURL(f));
    setPreviews(urls);
    return () => urls.forEach((u) => URL.revokeObjectURL(u));
  }, [files]);

  function slugFromName(name: string) {
    return name
      .toLowerCase()
      .trim()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/(^-|-$)/g, "");
  }

  function onFilesChosen(list: FileList | null) {
    if (!list) return;
    const next = Array.from(list).filter((f) => f.type.startsWith("image/"));
    setFiles((prev) => [...prev, ...next]);
  }

  function removeFile(index: number) {
    setFiles((prev) => prev.filter((_, i) => i !== index));
  }

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setMessage("");
    setLoading(true);

    const formEl = e.currentTarget;
    const fd = new FormData(formEl);
    const name = String(fd.get("name") || "").trim();
    const slugInput = String(fd.get("slug") || "").trim();
    const slug = slugInput || slugFromName(name);
    const pricePkr = Number(fd.get("pricePkr"));
    const urls = extraUrls.map((u) => u.trim()).filter(Boolean);

    if (!name) {
      setError("Name is required");
      setLoading(false);
      return;
    }
    if (!pricePkr || pricePkr < 1) {
      setError("Enter a valid price in PKR");
      setLoading(false);
      return;
    }
    if (files.length === 0 && urls.length === 0) {
      setError("Upload at least one image file (or add an image URL)");
      setLoading(false);
      return;
    }

    try {
      const body = new FormData();
      body.append("name", name);
      body.append("slug", slug);
      body.append("description", String(fd.get("description") || ""));
      body.append("basePricePaisa", String(Math.round(pricePkr * 100)));
      const categoryId = String(fd.get("categoryId") || "");
      if (categoryId) body.append("categoryId", categoryId);
      body.append("supportsCustom", fd.get("supportsCustom") === "on" ? "true" : "false");
      body.append("active", "true");
      urls.forEach((url) => body.append("imageUrls", url));
      files.forEach((file) => body.append("images", file));

      const created = await apiForm<{ slug: string; name: string; basePricePaisa: number; images: unknown[] }>(
        "/api/v1/admin/products",
        body
      );

      setMessage(
        `Saved: ${created.name} (${formatPkr(created.basePricePaisa)}) with ${created.images?.length ?? 0} image(s)`
      );
      setCreatedSlug(created.slug);
      formEl.reset();
      setFiles([]);
      setExtraUrls([""]);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Create failed";
      if (/denied|unauthorized|session expired|403|401/i.test(msg)) {
        setError(`${msg} — sign out and sign in again as admin.`);
      } else {
        setError(msg);
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="container section">
      <div className="page-header" style={{ maxWidth: 760 }}>
        <span className="section-label">Studio</span>
        <h2>Add dress</h2>
        <p className="lead">
          Upload product images and publish to the shop.{" "}
          <Link href="/admin" className="link-subtle">
            Back to admin
          </Link>
        </p>
      </div>

      <form className="panel form" onSubmit={onSubmit} style={{ maxWidth: 760 }}>
        <label>
          Name
          <input
            name="name"
            required
            placeholder="Royal Navy Kameez Shalwar"
            onBlur={(e) => {
              const slugInput = e.currentTarget.form?.elements.namedItem("slug") as HTMLInputElement | null;
              if (slugInput && !slugInput.value) {
                slugInput.value = slugFromName(e.currentTarget.value);
              }
            }}
          />
        </label>
        <label>
          Slug (URL)
          <input name="slug" placeholder="royal-navy-kameez-shalwar" />
        </label>
        <label>
          Description
          <textarea name="description" rows={3} />
        </label>
        <div className="form-row">
          <label>
            Price (PKR)
            <input name="pricePkr" type="number" min={1} step="1" required placeholder="8500" />
          </label>
          <label>
            Category
            <select name="categoryId" defaultValue="">
              <option value="">Select category</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
        </div>
        <label style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
          <input name="supportsCustom" type="checkbox" defaultChecked />
          Supports made-to-measure
        </label>

        <h3 style={{ marginBottom: 0 }}>Upload images</h3>
        <p className="muted" style={{ marginTop: 0 }}>
          Select multiple files (JPG, PNG, WEBP, GIF). Max 10MB each.
        </p>
        <label>
          Choose photos
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif"
            multiple
            onChange={(e) => {
              onFilesChosen(e.target.files);
              e.target.value = "";
            }}
          />
        </label>

        {previews.length > 0 && (
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(96px, 1fr))",
              gap: "0.6rem",
            }}
          >
            {previews.map((src, index) => (
              <div key={src} style={{ position: "relative" }}>
                <img
                  src={src}
                  alt={`Preview ${index + 1}`}
                  style={{
                    width: "100%",
                    height: 96,
                    objectFit: "cover",
                    borderRadius: "0.6rem",
                    border: "1px solid var(--line)",
                  }}
                />
                <button
                  type="button"
                  className="btn btn-ghost"
                  style={{ padding: "0.2rem 0.5rem", marginTop: "0.3rem", width: "100%" }}
                  onClick={() => removeFile(index)}
                >
                  Remove
                </button>
              </div>
            ))}
          </div>
        )}

        <h3 style={{ marginBottom: 0 }}>Optional image URLs</h3>
        {extraUrls.map((url, index) => (
          <div key={index} className="form-row" style={{ alignItems: "end" }}>
            <label>
              URL #{index + 1}
              <input
                value={url}
                onChange={(e) =>
                  setExtraUrls((prev) => prev.map((u, i) => (i === index ? e.target.value : u)))
                }
                placeholder="https://..."
              />
            </label>
            {extraUrls.length > 1 && (
              <button
                type="button"
                className="btn btn-ghost"
                onClick={() => setExtraUrls((prev) => prev.filter((_, i) => i !== index))}
              >
                Remove
              </button>
            )}
          </div>
        ))}
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => setExtraUrls((prev) => [...prev, ""])}
        >
          + Add URL
        </button>

        {error && <div className="error">{error}</div>}
        {message && <p className="price">{message}</p>}
        {createdSlug && (
          <Link href={`/shop/${createdSlug}`} className="btn btn-ghost">
            View on shop
          </Link>
        )}
        <button className="btn btn-primary" disabled={loading}>
          {loading ? "Saving…" : "Save dress"}
        </button>
      </form>
    </section>
  );
}
