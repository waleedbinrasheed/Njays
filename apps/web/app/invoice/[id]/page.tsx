"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { api, formatPkr } from "@/lib/api";

type InvoiceAddress = {
  line1: string;
  line2?: string;
  city: string;
  province?: string;
  postalCode?: string;
  country?: string;
};

type InvoiceMeasurements = {
  name?: string;
  unit?: string;
  kameezLength?: number;
  chest?: number;
  waist?: number;
  hip?: number;
  shoulder?: number;
  sleeveLength?: number;
  collarLength?: number;
  shalwarLength?: number;
  shalwarBottom?: number;
  backStyle?: string;
  sleeveStyle?: string;
  buttonStyle?: string;
  collarStyle?: string;
  cuffStyle?: string;
  notes?: string;
};

type InvoiceItem = {
  productName: string;
  custom: boolean;
  fabricLabel?: string;
  quantity: number;
  unitPricePaisa: number;
  lineTotalPaisa: number;
  measurements?: InvoiceMeasurements;
};

type Invoice = {
  business: { name: string; address?: string; phone?: string; email?: string };
  customer: { fullName?: string; phone?: string; email?: string; shippingAddress?: InvoiceAddress };
  order: { orderNumber: string; orderType: string; status: string; orderDate: string };
  items: InvoiceItem[];
  totals: {
    currency: string;
    subtotalPaisa: number;
    shippingPaisa: number;
    totalPaisa: number;
    amountPaidPaisa: number;
    balanceDuePaisa: number;
  };
};

function measurementLine(m: InvoiceMeasurements): string {
  const parts: string[] = [];
  if (m.kameezLength != null) parts.push(`Kameez L ${m.kameezLength}`);
  if (m.chest != null) parts.push(`Chest ${m.chest}`);
  if (m.waist != null) parts.push(`Waist ${m.waist}`);
  if (m.hip != null) parts.push(`Hip ${m.hip}`);
  if (m.shoulder != null) parts.push(`Shoulder ${m.shoulder}`);
  if (m.sleeveLength != null) parts.push(`Sleeve ${m.sleeveLength}`);
  if (m.collarLength != null) parts.push(`Collar ${m.collarLength}`);
  if (m.shalwarLength != null) parts.push(`Shalwar L ${m.shalwarLength}`);
  if (m.shalwarBottom != null) parts.push(`Shalwar Bottom ${m.shalwarBottom}`);
  const unit = m.unit ? m.unit.toLowerCase() : "";
  const measureLine = parts.length ? `${parts.join(" · ")}${unit ? ` (${unit})` : ""}` : "";

  const style: string[] = [];
  if (m.backStyle) style.push(`Back ${m.backStyle}`);
  if (m.sleeveStyle) style.push(`Sleeve ${m.sleeveStyle}`);
  if (m.buttonStyle) style.push(`Button ${m.buttonStyle}`);
  if (m.collarStyle) style.push(`Collar ${m.collarStyle}`);
  if (m.cuffStyle) style.push(`Cuff ${m.cuffStyle}`);
  const styleLine = style.join(" · ");

  return [measureLine, styleLine].filter(Boolean).join(" — ");
}

function formatAddress(a?: InvoiceAddress): string {
  if (!a) return "";
  return [a.line1, a.line2, a.city, a.province, a.postalCode, a.country].filter(Boolean).join(", ");
}

export default function InvoicePage() {
  const params = useParams<{ id: string }>();
  const [invoice, setInvoice] = useState<Invoice | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let role = "";
    try {
      role = (JSON.parse(localStorage.getItem("user") || "{}") as { role?: string }).role || "";
    } catch {
      role = "";
    }
    const isAdmin = role === "ADMIN" || role === "STAFF";
    const path = isAdmin
      ? `/api/v1/admin/orders/${params.id}/invoice`
      : `/api/v1/orders/${params.id}/invoice`;

    api<Invoice>(path)
      .then(setInvoice)
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load invoice"));
  }, [params.id]);

  if (error) {
    return (
      <div className="container section">
        <div className="error">{error}</div>
      </div>
    );
  }

  if (!invoice) {
    return (
      <div className="container section">
        <div className="skeleton" aria-busy="true" aria-label="Loading invoice" />
      </div>
    );
  }

  return (
    <section className="container invoice-shell">
      <div className="invoice-toolbar no-print">
        <Link href="/account/orders" className="link-subtle">
          ← Back to orders
        </Link>
        <button type="button" className="btn btn-primary" onClick={() => window.print()}>
          Print Invoice
        </button>
      </div>

      <div className="invoice-paper">
        <div className="invoice-header">
          <div className="invoice-business">
            <h1>{invoice.business.name}</h1>
            {invoice.business.address && <p>{invoice.business.address}</p>}
            {invoice.business.phone && <p>Phone: {invoice.business.phone}</p>}
            {invoice.business.email && <p>{invoice.business.email}</p>}
          </div>
          <div className="invoice-title">
            <h2>Invoice</h2>
            <p>
              <strong>Order / Tracking No.</strong> {invoice.order.orderNumber}
            </p>
            <p>Date: {new Date(invoice.order.orderDate).toLocaleDateString()}</p>
            <p>Status: {invoice.order.status.replaceAll("_", " ")}</p>
          </div>
        </div>

        <div className="invoice-parties">
          <div className="invoice-block">
            <h3>Bill To</h3>
            {invoice.customer.fullName && <p>{invoice.customer.fullName}</p>}
            {invoice.customer.phone && <p>{invoice.customer.phone}</p>}
            {invoice.customer.email && <p>{invoice.customer.email}</p>}
            {invoice.customer.shippingAddress && <p>{formatAddress(invoice.customer.shippingAddress)}</p>}
          </div>
          <div className="invoice-block">
            <h3>Order Details</h3>
            <p>Type: {invoice.order.orderType === "CUSTOM" ? "Made to measure" : "Ready-made"}</p>
          </div>
        </div>

        <table className="invoice-table">
          <thead>
            <tr>
              <th>Item</th>
              <th className="num">Qty</th>
              <th className="num">Price</th>
              <th className="num">Total</th>
            </tr>
          </thead>
          <tbody>
            {invoice.items.map((item, i) => {
              const measureLine = item.measurements ? measurementLine(item.measurements) : "";
              return (
                <tr key={i}>
                  <td>
                    {item.productName}
                    {item.fabricLabel && <span className="invoice-item-meta">{item.fabricLabel}</span>}
                    {measureLine && <span className="invoice-measurements">{measureLine}</span>}
                  </td>
                  <td className="num">{item.quantity}</td>
                  <td className="num">{formatPkr(item.unitPricePaisa)}</td>
                  <td className="num">{formatPkr(item.lineTotalPaisa)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>

        <div className="invoice-totals">
          <table>
            <tbody>
              <tr>
                <td>Subtotal</td>
                <td>{formatPkr(invoice.totals.subtotalPaisa)}</td>
              </tr>
              {invoice.totals.shippingPaisa > 0 && (
                <tr>
                  <td>Shipping</td>
                  <td>{formatPkr(invoice.totals.shippingPaisa)}</td>
                </tr>
              )}
              <tr className="invoice-grand-total">
                <td>Total</td>
                <td>{formatPkr(invoice.totals.totalPaisa)}</td>
              </tr>
              <tr>
                <td>Amount Paid</td>
                <td>{formatPkr(invoice.totals.amountPaidPaisa)}</td>
              </tr>
              <tr className="invoice-balance-due">
                <td>Balance Due</td>
                <td>{formatPkr(invoice.totals.balanceDuePaisa)}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <p className="invoice-footnote">Thank you for your order — {invoice.business.name}</p>
      </div>
    </section>
  );
}
