"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Sidebar from "@/components/layouts/sidebar";
import Button from "@/components/ui/Button";
import { useAuthGuard } from "@/lib/auth";
import api from "@/lib/api";
import toast from "react-hot-toast";

const templates = [
  {
    id: 1,
    name: "Employee Onboarding",
    description:
      "Automated welcome email, document upload, and HR system update",
    nodes: ["EMAIL", "DRIVE", "EXCEL"],
    category: "HR",
    icon: "👤",
  },
  {
    id: 2,
    name: "Leave Request Approval",
    description:
      "Process leave requests with manager notification and calendar update",
    nodes: ["EMAIL", "GPT", "EXCEL"],
    category: "HR",
    icon: "🏖️",
  },
  {
    id: 3,
    name: "Performance Review",
    description: "Collect feedback, analyze with AI, and generate reports",
    nodes: ["DRIVE", "GPT", "EMAIL", "EXCEL"],
    category: "Performance",
    icon: "📊",
  },
];

export default function TemplatesPage() {
  const router = useRouter();
  const [workflows, setWorkflows] = useState<any[]>([]);

  const authReady = useAuthGuard();
  const cloneTemplate = async (template: any) => {
    try {
      const response = await api.post("/workflows", {
        name: template.name + " (copy)",
        nodes: template.nodes,
        status: "DRAFT",
      });
      toast.success("Template cloned! You can edit it from the dashboard");
    } catch (e: any) {
      console.error("failed clone", e);
      toast.error("Unable to clone template");
    }

    if (!authReady) return null;
  };

  return (
    <div className="flex h-screen overflow-hidden bg-gray-50">
      <Sidebar
        workflows={workflows}
        onCreateWorkflow={() => router.push("/dashboard")}
        onWorkflowDeleted={() =>
          api.get("/workflows").then((r) => setWorkflows(r.data))
        }
      />
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-7xl mx-auto px-8 py-8">
          <h1 className="text-3xl font-bold mb-6">Workflow Templates</h1>
          <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            {templates.map((t) => (
              <div
                key={t.id}
                className="bg-white rounded-xl shadow p-6 flex flex-col justify-between"
              >
                <div>
                  <div className="text-4xl mb-2">{t.icon}</div>
                  <h2 className="text-xl font-semibold">{t.name}</h2>
                  <p className="text-gray-600 mt-2">{t.description}</p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {t.nodes.map((n) => (
                      <span
                        key={n}
                        className="text-xs bg-gray-200 rounded-full px-2 py-1"
                      >
                        {n}
                      </span>
                    ))}
                  </div>
                </div>
                <Button
                  onClick={() => cloneTemplate(t)}
                  variant="primary"
                  className="mt-4"
                >
                  Clone
                </Button>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
