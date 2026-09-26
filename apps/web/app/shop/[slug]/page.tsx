"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import {
  api,
  formatPkr,
  type FabricTier,
  type Measurement,
  type Product,
} from "@/lib/api";

export default function ProductPage() {
  const params = useParams<{ slug: string }>();
  const router = useRouter();
  const [product, setProduct] = useState<Product | null>(null);
  const [fabrics, setFabrics] = useState<FabricTier[]>([]);
  const [measurements, setMeasurements] = useState<Measurement[]>([]);
  const [custom, setCustom] = useState(true);
  const [fabricColorId, setFabricColorId] = useState<number | "">("");
  const [measurementId, setMeasurementId] = useState<number | "">("");
  const [waUrl, setWaUrl] = useState("#");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [activeImage, setActiveImage] = useState(0);

  useEffect(() => {
    Promise.all([
      api<Product>(`/api/v1/products/${params.slug}`),
      api<FabricTier[]>("/api/v1/fabrics"),
    ])
      .then(([p, f]) => {
        setProduct(p);
        setFabrics(f);
        setActiveImage(0);
        const first = f[0]?.colors[0]?.id;
        if (first) setFabricColorId(first);
        api<{ url: string }>(
          `/api/v1/whatsapp/link?context=PRODUCT&productName=${encodeURIComponent(p.name)}`
        ).then((r) => setWaUrl(r.url));
      })
      .catch((e) => setError(e.message));

    if (localStorage.getItem("accessToken")) {
      api<Measurement[]>("/api/v1/me/measurements")
        .then((m) => {
          setMeasurements(m);
          if (m[0]) setMeasurementId(m[0].id);
        })
        .catch(() => undefined);
    }
  }, [params.slug]);

  const surcharge = useMemo(() => {
    for (const tier of fabrics) {
      const color = tier.colors.find((c) => c.id === fabricColorId);
      if (color) return tier.surchargePaisa;
    }
    return 0;
  }, [fabrics, fabricColorId]);

  async function addToCart() {
    setError("");
    if (!localStorage.getItem("accessToken")) {
      router.push("/login");
      return;
    }
    if (!product) return;
    setLoading(true);
    try {
      await api("/api/v1/cart/items", {
        method: "POST",
        body: JSON.stringify({
          productId: product.id,
          quantity: 1,
          custom,
          fabricColorId: custom ? fabricColorId : null,
          measurementProfileId: custom ? measurementId : null,
        }),
      });
      router.push("/cart");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to add");
    } finally {
      setLoading(false);
    }
  }

  if (!product && !error) {
    return (
      <div className="container section">
        <div className="skeleton" aria-busy="true" aria-label="Loading product" />
      </div>
    );
  }

  if (!product) {
    return (
      <div className="container section">
        <div className="error">{error}</div>
      </div>
    );
  }

  const total = product.basePricePaisa + (custom ? surcharge : 0);

  return (
    <section className="container section">
      <div className="pdp-grid">
        <div className="pdp-gallery">
          <img
            className="pdp-main-image"
            src={product.images[activeImage]?.url || product.images[0]?.url}
            alt={product.images[activeImage]?.altText || product.name}
          />
          {product.images.length > 1 && (
            <div className="pdp-thumbs" role="listbox" aria-label="Product images">
              {product.images.map((img, idx) => (
                <button
                  key={`${img.url}-${idx}`}
                  type="button"
                  className={`pdp-thumb${activeImage === idx ? " active" : ""}`}
                  onClick={() => setActiveImage(idx)}
                  aria-label={`View image ${idx + 1}`}
                  aria-selected={activeImage === idx}
                  role="option"
                >
                  <img
                    src={img.url}
                    alt={img.altText || `${product.name} ${idx + 1}`}
                    loading="lazy"
                    decoding="async"
                  />
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="panel">
          <span className="section-label">{custom ? "Made to measure" : "Ready-made"}</span>
          <h2 style={{ marginTop: 0 }}>{product.name}</h2>
          <p className="muted">{product.description}</p>
          <p className="price pdp-price">{formatPkr(total)}</p>

          <div className="form" style={{ marginTop: "1.2rem" }}>
            <label>
              Order type
              <select
                value={custom ? "custom" : "ready"}
                onChange={(e) => setCustom(e.target.value === "custom")}
              >
                <option value="custom">Made to measure</option>
                <option value="ready">Ready-made</option>
              </select>
            </label>

            {custom && (
              <>
                <label>
                  Fabric color
                  <select
                    value={fabricColorId}
                    onChange={(e) => setFabricColorId(Number(e.target.value))}
                  >
                    {fabrics.map((tier) =>
                      tier.colors.map((c) => (
                        <option key={c.id} value={c.id}>
                          {tier.name} — {c.name} (+{formatPkr(tier.surchargePaisa)})
                        </option>
                      ))
                    )}
                  </select>
                </label>
                <label>
                  Measurement profile
                  <select
                    value={measurementId}
                    onChange={(e) => setMeasurementId(Number(e.target.value))}
                  >
                    {measurements.length === 0 && <option value="">Save a profile first</option>}
                    {measurements.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.name} (L {m.kameezLength || "—"} / Chest {m.chest || "—"})
                      </option>
                    ))}
                  </select>
                </label>
                {measurements.length === 0 && (
                  <a href="/account/measurements" className="btn btn-ghost">
                    Create measurements
                  </a>
                )}
              </>
            )}

            {error && <div className="error">{error}</div>}

            <div className="form-actions">
              <button className="btn btn-primary" disabled={loading} onClick={addToCart}>
                {loading ? "Adding…" : "Add to cart"}
              </button>
              <a className="btn btn-wa" href={waUrl} target="_blank" rel="noreferrer">
                Ask on WhatsApp
              </a>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
