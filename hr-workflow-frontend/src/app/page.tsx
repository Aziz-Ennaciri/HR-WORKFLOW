"use client";

import Link from "next/link";
import Button from "../components/ui/Button";
import Footer from "../components/layout/Footer";

export default function Home() {
  return (
    <div>
      {/* Hero */}
      <section className="bg-white relative overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_30%_20%,rgba(59,130,246,0.04),transparent_50%),radial-gradient(circle_at_80%_80%,rgba(99,102,241,0.04),transparent_50%)]" />
        <div className="max-w-7xl mx-auto px-6 py-24 lg:py-32 relative">
          <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-16">
            <div className="max-w-xl">
              <p className="inline-flex items-center px-3 py-1.5 rounded-full bg-blue-50 text-blue-600 text-[11px] font-bold tracking-wider uppercase border border-blue-100">
                Enterprise HR Automation
              </p>
              <h1 className="mt-6 text-4xl sm:text-5xl lg:text-[56px] font-bold leading-[1.1] text-gray-900 tracking-tight">
                Build, run, and scale
                <span className="text-blue-600"> HR workflows</span> in minutes
              </h1>
              <p className="mt-6 text-lg text-gray-500 leading-relaxed">
                Combine drag-and-drop workflow orchestration with intelligent AI
                and API integration for onboarding, approvals, payroll, and team
                operations.
              </p>
              <div className="mt-8 flex flex-wrap gap-3">
                <Link href="/login">
                  <button className="px-7 py-3 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold rounded-xl transition-all shadow-sm hover:shadow-md active:scale-[0.97]">
                    Try the app
                  </button>
                </Link>
                <Link href="/register">
                  <button className="px-7 py-3 bg-white hover:bg-gray-50 text-gray-700 text-sm font-semibold rounded-xl border border-gray-200 hover:border-gray-300 transition-all">
                    Create account
                  </button>
                </Link>
              </div>
            </div>

            {/* Preview card */}
            <div className="relative max-w-lg w-full">
              <div className="rounded-2xl border border-gray-200 bg-white p-8 shadow-lg shadow-gray-100/50">
                <div className="flex items-center gap-2 mb-5">
                  <div className="w-3 h-3 rounded-full bg-red-400" />
                  <div className="w-3 h-3 rounded-full bg-amber-400" />
                  <div className="w-3 h-3 rounded-full bg-emerald-400" />
                </div>
                <h3 className="text-gray-900 text-base font-semibold mb-4">
                  Live Workflow Overview
                </h3>
                <div className="space-y-3">
                  {[
                    {
                      icon: "🔄",
                      text: "Candidate data sync from form → HRIS → Slack",
                    },
                    {
                      icon: "📄",
                      text: "Auto-send onboarding docs after approval",
                    },
                    {
                      icon: "💰",
                      text: "Payroll trigger and audit trail generation",
                    },
                  ].map((item, i) => (
                    <div
                      key={i}
                      className="flex items-center gap-3 text-sm text-gray-600 bg-gray-50 rounded-lg px-4 py-2.5 border border-gray-100"
                    >
                      <span>{item.icon}</span>
                      <span>{item.text}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Features */}
      <section className="bg-[#f8f9fb]">
        <div className="max-w-7xl mx-auto px-6 py-20">
          <div className="text-center mb-12">
            <h2 className="text-3xl font-bold text-gray-900 tracking-tight">
              Why HR Workflow System?
            </h2>
            <p className="mt-3 text-gray-400 text-base max-w-lg mx-auto">
              Everything you need to automate, track, and scale your HR
              operations
            </p>
          </div>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            {[
              {
                title: "Visual Workflow Builder",
                desc: "Drag & drop nodes and conditional logic for fast design.",
                icon: "🧩",
              },
              {
                title: "AI & Business Logic",
                desc: "Use AI nodes for decision support, content generation and routing.",
                icon: "🤖",
              },
              {
                title: "Integrations",
                desc: "Connect email, storage, HR systems and APIs without code.",
                icon: "🔗",
              },
              {
                title: "Enterprise Security",
                desc: "Role-based access, audit logs and data encryption in transit and at rest.",
                icon: "🔒",
              },
            ].map((item) => (
              <div
                key={item.title}
                className="rounded-2xl border border-gray-200 bg-white p-6 hover:shadow-md hover:border-gray-300 transition-all"
              >
                <div className="w-10 h-10 bg-gray-50 rounded-xl flex items-center justify-center text-xl mb-4 border border-gray-100">
                  {item.icon}
                </div>
                <h3 className="text-base font-semibold text-gray-900 mb-1.5">
                  {item.title}
                </h3>
                <p className="text-gray-500 text-sm leading-relaxed">
                  {item.desc}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Social proof */}
      <section className="bg-white border-y border-gray-100">
        <div className="max-w-7xl mx-auto px-6 py-16">
          <p className="text-center text-sm text-gray-400 font-medium uppercase tracking-wider mb-8">
            Trusted by teams at
          </p>
          <div className="flex flex-wrap justify-center items-center gap-8">
            {["Google", "Microsoft", "Stripe", "Amazon", "Airbnb"].map(
              (logo) => (
                <div
                  key={logo}
                  className="text-lg font-semibold text-gray-300 tracking-tight"
                >
                  {logo}
                </div>
              ),
            )}
          </div>
        </div>
      </section>

      {/* Testimonials */}
      <section className="bg-[#f8f9fb]">
        <div className="max-w-7xl mx-auto px-6 py-20">
          <h2 className="text-3xl font-bold text-gray-900 text-center mb-10 tracking-tight">
            Built for teams, trusted by leaders
          </h2>
          <div className="grid gap-4 md:grid-cols-3">
            {[
              {
                quote: "Our onboarding time dropped by 70%.",
                name: "Sara",
                role: "Head of People",
              },
              {
                quote:
                  "Finally we can standardize HR processes across all departments.",
                name: "Omar",
                role: "Operations",
              },
              {
                quote: "The transparency and audit trail is a game-changer.",
                name: "Lina",
                role: "Compliance Lead",
              },
            ].map((item) => (
              <div
                key={item.name}
                className="rounded-2xl border border-gray-200 bg-white p-6"
              >
                <div className="flex items-center gap-1 mb-4">
                  {[...Array(5)].map((_, i) => (
                    <svg
                      key={i}
                      className="w-4 h-4 text-amber-400"
                      fill="currentColor"
                      viewBox="0 0 20 20"
                    >
                      <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                    </svg>
                  ))}
                </div>
                <p className="text-gray-600 text-sm leading-relaxed mb-4">
                  "{item.quote}"
                </p>
                <div>
                  <p className="font-semibold text-gray-900 text-sm">
                    {item.name}
                  </p>
                  <p className="text-gray-400 text-xs">{item.role}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* How it works */}
      <section className="bg-white">
        <div className="max-w-7xl mx-auto px-6 py-20">
          <h2 className="text-3xl font-bold text-gray-900 text-center mb-12 tracking-tight">
            How it works
          </h2>
          <div className="grid gap-4 md:grid-cols-3">
            {[
              {
                step: "01",
                title: "Design",
                desc: "Compose nodes, triggers and branches with our visual canvas.",
              },
              {
                step: "02",
                title: "Test",
                desc: "Run sandbox executions to validate data flow and outcomes.",
              },
              {
                step: "03",
                title: "Deploy",
                desc: "Activate workflows and monitor execution health in real time.",
              },
            ].map((item) => (
              <div
                key={item.step}
                className="rounded-2xl border border-gray-200 p-6 bg-white hover:shadow-md transition-all"
              >
                <div className="text-blue-600 text-2xl font-bold mb-3 tracking-tight">
                  {item.step}
                </div>
                <h3 className="text-lg font-semibold text-gray-900 mb-1.5">
                  {item.title}
                </h3>
                <p className="text-gray-500 text-sm leading-relaxed">
                  {item.desc}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="bg-gray-900">
        <div className="max-w-5xl mx-auto text-center px-6 py-20">
          <h2 className="text-3xl font-bold text-white mb-3 tracking-tight">
            Ready to accelerate HR operations?
          </h2>
          <p className="text-gray-400 text-base mb-8 max-w-lg mx-auto">
            Onboard faster, reduce manual follow-ups, and keep everyone aligned
            with one workflow platform.
          </p>
          <div className="flex justify-center gap-3 flex-wrap">
            <Link href="/login">
              <button className="px-8 py-3 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold rounded-xl transition-all shadow-sm">
                Try the app
              </button>
            </Link>
            <Link href="/register">
              <button className="px-8 py-3 bg-white/10 hover:bg-white/15 text-white text-sm font-semibold rounded-xl border border-white/20 transition-all">
                Sign up now
              </button>
            </Link>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
