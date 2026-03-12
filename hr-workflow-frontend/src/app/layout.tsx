import type { Metadata } from "next";
import "./globals.css";
import { Toaster } from "react-hot-toast";
import Footer from "../components/layout/Footer";

export const metadata: Metadata = {
  title: "HR Workflow System",
  description: "Automate your HR processes with ease",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="flex flex-col min-h-screen bg-background text-foreground">
        <main className="flex-grow">{children}</main>
        <Footer />
        <Toaster position="top-right" />
      </body>
    </html>
  );
}
