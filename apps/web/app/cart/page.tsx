"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api, formatPkr } from "@/lib/api";

type Cart = {
  id: number;
  subtotalPaisa: number;
  items: {
    id: number;
    productName: string;
    quantity: number;
    custom: boolean;
    lineTotalPaisa: number;
  }[];
};

export default function CartPage() {
  const [cart, setCart] = useState<Cart | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api<Cart>("/api/v1/cart")
      .then(setCart)
      .catch((e) => setError(e.message));
  }, []);

  if (error) {
    return (
      <div className="container section">
        <div className="page-header">
          <h2>Cart</h2>
        </div>
        <div className="error">{error}</div>
        <Link href="/login" className="btn btn-primary" style={{ marginTop: "1rem" }}>
          Sign in
        </Link>
      </div>
    );
  }

  if (!cart) {
    return (
      <div className="container section">
        <div className="skeleton" aria-busy="true" aria-label="Loading cart" />
      </div>
    );
  }

  return (
    <section className="container section">
      <div className="page-header">
        <span className="section-label">Bag</span>
        <h2>Your cart</h2>
        <p className="lead">Review pieces before checkout.</p>
      </div>
      <div className="panel" style={{ maxWidth: 720 }}>
        {cart.items.length === 0 && (
          <p className="muted">
            Your cart is empty.{" "}
            <Link href="/shop" className="link-subtle">
              Browse the collection
            </Link>
          </p>
        )}
        {cart.items.map((item) => (
          <div key={item.id} className="list-row">
            <div>
              <strong>{item.productName}</strong>
              <div className="muted">
                Qty {item.quantity} · {item.custom ? "Custom" : "Ready-made"}
              </div>
            </div>
            <div className="price">{formatPkr(item.lineTotalPaisa)}</div>
          </div>
        ))}
        {cart.items.length > 0 && (
          <>
            <div className="cart-total">
              <strong>Subtotal</strong>
              <strong className="price">{formatPkr(cart.subtotalPaisa)}</strong>
            </div>
            <Link href="/checkout" className="btn btn-primary" style={{ marginTop: "1.2rem" }}>
              Checkout
            </Link>
          </>
        )}
      </div>
    </section>
  );
}
