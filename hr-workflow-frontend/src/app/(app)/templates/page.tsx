"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { useInvalidateWorkflows } from "@/hooks/queries";
import { getErrorMessage } from "@/lib/errors";
import { NODE_META } from "@/lib/node-meta";
import { workflowsApi } from "@/services/workflows";
import type { NodeConfigs, NodeType } from "@/types";

interface TemplateStep<T extends NodeType = NodeType> {
  type: T;
  config: NodeConfigs[T];
}

interface Template {
  id: string;
  name: string;
  description: string;
  category: string;
  steps: TemplateStep[];
}

const TEMPLATES: Template[] = [
  {
    id: "screening",
    name: "Candidate screening",
    description: "Read CVs from a folder, rank them with AI, produce an Excel report and email it to the hiring team.",
    category: "Recruitment",
    steps: [
      { type: "DRIVE", config: { action: "read", folderId: "cvs" } },
      { type: "GPT", config: { provider: "ollama", prompt: "Rank these candidates for the role and explain each score.", outputFormat: "json" } },
      { type: "EXCEL", config: { operation: "WRITE", fileName: "Candidate ranking" } },
      { type: "EMAIL", config: { subject: "Candidate ranking report" } },
    ],
  },
  {
    id: "screening-approval",
    name: "Screening with manager approval",
    description: "Like candidate screening, but a manager reviews the AI shortlist before the report goes out.",
    category: "Recruitment",
    steps: [
      { type: "DRIVE", config: { action: "read", folderId: "cvs" } },
      { type: "GPT", config: { provider: "ollama", prompt: "Shortlist the best candidates for the role.", outputFormat: "json" } },
      { type: "APPROVAL", config: { instructions: "Check the shortlist before it is shared." } },
      { type: "EXCEL", config: { operation: "WRITE", fileName: "Approved shortlist" } },
      { type: "EMAIL", config: { subject: "Approved shortlist" } },
    ],
  },
  {
    id: "spreadsheet-review",
    name: "Spreadsheet review",
    description: "Read rows from an Excel file, have AI summarise them, and email the summary.",
    category: "Reporting",
    steps: [
      { type: "EXCEL", config: { operation: "READ", folderId: "reports", fileName: "employees.xlsx" } },
      { type: "GPT", config: { provider: "ollama", prompt: "Summarise the key figures and anything unusual.", outputFormat: "text" } },
      { type: "EMAIL", config: { subject: "Monthly summary" } },
    ],
  },
];

export default function TemplatesPage() {
  const router = useRouter();
  const invalidateWorkflows = useInvalidateWorkflows();
  const [cloningId, setCloningId] = useState<string | null>(null);

  const applyTemplate = async (template: Template) => {
    setCloningId(template.id);
    try {
      const workflow = await workflowsApi.create({ name: template.name, description: template.description });
      const created = await workflowsApi.createNodes(
        workflow.id,
        template.steps.map((step, index) => ({ type: step.type, order: index + 1, configJson: JSON.stringify(step.config) })),
      );
      // Connect the steps in order and lay them out left to right, then open the editor.
      const ids = [...created].sort((a, b) => a.order - b.order).map((node) => String(node.id));
      await workflowsApi.saveGraph(workflow.id, {
        nodes: [...created].map((node) => ({ id: String(node.id), type: node.type, configJson: node.configJson })),
        edges: ids.slice(1).map((target, i) => ({ id: `e${ids[i]}-${target}`, source: ids[i], target, type: "deletable" })),
        layout: Object.fromEntries(ids.map((id, i) => [id, { x: 80 + i * 280, y: 160 }])),
      });
      await invalidateWorkflows();
      toast.success("Template added. Fill in the highlighted settings, then activate it.");
      router.push(`/workflows/${workflow.id}`);
    } catch (error) {
      toast.error(getErrorMessage(error, "Could not create a workflow from this template"));
      setCloningId(null);
    }
  };

  return (
    <div className="mx-auto max-w-6xl px-4 py-6 sm:px-8 sm:py-8">
      <PageHeader title="Templates" description="Start from a ready-made workflow and adjust it to your needs." />
      <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
        {TEMPLATES.map((template) => (
          <Card key={template.id} className="flex flex-col p-5">
            <Badge className="mb-3 self-start">{template.category}</Badge>
            <h2 className="text-lg font-semibold">{template.name}</h2>
            <p className="mt-1.5 flex-1 text-sm text-muted-foreground">{template.description}</p>
            <ol className="mt-4 flex flex-wrap items-center gap-1.5" aria-label="Steps">
              {template.steps.map((step, index) => {
                const meta = NODE_META[step.type];
                const Icon = meta.icon;
                return (
                  <li key={index} className="flex items-center gap-1.5">
                    <span className={`flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium ${meta.tile}`}>
                      <Icon className="h-3.5 w-3.5" aria-hidden /> {meta.label}
                    </span>
                    {index < template.steps.length - 1 && <ArrowRight className="h-3 w-3 text-muted-foreground" aria-hidden />}
                  </li>
                );
              })}
            </ol>
            <Button className="mt-5" loading={cloningId === template.id} disabled={cloningId !== null} onClick={() => applyTemplate(template)}>
              Use template
            </Button>
          </Card>
        ))}
      </div>
    </div>
  );
}
