"use client";

import Link from "next/link";
import Button from "../components/ui/Button";
import Footer from "../components/layout/Footer";

export default function Home() {
  return (
    <div className="space-y-20">
      <section className="bg-gradient-to-br from-blue-600 via-purple-600 to-indigo-600 text-white">
        <div className="max-w-7xl mx-auto px-6 py-24 lg:py-28">
          <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-12">
            <div className="max-w-xl">
              <p className="inline-flex items-center px-3 py-1.5 rounded-full bg-white/20 text-sm font-semibold tracking-wide uppercase">
                enterprise-ready HR automation
              </p>
              <h1 className="mt-6 text-4xl sm:text-5xl lg:text-6xl font-extrabold leading-tight">
                Build, run, and scale HR workflows in minutes
              </h1>
              <p className="mt-6 text-lg sm:text-xl text-white/90">
                Combine drag-and-drop workflow orchestration with intelligent AI
                and API integration for onboarding, approvals, payroll, and team
                operations.
              </p>
              <div className="mt-8 flex flex-wrap gap-4">
                <Link href="/login">
                  <Button className="px-8 py-3" variant="primary">
                    Try the app
                  </Button>
                </Link>
                <Link href="/register">
                  <Button className="px-8 py-3" variant="outline">
                    Create account
                  </Button>
                </Link>
              </div>
            </div>
            <div className="relative max-w-2xl">
              <div className="rounded-3xl border border-white/20 bg-white/10 backdrop-blur-lg p-8 shadow-2xl">
                <h3 className="text-white text-xl font-semibold mb-4">
                  Live Workflow Overview
                </h3>
                <ul className="space-y-3 text-white/90 text-sm">
                  <li>• Candidate data sync from form → HRIS → Slack</li>
                  <li>• Auto-send onboarding docs after approval</li>
                  <li>• Payroll trigger and audit trail generation</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="bg-white">
        <div className="max-w-7xl mx-auto px-6 py-16">
          <h2 className="text-3xl sm:text-4xl font-bold text-center mb-10">
            Why HR Workflow System?
          </h2>
          <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-4">
            {[
              {
                title: "Visual Workflow Builder",
                desc: "Drag & drop nodes and conditional logic for fast design.",
              },
              {
                title: "AI & Business Logic",
                desc: "Use AI nodes for decision support, content generation and routing.",
              },
              {
                title: "Integrations",
                desc: "Connect email, storage, HR systems and APIs without code.",
              },
              {
                title: "Enterprise Security",
                desc: "Role-based access, audit logs and data encryption in transit and at rest.",
              },
            ].map((item) => (
              <div
                key={item.title}
                className="rounded-2xl border border-gray-200 p-6 hover:shadow-lg transition"
              >
                <h3 className="text-xl font-semibold mb-2">{item.title}</h3>
                <p className="text-gray-600">{item.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="bg-slate-950 text-white">
        <div className="max-w-7xl mx-auto px-6 py-16">
          <h2 className="text-3xl sm:text-4xl font-bold text-center mb-8">
            Built for teams, trusted by leaders
          </h2>
          <div className="flex flex-wrap justify-center items-center gap-6">
            {["Google", "Microsoft", "Stripe", "Amazon", "Airbnb"].map(
              (logo) => (
                <div
                  key={logo}
                  className="w-28 h-12 rounded-lg bg-white/10 flex items-center justify-center text-sm font-semibold"
                >
                  {logo}
                </div>
              ),
            )}
          </div>
          <div className="mt-12 grid gap-6 md:grid-cols-3">
            {[
              {
                quote: "Our onboarding time dropped by 70%.",
                name: "Sara, Head of People",
              },
              {
                quote:
                  "Finally we can standardize HR processes across all departments.",
                name: "Omar, Operations",
              },
              {
                quote: "The transparency and audit trail is a game-changer.",
                name: "Lina, Compliance Lead",
              },
            ].map((item) => (
              <div
                key={item.name}
                className="rounded-2xl border border-white/20 p-6 bg-white/5"
              >
                <p className="italic text-lg leading-relaxed">“{item.quote}”</p>
                <p className="mt-4 font-semibold">{item.name}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="bg-gray-50">
        <div className="max-w-7xl mx-auto px-6 py-16">
          <h2 className="text-3xl sm:text-4xl font-bold text-center mb-10">
            How it works
          </h2>
          <div className="grid gap-6 md:grid-cols-3">
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
                className="rounded-2xl border border-gray-200 p-6 bg-white shadow-sm"
              >
                <div className="text-primary text-2xl font-bold mb-3">
                  {item.step}
                </div>
                <h3 className="text-xl font-semibold mb-2">{item.title}</h3>
                <p className="text-gray-600">{item.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="bg-gradient-to-r from-blue-500 to-purple-600 text-white py-16">
        <div className="max-w-5xl mx-auto text-center px-6">
          <h2 className="text-3xl sm:text-4xl font-bold mb-4">
            Ready to accelerate HR operations?
          </h2>
          <p className="text-lg text-white/90 mb-8">
            Onboard faster, reduce manual follow-ups, and keep everyone aligned
            with one workflow platform.
          </p>
          <div className="flex justify-center gap-4 flex-wrap">
            <Link href="/login">
              <Button className="px-10 py-3" variant="primary">
                Try the app
              </Button>
            </Link>
            <Link href="/register">
              <Button className="px-10 py-3" variant="outline">
                Sign up now
              </Button>
            </Link>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
