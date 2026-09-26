import Link from "next/link";
import { api, formatPkr, type Product } from "@/lib/api";

export default async function ShopPage() {
  let products: Product[] = [];
  try {
    products = await api<Product[]>("/api/v1/products");
  } catch {
    products = [];
  }

  return (
    <section className="container section">
      <div className="page-header">
        <span className="section-label">Menswear</span>
        <h2>Shop the collection</h2>
        <p className="lead">Ready-made or made-to-measure — every piece can be tailored to you.</p>
      </div>
      <div className="grid">
        {products.map((p) => (
          <Link key={p.id} href={`/shop/${p.slug}`} className="product">
            <div className="product-media">
              <img src={p.images[0]?.url} alt={p.name} loading="lazy" decoding="async" />
            </div>
            <div className="product-body">
              <h3>{p.name}</h3>
              <p className="product-meta">{p.supportsCustom ? "Custom measure available" : "Ready-made"}</p>
              <div className="price">{formatPkr(p.basePricePaisa)}</div>
            </div>
          </Link>
        ))}
        {products.length === 0 && <p className="muted">No products yet. Start the API to load the catalog.</p>}
      </div>
    </section>
  );
}
