import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "HR Workflow System",
  description: "Automate your HR processes with ease",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <head>
        <link rel="icon" href="/favicon.ico" />
      </head>
      <body className="antialiased bg-gray-50">
        {children}
      </body>
    </html>
  );
}