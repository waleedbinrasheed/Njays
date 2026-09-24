import type { Metadata } from "next";
import { AuthProvider } from "@/context/AuthContext";
import { SiteHeader } from "@/components/SiteHeader";
import "./globals.css";

export const metadata: Metadata = {
  title: "NJAY'S by S.A.R — Made to Measure Menswear",
  description: "Custom tailored menswear by NJAY'S.",
  icons: { icon: "/logo.png" },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&family=Outfit:wght@500;600;700;800&display=swap"
          rel="stylesheet"
        />
      </head>
      <body>
        <AuthProvider>
          <SiteHeader />
          <main>{children}</main>
          <footer className="site-footer">
            <div className="container">
              <div className="footer-brand">
                <img src="/logo.png" alt="" width={40} height={40} />
                <div>
                  <strong>NJAY&apos;S</strong>
                  <span className="muted" style={{ fontSize: "0.8rem" }}>
                    BY S.A.R
                  </span>
                </div>
              </div>
              <div className="footer-copy">© {new Date().getFullYear()} NJAY&apos;S by S.A.R. All rights reserved.</div>
            </div>
          </footer>
        </AuthProvider>
      </body>
    </html>
  );
}
