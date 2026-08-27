import type { Metadata } from "next";
import "./globals.css";
import { SiteHeader } from "@/components/SiteHeader";
import { WhatsAppFab } from "@/components/WhatsAppFab";
import Link from "next/link";

export const metadata: Metadata = {
  title: "NJAY'S by S.A.R — Made to Measure Menswear",
  description:
    "Custom tailored menswear by NJAY'S. Measurements, tracking, WhatsApp support, and local payments.",
  icons: {
    icon: "/logo.png",
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&family=Outfit:wght@500;600;700;800&display=swap"
          rel="stylesheet"
        />
      </head>
      <body suppressHydrationWarning>
        <a href="#main-content" className="skip-link">
          Skip to content
        </a>
        <SiteHeader />
        <main id="main-content">{children}</main>
        <WhatsAppFab />
        <footer className="site-footer">
          <div className="container">
            <div className="footer-grid">
              <div className="footer-brand">
                <img src="/logo.png" alt="" width={52} height={52} />
                <div>
                  <strong>NJAY&apos;S</strong>
                  <span className="muted" style={{ display: "block", fontSize: "0.8rem", letterSpacing: "0.12em" }}>
                    BY S.A.R
                  </span>
                  <p className="muted" style={{ margin: "0.65rem 0 0", fontSize: "0.9rem", maxWidth: "22rem" }}>
                    Made-to-measure menswear with careful fittings, clear tracking, and local payment options.
                  </p>
                </div>
              </div>
              <div>
                <h3 className="footer-title">Explore</h3>
                <div className="footer-links">
                  <Link href="/shop">Shop</Link>
                  <Link href="/account/measurements">Measurements</Link>
                  <Link href="/track">Track order</Link>
                  <Link href="/cart">Cart</Link>
                </div>
              </div>
              <div>
                <h3 className="footer-title">Support</h3>
                <div className="footer-links">
                  <Link href="/login">Sign in</Link>
                  <Link href="/account/orders">Orders</Link>
                  <span>COD · Bank · JazzCash</span>
                  <span>Free delivery across Pakistan</span>
                </div>
              </div>
            </div>
            <div className="footer-copy">© {new Date().getFullYear()} NJAY&apos;S by S.A.R. All rights reserved.</div>
          </div>
        </footer>
      </body>
    </html>
  );
}
