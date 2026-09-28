"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { api, apiForm, formatPkr, type Category, type Product } from "@/lib/api";
import { AdminNav } from "@/components/AdminNav";

export default function AdminDesignsPage() {
  const [designs, setDesigns] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [files, setFiles] = useState<File[]>([]);
  const [previews, setPreviews] = useState<string[]>([]);
  const [extraUrls, setExtraUrls] = useState<string[]>([""]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  function load() {
    api<Product[]>("/api/v1/products")
      .then(setDesigns)
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load designs"));
  }

  useEffect(() => {
    load();
    api<Category[]>("/api/v1/categories")
      .then(setCategories)
      .catch(() => undefined);
  }, []);

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
      formEl.reset();
      setFiles([]);
      setExtraUrls([""]);
      load();
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
      <AdminNav />
      <div className="page-header">
        <span className="section-label">Designs</span>
        <h2>Dress Designs</h2>
        <p className="lead">Each design is a garment style customers can order ready-made or made-to-measure.</p>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="pos-grid">
        <div>
          <h3>Existing designs</h3>
          <div className="list-stack">
            {designs.map((d) => (
              <div key={d.id} className="panel">
                <div className="list-row" style={{ borderBottom: "none", paddingTop: 0 }}>
                  <div>
                    <strong>{d.name}</strong>
                    <div className="muted">{d.supportsCustom ? "Made-to-measure available" : "Ready-made only"}</div>
                  </div>
                  <div className="price">{formatPkr(d.basePricePaisa)}</div>
                </div>
                <Link href={`/shop/${d.slug}`} className="link-subtle">
                  View on shop
                </Link>
              </div>
            ))}
            {designs.length === 0 && <p className="muted">No designs yet.</p>}
          </div>
        </div>

        <div>
          <h3>Add design</h3>
          {message && <p className="success">{message}</p>}
          <form className="panel form" onSubmit={onSubmit}>
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
            <label>
              Price (PKR)
              <input name="pricePkr" type="number" min={1} step="1" required placeholder="8500" />
            </label>
            <label>
              Garment type
              <select name="categoryId" defaultValue="">
                <option value="">Select garment type</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
            <label style={{ display: "flex", alignItems: "center", gap: "0.5rem", flexDirection: "row" }}>
              <input name="supportsCustom" type="checkbox" defaultChecked />
              Supports made-to-measure
            </label>

            <label>
              Photos
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
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(72px, 1fr))", gap: "0.5rem" }}>
                {previews.map((src, index) => (
                  <div key={src}>
                    <img
                      src={src}
                      alt={`Preview ${index + 1}`}
                      style={{ width: "100%", height: 72, objectFit: "cover", borderRadius: "0.5rem" }}
                    />
                    <button type="button" className="link-subtle" onClick={() => removeFile(index)}>
                      Remove
                    </button>
                  </div>
                ))}
              </div>
            )}

            {extraUrls.map((url, index) => (
              <label key={index}>
                Image URL {index + 1}
                <input
                  value={url}
                  onChange={(e) => setExtraUrls((prev) => prev.map((u, i) => (i === index ? e.target.value : u)))}
                  placeholder="https://..."
                />
              </label>
            ))}
            <button type="button" className="btn btn-ghost" onClick={() => setExtraUrls((prev) => [...prev, ""])}>
              + Add URL
            </button>

            {error && <div className="error">{error}</div>}
            <button className="btn btn-primary" disabled={loading}>
              {loading ? "Saving…" : "Save design"}
            </button>
          </form>
        </div>
      </div>
    </section>
  );
}
