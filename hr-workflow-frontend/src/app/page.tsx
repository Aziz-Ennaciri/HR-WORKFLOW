"use client";

import Link from "next/link";
import Button from "../components/ui/Button";
import Card from "../components/ui/Card";

export default function Home() {
  return (
    <div className="">
      {/* Hero */}
      <section className="bg-gradient-to-br from-primary/10 to-secondary/10 py-24">
        <div className="max-w-4xl mx-auto text-center px-4">
          <h1 className="text-5xl sm:text-6xl font-extrabold text-gray-900 mb-4">
            Automate Your HR Processes with Confidence
          </h1>
          <p className="text-xl text-gray-700 mb-8">
            Build, execute and monitor custom workflows to streamline HR tasks
            like onboarding, approvals, and document management.
          </p>
          <div className="flex justify-center space-x-4">
            <Link href="/register">
              <Button className="px-8 py-3" variant="primary">
                Get Started
              </Button>
            </Link>
            <Link href="/login">
              <Button className="px-8 py-3" variant="outline">
                Login
              </Button>
            </Link>
          </div>
        </div>
      </section>

      {/* Features */}
      <section className="py-16 bg-white">
        <div className="max-w-5xl mx-auto px-4">
          <h2 className="text-3xl font-bold text-center mb-12">Key Features</h2>
          <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-3">
            <Card>
              <h3 className="text-xl font-semibold mb-2">
                Visual Workflow Builder
              </h3>
              <p className="text-gray-600">
                Design workflows with an intuitive drag-and-drop interface
                powered by React Flow.
              </p>
            </Card>
            <Card>
              <h3 className="text-xl font-semibold mb-2">
                AI-Driven Automation
              </h3>
              <p className="text-gray-600">
                Leverage AI nodes to generate content, summaries, and automate
                decision-making.
              </p>
            </Card>
            <Card>
              <h3 className="text-xl font-semibold mb-2">
                External Integrations
              </h3>
              <p className="text-gray-600">
                Connect to Google Drive, email services, and other APIs to
                extend your workflows.
              </p>
            </Card>
            <Card>
              <h3 className="text-xl font-semibold mb-2">Execution History</h3>
              <p className="text-gray-600">
                Track each workflow execution with full audit logs and status
                updates.
              </p>
            </Card>
            <Card>
              <h3 className="text-xl font-semibold mb-2">Secure & Reliable</h3>
              <p className="text-gray-600">
                Built with Spring Boot and Next.js, our platform ensures
                enterprise-grade security and performance.
              </p>
            </Card>
            <Card>
              <h3 className="text-xl font-semibold mb-2">Customizable Nodes</h3>
              <p className="text-gray-600">
                Create and configure your own nodes tailored to your HR
                processes.
              </p>
            </Card>
          </div>
        </div>
      </section>
    </div>
  );
}
