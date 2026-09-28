"use client";

import { FormEvent, Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
  api,
  formatPkr,
  type Branch,
  type Category,
  type FabricTier,
  type Measurement,
  type Product,
} from "@/lib/api";
import { AdminNav } from "@/components/AdminNav";

type CustomerSummary = { id: number; fullName: string; phone?: string; email?: string };

type CartLine = {
  key: string;
  productId: number;
  productName: string;
  quantity: number;
  custom: boolean;
  fabricColorId?: number;
  fabricLabel?: string;
  measurementProfileId?: number;
  unitPricePaisa: number;
  lineTotalPaisa: number;
};

const BACK_OPTIONS = ["PLAIN", "BOX"];
const SLEEVE_OPTIONS = ["PLAIN", "CUT"];
const BUTTON_OPTIONS = ["SAME", "CONTRAST", "BRASS"];
const COLLAR_OPTIONS = ["BAN", "HALF_BAN", "FULL_BAN", "MANDARIN"];
const CUFF_OPTIONS = ["ROUND", "CUT"];
const PAYMENT_METHODS = ["CASH", "BANK_TRANSFER", "COD"];

export default function AdminCreateOrderPage() {
  return (
    <Suspense fallback={null}>
      <AdminCreateOrderPageInner />
    </Suspense>
  );
}

function AdminCreateOrderPageInner() {
  const searchParams = useSearchParams();
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);

  // Step 1 — customer
  const [customerQuery, setCustomerQuery] = useState("");
  const [customerResults, setCustomerResults] = useState<CustomerSummary[]>([]);
  const [selectedCustomer, setSelectedCustomer] = useState<CustomerSummary | null>(null);
  const [showNewCustomer, setShowNewCustomer] = useState(false);

  // Step 2 — measurements
  const [profiles, setProfiles] = useState<Measurement[]>([]);
  const [selectedProfileId, setSelectedProfileId] = useState<number | "">("");
  const [showNewMeasurement, setShowNewMeasurement] = useState(false);

  // Step 3 — branch
  const [branches, setBranches] = useState<Branch[]>([]);
  const [createdBranchId, setCreatedBranchId] = useState<number | "">("");
  const [dispatchBranchId, setDispatchBranchId] = useState<number | "">("");
  const [dispatchCostPaisa, setDispatchCostPaisa] = useState(0);

  // Step 4 — garment / design / fabric + item builder
  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [itemCategoryId, setItemCategoryId] = useState<number | "">("");
  const [itemProductId, setItemProductId] = useState<number | "">("");
  const [itemCustom, setItemCustom] = useState(true);
  const [fabrics, setFabrics] = useState<FabricTier[]>([]);
  const [itemFabricColorId, setItemFabricColorId] = useState<number | "">("");
  const [itemQuantity, setItemQuantity] = useState(1);
  const [cart, setCart] = useState<CartLine[]>([]);

  // Step 5 — price + payment + delivery
  const [discountPkr, setDiscountPkr] = useState("0");
  const [paymentMethod, setPaymentMethod] = useState("CASH");
  const [paymentAmountPkr, setPaymentAmountPkr] = useState("");
  const [expectedDeliveryDate, setExpectedDeliveryDate] = useState("");
  const [line1, setLine1] = useState("In-store pickup");
  const [city, setCity] = useState("");
  const [whatsappPhone, setWhatsappPhone] = useState("");
  const [note, setNote] = useState("");
  const [successOrder, setSuccessOrder] = useState<{ id: number; publicCode: string } | null>(null);

  useEffect(() => {
    api<Category[]>("/api/v1/categories").then(setCategories).catch(() => undefined);
    api<Product[]>("/api/v1/products").then(setProducts).catch(() => undefined);
    api<FabricTier[]>("/api/v1/fabrics").then(setFabrics).catch(() => undefined);
    api<Branch[]>("/api/v1/branches").then((list) => {
      setBranches(list);
      if (list.length === 1) {
        setCreatedBranchId(list[0].id);
        setDispatchBranchId(list[0].id);
      }
    }).catch(() => undefined);
  }, []);

  useEffect(() => {
    const customerId = searchParams.get("customerId");
    if (!customerId) return;
    api<{ id: number; fullName: string; phone?: string; email?: string }>(`/api/v1/admin/customers/${customerId}/detail`)
      .then((d) => setSelectedCustomer({ id: d.id, fullName: d.fullName, phone: d.phone, email: d.email }))
      .catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (selectedCustomer) {
      setWhatsappPhone(selectedCustomer.phone || "");
      api<Measurement[]>(`/api/v1/admin/customers/${selectedCustomer.id}/measurements`)
        .then((list) => {
          setProfiles(list);
          if (list[0]) setSelectedProfileId(list[0].id);
        })
        .catch(() => setProfiles([]));
    } else {
      setProfiles([]);
      setSelectedProfileId("");
    }
  }, [selectedCustomer]);

  useEffect(() => {
    if (!createdBranchId || !dispatchBranchId) {
      setDispatchCostPaisa(0);
      return;
    }
    if (createdBranchId === dispatchBranchId) {
      setDispatchCostPaisa(0);
      return;
    }
    api<{ costPaisa: number }>(
      `/api/v1/admin/dispatch-costs/resolve?sourceBranchId=${createdBranchId}&destinationBranchId=${dispatchBranchId}`
    )
      .then((r) => setDispatchCostPaisa(r.costPaisa))
      .catch(() => setDispatchCostPaisa(0));
  }, [createdBranchId, dispatchBranchId]);

  async function searchCustomers(e: FormEvent) {
    e.preventDefault();
    setError("");
    try {
      const results = await api<CustomerSummary[]>(`/api/v1/admin/customers?query=${encodeURIComponent(customerQuery)}`);
      setCustomerResults(results);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Search failed");
    }
  }

  async function createCustomer(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    const fd = new FormData(e.currentTarget);
    try {
      const created = await api<CustomerSummary>("/api/v1/admin/customers", {
        method: "POST",
        body: JSON.stringify({
          fullName: String(fd.get("fullName")),
          phone: String(fd.get("phone")),
          email: String(fd.get("email") || "") || null,
        }),
      });
      setSelectedCustomer(created);
      setShowNewCustomer(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create customer");
    }
  }

  async function createMeasurement(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!selectedCustomer) return;
    setError("");
    const fd = new FormData(e.currentTarget);
    const num = (key: string) => {
      const v = fd.get(key);
      return v === null || v === "" ? null : Number(v);
    };
    try {
      const created = await api<Measurement>(`/api/v1/admin/customers/${selectedCustomer.id}/measurements`, {
        method: "POST",
        body: JSON.stringify({
          name: String(fd.get("name") || "In-store fit"),
          unit: "INCH",
          kameezLength: num("kameezLength"),
          chest: num("chest"),
          waist: num("waist"),
          hip: num("hip"),
          shoulder: num("shoulder"),
          sleeveLength: num("sleeveLength"),
          collarLength: num("collarLength"),
          shalwarLength: num("shalwarLength"),
          shalwarBottom: num("shalwarBottom"),
          backStyle: fd.get("backStyle"),
          sleeveStyle: fd.get("sleeveStyle"),
          buttonStyle: fd.get("buttonStyle"),
          collarStyle: fd.get("collarStyle"),
          cuffStyle: fd.get("cuffStyle"),
          notes: fd.get("notes"),
          isDefault: false,
        }),
      });
      setProfiles((prev) => [created, ...prev]);
      setSelectedProfileId(created.id);
      setShowNewMeasurement(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save measurements");
    }
  }

  const filteredProducts = useMemo(
    () => (itemCategoryId ? products.filter((p) => p.categoryId === itemCategoryId) : products),
    [products, itemCategoryId]
  );
  const selectedProduct = useMemo(() => products.find((p) => p.id === itemProductId), [products, itemProductId]);
  const selectedFabricColor = useMemo(() => {
    for (const tier of fabrics) {
      const color = tier.colors.find((c) => c.id === itemFabricColorId);
      if (color) return { tier, color };
    }
    return null;
  }, [fabrics, itemFabricColorId]);

  function addItemToCart() {
    setError("");
    if (!selectedProduct) {
      setError("Pick a design first");
      return;
    }
    if (itemCustom && (!selectedFabricColor || !selectedProfileId)) {
      setError("Custom items need a fabric color and a measurement profile");
      return;
    }
    const unitPrice = selectedProduct.basePricePaisa + (itemCustom && selectedFabricColor ? selectedFabricColor.tier.surchargePaisa : 0);
    const line: CartLine = {
      key: `${Date.now()}-${Math.random()}`,
      productId: selectedProduct.id,
      productName: selectedProduct.name,
      quantity: itemQuantity,
      custom: itemCustom,
      fabricColorId: itemCustom ? (itemFabricColorId as number) : undefined,
      fabricLabel: itemCustom && selectedFabricColor ? `${selectedFabricColor.tier.name} / ${selectedFabricColor.color.name}` : undefined,
      measurementProfileId: itemCustom ? (selectedProfileId as number) : undefined,
      unitPricePaisa: unitPrice,
      lineTotalPaisa: unitPrice * itemQuantity,
    };
    setCart((prev) => [...prev, line]);
    setItemQuantity(1);
  }

  function removeItem(key: string) {
    setCart((prev) => prev.filter((l) => l.key !== key));
  }

  const subtotal = cart.reduce((sum, l) => sum + l.lineTotalPaisa, 0);
  const discountPaisa = Math.min(subtotal, Math.max(0, Math.round(Number(discountPkr || 0) * 100)));
  const grandTotal = subtotal - discountPaisa + dispatchCostPaisa;

  useEffect(() => {
    setPaymentAmountPkr((grandTotal / 100).toFixed(0));
  }, [grandTotal]);

  async function submitOrder() {
    setError("");
    if (!selectedCustomer) {
      setError("Select or create a customer first");
      return;
    }
    if (!dispatchBranchId) {
      setError("Select a dispatch/pickup branch");
      return;
    }
    if (cart.length === 0) {
      setError("Add at least one item");
      return;
    }
    const paymentAmountPaisa = Math.round(Number(paymentAmountPkr || 0) * 100);
    setSubmitting(true);
    try {
      const order = await api<{ id: number; publicCode: string }>("/api/v1/admin/orders", {
        method: "POST",
        body: JSON.stringify({
          customerId: selectedCustomer.id,
          shippingAddress: { line1, city: city || "N/A", country: "PK" },
          whatsappPhone: whatsappPhone || selectedCustomer.phone,
          customerNote: note,
          createdBranchId: createdBranchId || null,
          dispatchBranchId,
          discountPaisa,
          expectedDeliveryDate: expectedDeliveryDate || null,
          payment: { method: paymentMethod, amountPaisa: paymentAmountPaisa },
          items: cart.map((l) => ({
            productId: l.productId,
            quantity: l.quantity,
            custom: l.custom,
            fabricColorId: l.fabricColorId,
            measurementProfileId: l.measurementProfileId,
          })),
        }),
      });
      setSuccessOrder(order);
      setMessage(`Order ${order.publicCode} created.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create order");
    } finally {
      setSubmitting(false);
    }
  }

  if (successOrder) {
    return (
      <section className="container section">
        <AdminNav />
        <div className="panel" style={{ maxWidth: 560 }}>
          <h3>Order {successOrder.publicCode} created</h3>
          <p className="success">{message}</p>
          <div className="form-actions">
            <Link href={`/invoice/${successOrder.id}`} className="btn btn-primary">
              Print Payment Slip
            </Link>
            <a href="/admin/orders/new" className="btn btn-ghost">
              New order
            </a>
            <Link href="/admin" className="btn btn-ghost">
              Back to dashboard
            </Link>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="container section">
      <AdminNav />
      <div className="page-header">
        <span className="section-label">Orders</span>
        <h2>New order</h2>
        <p className="lead">For a walk-in client at the shop.</p>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="pos-grid">
        <div>
          {/* Step 1: customer */}
          <div className="pos-step panel">
            <h3 className="pos-step-title">
              <span className="pos-step-num">1</span> Customer
            </h3>
            {selectedCustomer ? (
              <div className="pos-customer-result selected" style={{ cursor: "default" }}>
                <div>
                  <strong>{selectedCustomer.fullName}</strong>
                  <div className="muted">{selectedCustomer.phone || selectedCustomer.email || "—"}</div>
                </div>
                <button type="button" className="btn btn-ghost" onClick={() => setSelectedCustomer(null)}>
                  Change
                </button>
              </div>
            ) : (
              <>
                <form className="form-row" onSubmit={searchCustomers} style={{ alignItems: "end" }}>
                  <label>
                    Mobile number
                    <input
                      value={customerQuery}
                      onChange={(e) => setCustomerQuery(e.target.value)}
                      placeholder="03001234567"
                    />
                  </label>
                  <button className="btn btn-primary">Search</button>
                </form>
                {customerResults.map((c) => (
                  <button
                    key={c.id}
                    type="button"
                    className="pos-customer-result"
                    onClick={() => {
                      setSelectedCustomer(c);
                      setCustomerResults([]);
                    }}
                  >
                    <div>
                      <strong>{c.fullName}</strong>
                      <div className="muted">{c.phone || c.email}</div>
                    </div>
                    <span className="link-subtle">Select</span>
                  </button>
                ))}
                {!showNewCustomer ? (
                  <button type="button" className="btn btn-ghost" style={{ marginTop: "0.5rem" }} onClick={() => setShowNewCustomer(true)}>
                    + New customer
                  </button>
                ) : (
                  <form className="form" onSubmit={createCustomer} style={{ marginTop: "0.75rem" }}>
                    <div className="form-row">
                      <label>
                        Full name
                        <input name="fullName" required />
                      </label>
                      <label>
                        Mobile number
                        <input name="phone" required placeholder="03001234567" />
                      </label>
                    </div>
                    <label>
                      Email (optional)
                      <input name="email" type="email" />
                    </label>
                    <div className="form-actions">
                      <button className="btn btn-primary">Create customer</button>
                      <button type="button" className="btn btn-ghost" onClick={() => setShowNewCustomer(false)}>
                        Cancel
                      </button>
                    </div>
                  </form>
                )}
              </>
            )}
          </div>

          {/* Step 2: measurements */}
          {selectedCustomer && (
            <div className="pos-step panel">
              <h3 className="pos-step-title">
                <span className="pos-step-num">2</span> Measurements
              </h3>
              {profiles.length > 0 && !showNewMeasurement && (
                <label>
                  Use saved profile
                  <select value={selectedProfileId} onChange={(e) => setSelectedProfileId(Number(e.target.value))}>
                    {profiles.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name} (Chest {p.chest || "—"} / Kameez L {p.kameezLength || "—"})
                      </option>
                    ))}
                  </select>
                </label>
              )}
              {!showNewMeasurement ? (
                <button type="button" className="btn btn-ghost" style={{ marginTop: "0.5rem" }} onClick={() => setShowNewMeasurement(true)}>
                  + New measurement profile
                </button>
              ) : (
                <form className="form" onSubmit={createMeasurement} style={{ marginTop: "0.75rem" }}>
                  <label>
                    Profile name
                    <input name="name" defaultValue="In-store fit" />
                  </label>
                  <div className="pos-measure-grid">
                    <label>
                      Kameez L<input name="kameezLength" type="number" step="0.1" />
                    </label>
                    <label>
                      Chest<input name="chest" type="number" step="0.1" />
                    </label>
                    <label>
                      Waist<input name="waist" type="number" step="0.1" />
                    </label>
                    <label>
                      Hip<input name="hip" type="number" step="0.1" />
                    </label>
                    <label>
                      Shoulder<input name="shoulder" type="number" step="0.1" />
                    </label>
                    <label>
                      Sleeve<input name="sleeveLength" type="number" step="0.1" />
                    </label>
                    <label>
                      Collar<input name="collarLength" type="number" step="0.1" />
                    </label>
                    <label>
                      Shalwar L<input name="shalwarLength" type="number" step="0.1" />
                    </label>
                    <label>
                      Shalwar Bottom<input name="shalwarBottom" type="number" step="0.1" />
                    </label>
                    <label>
                      Back
                      <select name="backStyle" defaultValue="PLAIN">
                        {BACK_OPTIONS.map((o) => (
                          <option key={o} value={o}>
                            {o}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      Sleeve style
                      <select name="sleeveStyle" defaultValue="PLAIN">
                        {SLEEVE_OPTIONS.map((o) => (
                          <option key={o} value={o}>
                            {o}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      Button
                      <select name="buttonStyle" defaultValue="SAME">
                        {BUTTON_OPTIONS.map((o) => (
                          <option key={o} value={o}>
                            {o}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      Collar style
                      <select name="collarStyle" defaultValue="BAN">
                        {COLLAR_OPTIONS.map((o) => (
                          <option key={o} value={o}>
                            {o}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      Cuff
                      <select name="cuffStyle" defaultValue="ROUND">
                        {CUFF_OPTIONS.map((o) => (
                          <option key={o} value={o}>
                            {o}
                          </option>
                        ))}
                      </select>
                    </label>
                  </div>
                  <label>
                    Notes
                    <textarea name="notes" rows={2} />
                  </label>
                  <div className="form-actions">
                    <button className="btn btn-primary">Save profile</button>
                    <button type="button" className="btn btn-ghost" onClick={() => setShowNewMeasurement(false)}>
                      Cancel
                    </button>
                  </div>
                </form>
              )}
            </div>
          )}

          {/* Step 3: branch */}
          {selectedCustomer && (
            <div className="pos-step panel">
              <h3 className="pos-step-title">
                <span className="pos-step-num">3</span> Branch
              </h3>
              <div className="form-row">
                <label>
                  Created at
                  <select value={createdBranchId} onChange={(e) => setCreatedBranchId(Number(e.target.value) || "")}>
                    <option value="">Select branch</option>
                    {branches.map((b) => (
                      <option key={b.id} value={b.id}>
                        {b.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Dispatch / pickup at
                  <select value={dispatchBranchId} onChange={(e) => setDispatchBranchId(Number(e.target.value) || "")}>
                    <option value="">Select branch</option>
                    {branches.map((b) => (
                      <option key={b.id} value={b.id}>
                        {b.name}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              {dispatchCostPaisa > 0 && (
                <p className="muted">Dispatch cost: {formatPkr(dispatchCostPaisa)}</p>
              )}
              {branches.length === 0 && (
                <p className="muted">
                  No branches yet — add one under{" "}
                  <Link href="/admin/branches" className="link-subtle">
                    Administration &gt; Branches
                  </Link>
                  .
                </p>
              )}
            </div>
          )}

          {/* Step 4: garment / design / fabric */}
          {selectedCustomer && (
            <div className="pos-step panel">
              <h3 className="pos-step-title">
                <span className="pos-step-num">4</span> Garment, design &amp; fabric
              </h3>
              <div className="form">
                <label>
                  Garment type
                  <select
                    value={itemCategoryId}
                    onChange={(e) => {
                      setItemCategoryId(Number(e.target.value) || "");
                      setItemProductId("");
                    }}
                  >
                    <option value="">All garment types</option>
                    {categories.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Design
                  <select value={itemProductId} onChange={(e) => setItemProductId(Number(e.target.value))}>
                    <option value="">Select a design</option>
                    {filteredProducts.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name} — {formatPkr(p.basePricePaisa)}
                      </option>
                    ))}
                  </select>
                </label>
                <label style={{ display: "flex", alignItems: "center", gap: "0.5rem", flexDirection: "row" }}>
                  <input type="checkbox" checked={itemCustom} onChange={(e) => setItemCustom(e.target.checked)} />
                  Made to measure (uses fabric + measurements above)
                </label>
                {itemCustom && (
                  <label>
                    Fabric color
                    <select value={itemFabricColorId} onChange={(e) => setItemFabricColorId(Number(e.target.value))}>
                      <option value="">Select a fabric color</option>
                      {fabrics.map((tier) =>
                        tier.colors.map((c) => (
                          <option key={c.id} value={c.id}>
                            {tier.name} — {c.name} (+{formatPkr(tier.surchargePaisa)})
                          </option>
                        ))
                      )}
                    </select>
                  </label>
                )}
                <label style={{ maxWidth: 140 }}>
                  Quantity
                  <input
                    type="number"
                    min={1}
                    value={itemQuantity}
                    onChange={(e) => setItemQuantity(Math.max(1, Number(e.target.value)))}
                  />
                </label>
                <button type="button" className="btn btn-primary" onClick={addItemToCart}>
                  Add item
                </button>
              </div>
            </div>
          )}
        </div>

        <div>
          {/* Order summary + price/payment/delivery */}
          <div className="panel preview-sticky">
            <h3 style={{ marginTop: 0 }}>Order summary</h3>
            {cart.length === 0 && <p className="muted">No items added yet.</p>}
            {cart.map((l) => (
              <div key={l.key} className="pos-cart-row">
                <div>
                  <strong>{l.productName}</strong>
                  <div className="muted">
                    Qty {l.quantity} - {l.custom ? l.fabricLabel || "Custom" : "Ready-made"}
                  </div>
                </div>
                <div style={{ textAlign: "right" }}>
                  <div className="price">{formatPkr(l.lineTotalPaisa)}</div>
                  <button type="button" className="link-subtle" onClick={() => removeItem(l.key)}>
                    Remove
                  </button>
                </div>
              </div>
            ))}

            {cart.length > 0 && (
              <>
                <div className="cart-total">
                  <span>Subtotal</span>
                  <span>{formatPkr(subtotal)}</span>
                </div>
                <label>
                  Discount (PKR)
                  <input
                    type="number"
                    min={0}
                    value={discountPkr}
                    onChange={(e) => setDiscountPkr(e.target.value)}
                    style={{ maxWidth: 140 }}
                  />
                </label>
                {dispatchCostPaisa > 0 && (
                  <div className="cart-total">
                    <span>Dispatch</span>
                    <span>{formatPkr(dispatchCostPaisa)}</span>
                  </div>
                )}
                <div className="cart-total">
                  <strong>Grand total</strong>
                  <strong className="price">{formatPkr(grandTotal)}</strong>
                </div>
              </>
            )}

            {selectedCustomer && cart.length > 0 && (
              <div className="form" style={{ marginTop: "1.25rem" }}>
                <h3 style={{ marginBottom: 0 }}>
                  <span className="pos-step-num" style={{ marginRight: "0.5rem" }}>
                    5
                  </span>
                  Payment
                </h3>
                <div className="form-row">
                  <label>
                    Method
                    <select value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)}>
                      {PAYMENT_METHODS.map((m) => (
                        <option key={m} value={m}>
                          {m}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Amount received (PKR)
                    <input
                      type="number"
                      min={0}
                      max={grandTotal / 100}
                      value={paymentAmountPkr}
                      onChange={(e) => setPaymentAmountPkr(e.target.value)}
                    />
                  </label>
                </div>
                {Math.round(Number(paymentAmountPkr || 0) * 100) < grandTotal && (
                  <p className="muted">
                    Remaining balance after this payment:{" "}
                    {formatPkr(grandTotal - Math.round(Number(paymentAmountPkr || 0) * 100))}
                  </p>
                )}

                <h3 style={{ marginBottom: 0 }}>
                  <span className="pos-step-num" style={{ marginRight: "0.5rem" }}>
                    6
                  </span>
                  Delivery
                </h3>
                <label>
                  Address / pickup note
                  <input value={line1} onChange={(e) => setLine1(e.target.value)} />
                </label>
                <label>
                  City
                  <input value={city} onChange={(e) => setCity(e.target.value)} placeholder="Karachi" />
                </label>
                <label>
                  WhatsApp phone
                  <input value={whatsappPhone} onChange={(e) => setWhatsappPhone(e.target.value)} />
                </label>
                <label>
                  Expected delivery date
                  <input
                    type="date"
                    value={expectedDeliveryDate}
                    onChange={(e) => setExpectedDeliveryDate(e.target.value)}
                  />
                </label>
                <label>
                  Note
                  <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={2} />
                </label>
                <button type="button" className="btn btn-primary" disabled={submitting} onClick={submitOrder}>
                  {submitting ? "Creating…" : `Create order — ${formatPkr(grandTotal)}`}
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
