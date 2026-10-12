"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Sidebar from "@/components/layouts/sidebar";
import Button from "@/components/ui/Button";
import { useAuthGuard } from "@/lib/auth";
import api from "@/lib/api";
import toast from "react-hot-toast";
import { getWorkflowsUrl } from "@/lib/workflows";

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

type Template = (typeof templates)[number];

export default function TemplatesPage() {
  const router = useRouter();
  const [workflows, setWorkflows] = useState<any[]>([]);
  const [cloningId, setCloningId] = useState<number | null>(null);

  const authReady = useAuthGuard();

  const fetchWorkflows = () =>
    api.get(getWorkflowsUrl()).then((r) => setWorkflows(r.data)).catch(() => {});

  useEffect(() => {
    if (!authReady) return;
    fetchWorkflows();
  }, [authReady]);

  const cloneTemplate = async (template: Template) => {
    setCloningId(template.id);
    try {
      // POST /workflows only takes name/description; nodes are created separately, in chain order.
      const { data: workflow } = await api.post("/workflows", {
        name: template.name + " (copy)",
        description: template.description,
      });
      const { data: nodes } = await api.post(`/nodes/workflow/${workflow.id}/batch`,
        template.nodes.map((type, index) => ({ type, order: index + 1, configJson: "{}" })),
      );

      // Connect the nodes in sequence and lay them out left-to-right, like the editor does.
      const ids: string[] = nodes
        .sort((a: any, b: any) => a.order - b.order)
        .map((n: any) => n.id.toString());
      const edges = ids.slice(1).map((target, i) => ({
        id: `e${ids[i]}-${target}`,
        source: ids[i],
        target,
        type: "deletable",
        style: { stroke: "#94a3b8", strokeWidth: 2 },
        markerEnd: { type: "arrowclosed", color: "#94a3b8" },
      }));
      const layout = Object.fromEntries(ids.map((id, i) => [id, { x: 100 + i * 250, y: 150 }]));
      await api.put(`/workflows/${workflow.id}`, {
        name: workflow.name,
        description: workflow.description,
        edgesJson: JSON.stringify(edges),
        layoutJson: JSON.stringify(layout),
      });

      toast.success("Template cloned. Configure each node, then activate it.");
      router.push(`/workflows/${workflow.id}`);
    } catch (e: any) {
      console.error("failed clone", e);
      toast.error(e.response?.data?.detail || "Unable to clone template");
      setCloningId(null);
    }
  };

  if (!authReady) return null;

  return (
    <div className="flex h-screen overflow-hidden bg-gray-50">
      <Sidebar
        workflows={workflows}
        onCreateWorkflow={() => router.push("/dashboard")}
        onWorkflowDeleted={fetchWorkflows}
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
                  disabled={cloningId !== null}
                >
                  {cloningId === t.id ? "Cloning…" : "Clone"}
                </Button>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
