import type { Metadata } from "next";
import { AuthProvider } from "@/context/AuthContext";
import { TopBar } from "@/components/TopBar";
import "./globals.css";

export const metadata: Metadata = {
  title: "Menswear",
  description: "Made-to-measure tailoring, made simple.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <AuthProvider>
          <TopBar />
          <main className="container" style={{ paddingTop: 32, paddingBottom: 64 }}>
            {children}
          </main>
        </AuthProvider>
      </body>
    </html>
  );
}
