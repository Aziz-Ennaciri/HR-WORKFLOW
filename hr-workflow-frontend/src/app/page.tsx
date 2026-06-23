"use client";

import { useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { isAuthenticated } from "@/lib/auth";

/* ─── Data ─────────────────────────────────────────── */

const features = [
  {
    icon: "📂",
    title: "Read Documents",
    desc: "Connect to file storage and automatically read CVs, policies, and HR documents.",
  },
  {
    icon: "🤖",
    title: "AI Analysis",
    desc: "Use AI (Ollama/GPT) to evaluate candidates, review documents, and generate insights.",
  },
  {
    icon: "📊",
    title: "Excel Reports",
    desc: "Automatically generate structured Excel reports with formatted tables and rankings.",
  },
  {
    icon: "✅",
    title: "Approvals & Email",
    desc: "Add manager approvals and receive professional email notifications with results.",
  },
];

const steps = [
  {
    title: "Build",
    desc: 'Create a workflow by connecting nodes: Drive → AI → Excel → Email.',
  },
  {
    title: "Execute",
    desc: "Run your workflow with one click and monitor each step in real time.",
  },
  {
    title: "Results",
    desc: "Get structured Excel reports, approval requests, and email notifications automatically.",
  },
];

/* ─── Page ──────────────────────────────────────────── */

export default function Home() {
  const router = useRouter();

  useEffect(() => {
    if (isAuthenticated()) router.replace("/dashboard");
  }, [router]);

  return (
    <div className="min-h-screen flex flex-col bg-[#f7fafc]">
      {/* ── Navbar ── */}
      <header className="sticky top-0 z-50 bg-white border-b border-gray-100 shadow-sm">
        <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 bg-[#1a365d] rounded-[6px] flex items-center justify-center flex-shrink-0">
              <span className="text-white font-bold text-xs">HR</span>
            </div>
            <span className="font-bold text-[#1a202c] text-[15px] tracking-tight">
              HR Workflow
            </span>
          </div>
          <div className="flex items-center gap-1.5">
            <Link href="/login">
              <button className="px-4 py-2 text-sm font-medium text-[#4a5568] hover:text-[#1a202c] hover:bg-gray-50 rounded-[6px] transition-colors">
                Sign in
              </button>
            </Link>
            <Link href="/register">
              <button className="px-4 py-2 text-sm font-semibold bg-[#1a365d] text-white rounded-[6px] hover:bg-[#2d4a7c] transition-colors shadow-sm">
                Get Started
              </button>
            </Link>
          </div>
        </div>
      </header>

      {/* ── Hero ── */}
      <section className="bg-gradient-to-br from-[#0f1f38] via-[#1a365d] to-[#243b6e] py-24 lg:py-32 relative overflow-hidden">
        {/* Subtle background circle */}
        <div className="absolute top-[-80px] right-[-80px] w-72 h-72 rounded-full border border-white/5 pointer-events-none" />
        <div className="absolute bottom-[-60px] left-[-60px] w-56 h-56 rounded-full border border-white/5 pointer-events-none" />

        <div className="relative max-w-3xl mx-auto px-6 text-center">
          <h1 className="text-4xl sm:text-5xl lg:text-[56px] font-bold text-white leading-tight tracking-tight">
            Automate Your HR
            <br />
            Workflows with AI
          </h1>
          <p className="mt-6 text-[17px] text-white/60 leading-relaxed max-w-xl mx-auto">
            Build intelligent HR workflows that read documents, analyze
            candidates with AI, generate Excel reports, and send email
            notifications — all automated.
          </p>
          <div className="mt-10 flex flex-wrap justify-center gap-3">
            <Link href="/register">
              <button className="px-7 py-3 bg-white text-[#1a365d] text-sm font-bold rounded-[6px] hover:bg-blue-50 transition-colors shadow-md hover:shadow-lg">
                Get Started
              </button>
            </Link>
            <Link href="/login">
              <button className="px-7 py-3 border border-white/25 text-white text-sm font-semibold rounded-[6px] hover:bg-white/10 transition-colors">
                Sign In
              </button>
            </Link>
          </div>
        </div>
      </section>

      {/* ── What It Does ── */}
      <section className="py-20 bg-[#f7fafc]">
        <div className="max-w-6xl mx-auto px-6">
          <div className="text-center mb-12">
            <h2 className="text-2xl sm:text-3xl font-bold text-[#1a202c]">
              What It Does
            </h2>
            <p className="mt-2 text-[#4a5568] text-base max-w-lg mx-auto">
              A complete workflow automation system for common HR tasks
            </p>
          </div>

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {features.map((f) => (
              <FeatureCard key={f.title} {...f} />
            ))}
          </div>
        </div>
      </section>

      {/* ── How It Works ── */}
      <section className="py-20 bg-white border-t border-gray-100">
        <div className="max-w-5xl mx-auto px-6">
          <div className="text-center mb-12">
            <h2 className="text-2xl sm:text-3xl font-bold text-[#1a202c]">
              How It Works
            </h2>
          </div>

          <div className="relative grid gap-6 md:grid-cols-3">
            {/* Connecting dashed line between steps (desktop only) */}
            <div
              className="hidden md:block absolute top-[40px] left-[calc(33%+24px)] right-[calc(33%+24px)] h-px border-t-2 border-dashed border-gray-200"
              aria-hidden="true"
            />

            {steps.map((step, i) => (
              <StepCard key={i} number={i + 1} {...step} />
            ))}
          </div>
        </div>
      </section>

      {/* ── Footer ── */}
      <footer className="mt-auto bg-[#1a202c] py-8">
        <div className="max-w-6xl mx-auto px-6 text-center space-y-1">
          <p className="text-white/75 font-semibold text-sm">
            HR Workflow System — Final Year Project
          </p>
          <p className="text-white/35 text-xs">
            Built with Spring Boot, Next.js, and Ollama
          </p>
        </div>
      </footer>
    </div>
  );
}

/* ─── Feature card ───────────────────────────────────── */
function FeatureCard({
  icon,
  title,
  desc,
}: {
  icon: string;
  title: string;
  desc: string;
}) {
  return (
    <div className="bg-white rounded-lg border border-gray-100 p-6 shadow-sm hover:shadow-md hover:-translate-y-0.5 transition-all duration-200 group">
      <div className="w-11 h-11 rounded-lg bg-blue-50 flex items-center justify-center text-2xl mb-4 group-hover:scale-110 transition-transform duration-200">
        {icon}
      </div>
      <h3 className="text-[15px] font-bold text-[#1a202c] mb-1.5">{title}</h3>
      <p className="text-[#4a5568] text-sm leading-relaxed">{desc}</p>
    </div>
  );
}

/* ─── Step card ──────────────────────────────────────── */
function StepCard({
  number,
  title,
  desc,
}: {
  number: number;
  title: string;
  desc: string;
}) {
  return (
    <div className="relative z-10 bg-white rounded-lg border border-gray-100 shadow-sm p-7 text-center hover:shadow-md transition-shadow duration-200">
      <div className="w-10 h-10 rounded-full bg-[#1a365d] text-white font-bold text-sm flex items-center justify-center mx-auto mb-4">
        {number}
      </div>
      <h3 className="text-[15px] font-bold text-[#1a202c] mb-2">{title}</h3>
      <p className="text-[#4a5568] text-sm leading-relaxed">{desc}</p>
    </div>
  );
}
