import Link from "next/link";
import { api, formatPkr, type Product } from "@/lib/api";

async function getFeatured(): Promise<Product[]> {
  try {
    return await api<Product[]>("/api/v1/products");
  } catch {
    return [];
  }
}

export default async function HomePage() {
  const products = (await getFeatured()).slice(0, 4);

  return (
    <>
      <section className="hero-bleed" aria-label="NJAY'S made to measure">
        <div className="hero-bleed__media" aria-hidden />
        <div className="container hero-bleed__content">
          <img src="/logo.png" alt="NJAY'S by S.A.R" className="hero-brand" />
          <h1>Tailored with care</h1>
          <p className="hero-copy">
            Made-to-measure menswear from NJAY&apos;S — measure once, order with confidence, track every stitch.
          </p>
          <div className="hero-actions">
            <Link href="/shop" className="btn btn-primary">
              Shop collection
            </Link>
            <Link href="/account/measurements" className="btn btn-ghost">
              Save measurements
            </Link>
          </div>
        </div>
      </section>

      <section className="container section featured-band">
        <span className="section-label">Collection</span>
        <h2>Featured pieces</h2>
        <p className="lead">Hand-finished menswear ready for your custom fit.</p>
        <div className="grid" style={{ marginTop: "1.75rem" }}>
          {products.map((p) => (
            <Link key={p.id} href={`/shop/${p.slug}`} className="product">
              <div className="product-media">
                <img src={p.images[0]?.url} alt={p.name} loading="lazy" decoding="async" />
              </div>
              <div className="product-body">
                <h3>{p.name}</h3>
                <div className="price">{formatPkr(p.basePricePaisa)}</div>
              </div>
            </Link>
          ))}
          {products.length === 0 && (
            <p className="muted">Start the API to load products (`services/api`).</p>
          )}
        </div>
      </section>
    </>
  );
}
