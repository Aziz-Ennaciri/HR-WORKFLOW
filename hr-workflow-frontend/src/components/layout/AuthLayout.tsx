"use client";

import Link from "next/link";

interface AuthLayoutProps {
  children: React.ReactNode;
  tagline: string;
}

export default function AuthLayout({ children, tagline }: AuthLayoutProps) {
  return (
    <div className="min-h-screen flex">
      {/* ── Left Panel ── */}
      <div className="hidden lg:flex lg:w-[55%] relative bg-gradient-to-br from-[#1a365d] to-[#2d4a7c] overflow-hidden">
        {/* Abstract CSS decoration */}
        <PanelDecoration />

        <div className="relative z-10 flex flex-col w-full px-14 py-12">
          {/* Logo */}
          <Link href="/" className="flex items-center gap-3 w-fit group">
            <div className="w-10 h-10 rounded-lg bg-white/15 border border-white/20 flex items-center justify-center">
              <span className="text-white font-bold text-sm">HR</span>
            </div>
            <span className="text-white font-bold text-xl tracking-tight">
              HR Workflow
            </span>
          </Link>

          {/* Tagline — vertically centered */}
          <div className="flex-1 flex flex-col justify-center">
            <h2 className="text-4xl xl:text-[44px] font-bold text-white leading-tight mb-5">
              {tagline}
            </h2>
            <p className="text-white/50 text-[15px] leading-relaxed max-w-[280px]">
              Build intelligent HR workflows with AI analysis, automated
              reports, and email notifications.
            </p>
          </div>

          {/* Bottom badge */}
          <p className="text-white/25 text-xs">
            HR Workflow System — Final Year Project (PFE)
          </p>
        </div>
      </div>

      {/* ── Right Panel ── */}
      <div className="flex-1 flex flex-col bg-white">
        {/* Mobile logo bar */}
        <div className="lg:hidden flex items-center gap-2.5 px-6 py-4 border-b border-gray-100">
          <div className="w-8 h-8 bg-[#1a365d] rounded-lg flex items-center justify-center">
            <span className="text-white font-bold text-xs">HR</span>
          </div>
          <span className="text-[#1a202c] font-bold text-sm">HR Workflow</span>
        </div>

        {/* Form slot */}
        <div className="flex-1 flex items-center justify-center p-8 overflow-y-auto">
          <div className="w-full max-w-[400px]">{children}</div>
        </div>
      </div>
    </div>
  );
}

/* Pure-CSS abstract decoration using divs — no images, no SVG needed */
function PanelDecoration() {
  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none">
      {/* Large circle outline — top right */}
      <div className="absolute -top-20 -right-20 w-72 h-72 rounded-full border-2 border-white/8" />
      {/* Medium circle outline — offset inside */}
      <div className="absolute -top-4 -right-4 w-44 h-44 rounded-full border border-white/6" />
      {/* Large filled blob — bottom left */}
      <div className="absolute -bottom-24 -left-24 w-80 h-80 rounded-full bg-white/[0.04]" />
      {/* Small circle — mid left */}
      <div className="absolute top-1/2 -left-6 w-24 h-24 rounded-full border border-white/6" />
      {/* Dot grid */}
      <div
        className="absolute inset-0 opacity-[0.06]"
        style={{
          backgroundImage:
            "radial-gradient(circle, white 1px, transparent 0)",
          backgroundSize: "28px 28px",
        }}
      />
    </div>
  );
}
